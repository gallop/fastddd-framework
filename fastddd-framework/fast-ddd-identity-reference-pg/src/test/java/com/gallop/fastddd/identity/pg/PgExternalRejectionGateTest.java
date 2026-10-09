package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.RejectionRecord;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-c external_rejection 门禁 PG 验收测试（W5 设计 §7.5；端口 javadoc 冻结契约）。
 *
 * <p><b>R3-O-2 实测关闭</b>：{@code pg_advisory_xact_lock(int, int)}（经
 * {@code hashtext}）双参 {@code _xact_} 形态在真实 zonky PG 上可调用——本类全部
 * 写入路径均经该语句，通过即确认；键级串行化以两独立 DataSource（两实例）+ 闩锁
 * 交错实测：A 实例持键 advisory 锁未提交期间，B 实例同键 record 阻塞至 A 提交。</p>
 */
class PgExternalRejectionGateTest {

    private static final ExternalIdentity IDENTITY =
            new ExternalIdentity("feishu", "tenant-rej", "app", "open_id", "ou_rej_1");

    private DataSource dataSource;
    private PgExternalRejectionGate gate;
    private IdentityTransactionRunner runner;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        gate = new PgExternalRejectionGate(new JdbcTemplate(dataSource));
        runner = Support.runner(dataSource);
    }

    @Test
    void recordPersistsRowAndIsIdempotentReturningExistingRowAsIs() {
        RejectionRecord first = runner.inTransaction(() -> gate.record(IDENTITY, "明确停用：离职"));
        assertThat(first.reason()).isEqualTo("明确停用：离职");
        assertThat(first.clearedAt()).isNull();

        // 幂等落回：不同 reason 亦不覆盖、不复活——返回既有行原样
        RejectionRecord second = runner.inTransaction(() -> gate.record(IDENTITY, "另一理由"));
        assertThat(second.rejectedAt()).isEqualTo(first.rejectedAt());
        assertThat(second.reason()).isEqualTo("明确停用：离职");
        assertThat(Support.count(dataSource, "external_rejection")).isEqualTo(1);

        // R3-P3-5 契约面：findActive 无事务可读（非门禁读取面，同批紧邻 O-1 源头）
        assertThat(gate.findActive(IDENTITY)).hasValueSatisfying(active ->
                assertThat(active.reason()).isEqualTo("明确停用：离职"));
    }

    @Test
    void recordFallsBackToClearedRowWithoutResurrection() {
        runner.inTransaction(() -> gate.record(IDENTITY, "先拒绝"));
        runner.inTransaction(() -> gate.clearControlled(IDENTITY, "op-1", "误判"));
        assertThat(gate.findActive(IDENTITY)).isEmpty();

        // 已清除行再度 record：落回既有行（cleared_at 保留）——复活语义不在冻结契约
        RejectionRecord again = runner.inTransaction(() -> gate.record(IDENTITY, "再次拒绝"));
        assertThat(again.clearedAt()).isNotNull();
        assertThat(gate.findActive(IDENTITY)).isEmpty();
        assertThat(Support.count(dataSource, "external_rejection")).isEqualTo(1);
    }

    @Test
    void clearsReturnOneThenZeroAndWritePathsRequireTransaction() {
        runner.inTransaction(() -> gate.record(IDENTITY, "r"));
        assertThat(runner.inTransaction(() -> gate.clearControlled(IDENTITY, "op", "reason"))).isEqualTo(1);
        assertThat(runner.inTransaction(() ->
                gate.clearByAuthoritativeRestoration(IDENTITY, "chg-log-id"))).isEqualTo(0); // 已清除：零行

        // 写入路径 MANDATORY 门禁：事务外一律拒绝（advisory _xact_ 必须活在事务内）
        assertThatThrownBy(() -> gate.record(IDENTITY, "r"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external-rejection:record");
        assertThatThrownBy(() -> gate.clearControlled(IDENTITY, "op", "r"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external-rejection:clear");
        assertThatThrownBy(() -> gate.lockActive(IDENTITY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external-rejection:lockActive");
    }

    @Test
    void lockActiveSeesRowInsideTransactionAndMissingKeyInsertsNothing() {
        runner.inTransaction(() -> gate.record(IDENTITY, "r"));
        runner.inTransaction(() -> {
            assertThat(gate.lockActive(IDENTITY)).isPresent();
            return null;
        });
        // 无行键：lockActive 空返回——advisory 锁仍持有至事务结束（无行不插空行）
        ExternalIdentity missing = new ExternalIdentity("feishu", "tenant-rej", "app", "open_id", "ou_none");
        runner.inTransaction(() -> {
            assertThat(gate.lockActive(missing)).isEmpty();
            return null;
        });
        assertThat(Support.count(dataSource, "external_rejection")).isEqualTo(1);
    }

    /**
     * 两实例键级串行化（M-31 可断言形态）：A 持同键 advisory 锁未提交期间，
     * B 的同键 record 阻塞至 A 提交后才落行——线性化时点被封死在 A 提交之后。
     */
    @Test
    void advisoryKeyLockSerializesTwoInstancesOnSameKey() throws Exception {
        DataSource dsA = Support.dataSource();
        DataSource dsB = Support.dataSource();
        PgExternalRejectionGate gateA = new PgExternalRejectionGate(new JdbcTemplate(dsA));
        PgExternalRejectionGate gateB = new PgExternalRejectionGate(new JdbcTemplate(dsB));
        IdentityTransactionRunner runnerA = Support.runner(dsA);
        IdentityTransactionRunner runnerB = Support.runner(dsB);

        CountDownLatch lockHeld = new CountDownLatch(1);
        AtomicReference<Throwable> aFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                runnerA.inTransaction(() -> {
                    gateA.lockActive(IDENTITY); // ⓪ 键 advisory 锁 + 无行不插
                    lockHeld.countDown();
                    try {
                        Thread.sleep(400); // 持锁窗口
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Throwable t) {
                lockHeld.countDown();
                aFailure.set(t);
            }
        }, "advisory-holder");
        holder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        RejectionRecord observed = runnerB.inTransaction(() -> gateB.record(IDENTITY, "B 视角拒绝"));
        long blockedMillis = (System.nanoTime() - start) / 1_000_000;
        holder.join(5000);
        assertThat(aFailure.get()).isNull();
        // B 必须等到 A 提交（advisory 释放）才写入——阻塞下界即串行化证据
        assertThat(blockedMillis).isGreaterThanOrEqualTo(300);
        assertThat(observed.reason()).isEqualTo("B 视角拒绝");
        assertThat(Support.count(dataSource, "external_rejection")).isEqualTo(1);
        // 门禁类常量 = 0x46444444 = 1,178,879,044（C-2 更正值钉在锁面）
        assertThat(ExternalRejectionGate.ADVISORY_LOCK_CLASS).isEqualTo(0x46444444);
        assertThat(ExternalRejectionGate.ADVISORY_LOCK_CLASS).isEqualTo(1_178_879_044);
    }
}
