package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.LoginStateEntry;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-2 登录授权上下文（login_state）真实 PG 门禁：V2 增量迁移、一次性原子消费
 * （含并发恰一赢家）、过期拒绝、门禁 MANDATORY。
 */
class PgLoginStateStoreTest {

    private final DataSource dataSource = Support.dataSource();

    private PgLoginStateStore store;

    private IdentityTransactionRunner runner;

    /** 可推进时钟：过期路径确定性。 */
    private Instant now;

    @BeforeEach
    void setUp() {
        Support.cleanLoginState(dataSource);
        now = Instant.now();
        store = new PgLoginStateStore(new org.springframework.jdbc.core.JdbcTemplate(dataSource),
                () -> now);
        runner = Support.runner(dataSource);
    }

    private LoginStateEntry entry(String state) {
        return new LoginStateEntry(state, "verifier-" + state, "binding-" + state,
                "/", now.plus(10, ChronoUnit.MINUTES));
    }

    @Test
    void v2IncrementalMigrationAddsColumnsAndRoundTripWorks() {
        // V2 列在真实库上存在（Support.ensureIdentitySchema 已执行 V1+V2）
        var columns = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForList("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name='login_state' ORDER BY ordinal_position", String.class);
        assertThat(columns).contains("code_verifier", "browser_binding", "post_login_redirect", "created_at");
        // W22-RV-01：过期回收索引（save 惰性清理 DELETE … WHERE expires_at < ? 走此索引）
        var indexes = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForList("SELECT indexname FROM pg_indexes WHERE tablename='login_state'",
                        String.class);
        assertThat(indexes).contains("idx_login_state_expires_at");

        runner.inTransaction(() -> {
            store.save(entry("state-rt"));
            return null;
        });
        Optional<LoginStateEntry> consumed = runner.inTransaction(() -> store.consume("state-rt"));

        assertThat(consumed).isPresent();
        assertThat(consumed.get().codeVerifier()).isEqualTo("verifier-state-rt");
        assertThat(consumed.get().browserBinding()).isEqualTo("binding-state-rt");
        assertThat(consumed.get().postLoginRedirect()).isEqualTo("/");
    }

    // ==== 过期回收（W22-RV-01）====

    @Test
    void savePurgesExpiredRowsPastRetentionWindow() {
        // retention=0：越过 expires_at 即可回收（确定性验证）
        var purgingStore = new PgLoginStateStore(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource), () -> now, Duration.ZERO);

        // 已消费但未过期的行：本次不回收（consume 语义靠行存在性保持）
        runner.inTransaction(() -> {
            purgingStore.save(entry("state-live"));
            return null;
        });
        runner.inTransaction(() -> purgingStore.consume("state-live"));

        // 时钟推进越过全部行 expires_at；再次 save 触发惰性回收
        now = now.plus(11, ChronoUnit.MINUTES);
        runner.inTransaction(() -> {
            purgingStore.save(entry("state-next"));
            return null;
        });

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        // 已消费且过期的行被回收——删除后重放按 state-missing 拒绝（语义不变，复评确认安全）
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_state WHERE state='state-live'",
                Integer.class)).isZero();
        // 未越过保留窗口外边界之外的本次新行保留
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_state WHERE state='state-next'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void saveKeepsExpiredRowsWithinRetentionWindow() {
        // retention=1h（缺省级）：过期 11 分钟仍在保留窗口内，不回收（审计/排障可查）
        var keepingStore = new PgLoginStateStore(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource), () -> now, Duration.ofHours(1));
        runner.inTransaction(() -> {
            keepingStore.save(entry("state-keep"));
            return null;
        });
        now = now.plus(11, ChronoUnit.MINUTES);
        runner.inTransaction(() -> {
            keepingStore.save(entry("state-fresh"));
            return null;
        });

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_state WHERE state='state-keep'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_state WHERE state='state-fresh'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void consumeIsOneShotReplayReturnsEmpty() {
        runner.inTransaction(() -> {
            store.save(entry("state-once"));
            return null;
        });

        assertThat(runner.inTransaction(() -> store.consume("state-once"))).isPresent();
        assertThat(runner.inTransaction(() -> store.consume("state-once"))).isEmpty();
        assertThat(runner.inTransaction(() -> store.consume("state-never"))).isEmpty();
    }

    @Test
    void expiredStateIsRejectedAtConsume() {
        runner.inTransaction(() -> {
            store.save(entry("state-exp"));
            return null;
        });
        // 时钟推进越过 expires_at：过期 state 不可消费
        now = now.plus(11, ChronoUnit.MINUTES);

        assertThat(runner.inTransaction(() -> store.consume("state-exp"))).isEmpty();
    }

    @Test
    void concurrentConsumeHasExactlyOneWinner() throws Exception {
        runner.inTransaction(() -> {
            store.save(entry("state-race"));
            return null;
        });

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger winners = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    // 每线程独立短事务消费（与生产回调路径同构）
                    Support.runner(dataSource).inTransaction(() -> {
                        if (store.consume("state-race").isPresent()) {
                            winners.incrementAndGet();
                        }
                        return null;
                    });
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }

            // 条件更新原子性：并发恰一个赢家（防"先读后写"窗口/并发重复签发）
            assertThat(winners.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateStateSaveIsRejectedByPrimaryKey() {
        runner.inTransaction(() -> {
            store.save(entry("state-dup"));
            return null;
        });

        assertThatThrownBy(() -> runner.inTransaction(() -> {
            store.save(entry("state-dup"));
            return null;
        })).isInstanceOf(RuntimeException.class);
    }

    @Test
    void gateRejectsOperationsWithoutTransaction() {
        assertThatThrownBy(() -> store.consume("state-nogate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实际事务");

        assertThatThrownBy(() -> store.save(entry("state-nogate")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实际事务");
    }
}
