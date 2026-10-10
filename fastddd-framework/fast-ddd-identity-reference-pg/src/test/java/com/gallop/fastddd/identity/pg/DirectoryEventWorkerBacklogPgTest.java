package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-5（W5-f f-3 §7.2；F6 pg 集成级）：N 行积压批量消解——真实收件箱 +
 * 真实回查提交器 + 真实事实读取器（{@code PgDirectoryFactReader}）+ 真实
 * runner，仅桩源侧（channel 解析面/recheckQuery 观察面——外部源不进测试）。
 *
 * <p><b>弧线</b>：7 条良事件（同对象 ou-backlog，观察侧逐次变版 → 每事件
 * 一次真实 CAS 提交、rev 单调）+ 1 条毒事件（channel 解析抛 → resolve-failed
 * 重试语义：attempts&lt;5 回 RECEIVED 跨轮重试、=5 转 FAILED 终态）按
 * batchSize=3 分轮消解；<b>积压中 T7 全量发布照常走</b>（worker 轮间零跨轮
 * 持锁——claim 行锁随轮提交释放）；断言每轮 committed 单调不减 + 终值
 * = 7 次事件提交 + 1 次发布推进。</p>
 *
 * <p><b>载体注记（f-4 登记项）</b>：W5-f 设计 §7.2 R-5 行写「pg/core worker
 * 扩」——pg 侧此前无 worker 测试类，本测为新类载体（设计口径「扩」按
 * 「worker 编排面的 pg 集成级验证」兑现，core 侧编排级见 R-7）。</p>
 */
class DirectoryEventWorkerBacklogPgTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-backlog", "app");
    private static final String GOOD_TYPE = "contact.user.updated_v3";
    private static final String POISON_TYPE = "contact.user.poison_v1";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private PgDirectoryEventInbox inbox;
    private StaggeredRecheckQuery recheckQuery;
    private DirectoryEventWorkerUseCase worker;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        inbox = new PgDirectoryEventInbox(jdbc);
        recheckQuery = new StaggeredRecheckQuery();
        worker = new DirectoryEventWorkerUseCase(inbox, new StubChannel(), recheckQuery,
                new PgDirectoryFactReader(jdbc), new NeverMatchMatcher(),
                new PgDirectoryRecheckCommitter(jdbc, new PgDirectoryChangeLog(jdbc)),
                Support.runner(dataSource), 3, Duration.ofMinutes(1));
    }

    @Test
    void r5_backlogMeltsInBatchesWithMonotonicWatermarkWhileT7StillPublishes() {
        Instant now = Instant.now();
        for (int i = 1; i <= 7; i++) { // 良事件：receivedAt 递增 → D11 消费序确定
            assertThat(inbox.persist(EventEnvelope.received("evt-b" + i, SCOPE, GOOD_TYPE,
                    "{}", now.minusSeconds(10 - i)))).isTrue();
        }
        assertThat(inbox.persist(EventEnvelope.received("evt-poison", SCOPE, POISON_TYPE,
                "{}", now.minusSeconds(1)))).isTrue();

        List<Long> committedTrail = new ArrayList<>();
        // 轮 1–2：6/7 良事件按 batchSize=3 消解（毒事件未触——消费序在后）
        DirectoryEventWorkerUseCase.InboxRunResult round1 = worker.runOnce();
        assertThat(round1.claimed()).isEqualTo(3);
        assertThat(round1.processed()).isEqualTo(3);
        committedTrail.add(committedSeq());
        assertThat(committedSeq()).isEqualTo(3); // 每事件恰一次真实提交
        DirectoryEventWorkerUseCase.InboxRunResult round2 = worker.runOnce();
        assertThat(round2.processed()).isEqualTo(3);
        committedTrail.add(committedSeq());
        assertThat(committedSeq()).isEqualTo(6);

        // 积压不阻塞：worker 轮间 T7 全量发布照常走（claim 行锁随轮提交释放）
        DirectoryPublication publication = publishScanGeneration();
        assertThat(publication.factsInserted()).isEqualTo(2); // od-x + ou-new 首见（ou-backlog 已有事实 → Rule-A 跳过）
        assertThat(publication.committedSeqAfter()).isEqualTo(7); // 发布推进水位（含新事实）
        committedTrail.add(committedSeq());

        // 轮 3+：余下 1 良事件 + 毒事件重试至 FAILED；循环至 claim 空
        DirectoryEventWorkerUseCase.InboxRunResult round;
        do {
            round = worker.runOnce();
            committedTrail.add(committedSeq());
        } while (round.claimed() > 0);

        // committed 全程单调不减 + 终值 = 7 次事件提交 + 1 次发布推进
        assertThat(committedTrail).isSorted();
        assertThat(committedSeq()).isEqualTo(8);
        // 7 良事件各恰处理一次（PROCESSED 终态）；毒事件 5 次尝试后 FAILED
        assertThat(inboxStateCounts()).containsEntry("PROCESSED", 7L).containsEntry("FAILED", 1L);
        var poisonRow = jdbc.queryForMap("SELECT attempts, last_error FROM directory_event_inbox "
                + "WHERE event_id='evt-poison'");
        assertThat(poisonRow.get("attempts")).isEqualTo(5); // 5 次重试后终态
        assertThat((String) poisonRow.get("last_error")).startsWith("resolve-failed:");
        assertThat(inbox.claim(10, Duration.ofMinutes(1))).isEmpty(); // 零 RECEIVED 残留
    }

    /** T7 全量发布（ou-backlog 扫描名 + ou-new 新人——后者首见落事实推水位）。 */
    private DirectoryPublication publishScanGeneration() {
        IdentityTransactionRunner runner = Support.runner(dataSource);
        PgDirectoryMirrorStore store = new PgDirectoryMirrorStore(jdbc, Instant::now,
                Duration.ofMinutes(10));
        GenerationHandle handle = runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> {
            store.writeBatch(handle, DirectoryBatch.ofDepartments(
                    List.of(new DirectoryDepartment("od-x", null, "积压中部门"))));
            return null;
        });
        runner.inTransaction(() -> {
            store.writeBatch(handle, DirectoryBatch.ofPersons(List.of(
                    new DirectoryPerson("ou-backlog", "扫描名", null, List.of("od-x")),
                    new DirectoryPerson("ou-new", "新人", null, List.of("od-x")))));
            return null;
        });
        runner.inTransaction(() -> {
            store.completePaging(handle);
            return null;
        });
        runner.inTransaction(() -> {
            store.validate(handle);
            return null;
        });
        return runner.inTransaction(() -> store.publish(handle));
    }

    private long committedSeq() {
        return jdbc.queryForObject("SELECT committed_seq FROM directory_publish_metadata "
                + "WHERE tenant_id=? AND app_scope=?", Long.class,
                SCOPE.tenantKey(), SCOPE.appScope());
    }

    private Map<String, Long> inboxStateCounts() {
        Map<String, Long> counts = new java.util.HashMap<>();
        jdbc.query("SELECT state, count(*) FROM directory_event_inbox GROUP BY state",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        return counts;
    }

    /** 观察面桩：每次回查返回递增版本（→ 每事件派生 CONTENT_DIFFERS、真实 CAS）。 */
    static final class StaggeredRecheckQuery implements DirectoryRecheckQuery {
        private final AtomicInteger version = new AtomicInteger();

        @Override
        public Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType,
                                                               String externalId) {
            return Optional.of(ObservedDirectoryObject.of(
                    new DirectoryPerson("ou-backlog", "backlog-v" + version.incrementAndGet(),
                            null, List.of("od-x")),
                    false));
        }
    }

    /** 解析面桩：良类型 → 人员目标；毒类型 → 抛（resolve-failed 语义）；其余非消费。 */
    static final class StubChannel implements DirectoryEventChannel {
        @Override
        public WebhookReceipt receive(DirectoryWebhookRequest request) {
            throw new UnsupportedOperationException("R-5 不走接收面");
        }

        @Override
        public Optional<RecheckTarget> resolveObject(String eventType, String payloadJson) {
            if (GOOD_TYPE.equals(eventType)) {
                return Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou-backlog"));
            }
            if (POISON_TYPE.equals(eventType)) {
                throw new IllegalStateException("毒载荷（测试桩）");
            }
            return Optional.empty();
        }
    }

    /** 匹配面桩：恒不匹配（→ 观察侧每次变版必派生 CONTENT_DIFFERS）。 */
    static final class NeverMatchMatcher implements DirectoryFactMatcher {
        @Override
        public boolean matches(String storedFactJson, DirectoryPerson observed) {
            return false;
        }

        @Override
        public boolean matches(String storedFactJson, DirectoryDepartment observed) {
            return false;
        }
    }
}
