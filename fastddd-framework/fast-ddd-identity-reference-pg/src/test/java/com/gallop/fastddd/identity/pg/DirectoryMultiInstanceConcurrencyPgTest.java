package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.IncrementResult;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-f f-3 M-25 多实例验证（W5-f 设计 §6 表 C-1…C-6；W3 :50 口径：M2 不做
 * 多实例吞吐扩展，只做单持有者租约互斥，两实例并发 = 一方等待/跳过）。
 *
 * <p><b>测试形态</b>：两独立 DataSource + 两 runner = 两「实例」（沿
 * {@code PgExternalRejectionGateTest}/{@code TwoInstanceInterleavePgTest} 先例）；
 * 临界区可控点 = 事务回调内（操作返回后、提交前 latch 等待——持锁未提交形态）。
 * 锁序纪律（V3 头注：metadata → generation → 镜像/事实）：一切发布/增量/回查
 * 写者第一把锁 = {@code directory_publish_metadata} 行 FOR UPDATE，C-1/C-2/C-3/
 * C-4/C-6 的阻塞面皆在该锁；C-5 收件箱 claim = {@code FOR UPDATE SKIP LOCKED}
 * （不阻塞、跳过已锁行——批次不重叠）。断言面 jdbc = 第三个 DataSource
 * （只读已提交视图）。</p>
 *
 * <p>覆盖：C-1 同代双触发并发发布（B-5 并发形态补强——既有
 * {@code DirectorySyncT8CompositionTest.secondPublishOfSameGenerationIsNoOp}
 * 为顺序形态）/ C-2 增量 × T7 两序（M-12）/ C-3 增量 × 增量同对象串行无丢失
 * + 不同对象零互假串行 / C-4 回查 × 回查跨实例 CAS 收敛（表 7-1③）/ C-5
 * Inbox claim × claim（W5-c D11 tiebreak）/ C-6 T7 接管 × 迟到旧持有者操作
 * （M-14 接管纪律的并发形态：接管等待在场持有者提交）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
class DirectoryMultiInstanceConcurrencyPgTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-multi", "app");
    private static final Duration TTL = Duration.ofMinutes(10);

    private final DataSource dsA = Support.dataSource();
    private final DataSource dsB = Support.dataSource();
    private final IdentityTransactionRunner runnerA = Support.runner(dsA);
    private final IdentityTransactionRunner runnerB = Support.runner(dsB);
    private final JdbcTemplate jdbcA = new JdbcTemplate(dsA);
    private final JdbcTemplate jdbcB = new JdbcTemplate(dsB);
    /** 断言面（独立连接：只读已提交视图，与两竞争实例互不串扰）。 */
    private final JdbcTemplate jdbc = new JdbcTemplate(Support.dataSource());
    private final PgDirectoryEffectiveView view = new PgDirectoryEffectiveView(jdbc);

    @BeforeEach
    void setUp() {
        Support.cleanDirectoryTables(dsA);
    }

    // ------------------------------------------------------------- C-1

    /**
     * C-1：同代双触发并发发布——两实例共享同一 {@link GenerationHandle}/租约
     * token 并发 publish：A 真发布（持 metadata 行锁未提交）；B 阻塞于同一
     * 行锁，A 提交后 B 见 PUBLISHED 走幂等 no-op（发布前判态——过期 token
     * 不作废已完成发布；零二次写入）。断言：指针/水位无撕裂 + change_log
     * 二次发布零追加（发布链零 change_log 写者，预合并条目保持单条）+ B 结果
     * 与 A 全等（no-op 重建形态）——B-5（W5 设计 §9.3 :613）并发形态补强。
     */
    @Test
    void c1_sameGenerationDualPublishSecondBlocksThenIdempotentNoOp() throws Exception {
        PgDirectoryMirrorStore storeA = store(dsA);
        PgDirectoryMirrorStore storeB = store(dsB);
        GenerationHandle handle = buildGeneration(storeA, runnerA);
        // 预合并一条增量（开放 VALIDATED 代在场）→ committed=1 + change_log 单条
        PgDirectoryIncrementApplier applierA = new PgDirectoryIncrementApplier(jdbcA,
                new PgDirectoryChangeLog(jdbcA));
        runnerA.inTransaction(() -> applierA.apply(SCOPE, new IncrementBatch("evt-c1",
                List.of(new DirectoryDepartment("od-d2", "od-d1", "研发中心")), List.of())));
        long changeLogBefore = changeLogCount();

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<DirectoryPublication> a = new TxThread<>("c1-holder", runnerA,
                () -> storeA.publish(handle), inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue(); // A 持锁未提交

        long t0 = System.nanoTime();
        TxThread<DirectoryPublication> b = new TxThread<>("c1-contender", runnerB,
                () -> storeB.publish(handle), null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // B 阻塞于 metadata 行锁（A 未提交）
        long blockedMillis = (System.nanoTime() - t0) / 1_000_000;

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        DirectoryPublication pubA = a.result.get();
        DirectoryPublication pubB = b.result.get();
        // A 真发布：预合并已写 od-d2 事实 → Rule-A 首见键 = od-d1/ou-p1/ou-p2 → H 1→2
        assertThat(pubA.factsInserted()).isEqualTo(3);
        assertThat(pubA.committedSeqAfter()).isEqualTo(2);
        // B 幂等 no-op：发布结果自 pub_* 列重建，与 A 全等（零二次写入）
        assertThat(pubB).isEqualTo(pubA);
        // 指针/水位无撕裂 + 状态唯一 PUBLISHED
        var meta = metadataRow();
        assertThat(meta.get("active_generation")).isEqualTo(pubA.generationId());
        assertThat(meta.get("committed_seq")).isEqualTo(2L);
        assertThat(meta.get("applied_seq")).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT state FROM directory_generation WHERE id=?",
                String.class, pubA.generationId())).isEqualTo("PUBLISHED");
        assertThat(changeLogCount()).isEqualTo(changeLogBefore); // 二次发布零追加
        assertThat(blockedMillis).isGreaterThanOrEqualTo(300); // 真并发阻塞下界
    }

    // ------------------------------------------------------------- C-2

    /**
     * C-2 序一（增量等待）：增量事务在 metadata 锁上等待 → T7 提交后无构建代
     * → 增量走路径 A 改新活动代：双水位推进、committed 单调（H→H+1→H+2）、
     * 有效视图 = 新活动代 raw 之上事实胜出叠加。
     */
    @Test
    void c2a_incrementWaitsForT7ThenFallsBackToPathAOnNewActiveGeneration() throws Exception {
        PgDirectoryMirrorStore storeA = store(dsA);
        GenerationHandle handle = buildGeneration(storeA, runnerA);
        PgDirectoryIncrementApplier applierB = new PgDirectoryIncrementApplier(jdbcB,
                new PgDirectoryChangeLog(jdbcB));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<DirectoryPublication> a = new TxThread<>("c2a-t7", runnerA,
                () -> storeA.publish(handle), inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue();

        TxThread<IncrementResult> b = new TxThread<>("c2a-incr", runnerB,
                () -> applierB.apply(SCOPE, new IncrementBatch("evt-c2a",
                        List.of(new DirectoryDepartment("od-d2", "od-d1", "研发中心-增量")),
                        List.of())), null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // 增量阻塞于 metadata 行锁

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        DirectoryPublication pub = a.result.get();
        IncrementResult inc = b.result.get();
        assertThat(pub.committedSeqAfter()).isEqualTo(1); // T7 首发布（4 首见键）
        // T7 提交后无构建代 → 路径 A：改新活动代、双水位推进（无撕裂、单调）
        assertThat(inc.premerged()).isFalse();
        assertThat(inc.committedSeqAfter()).isEqualTo(2);
        assertThat(inc.appliedSeqAfter()).isEqualTo(2);
        var meta = metadataRow();
        assertThat(meta.get("active_generation")).isEqualTo(pub.generationId());
        assertThat(meta.get("committed_seq")).isEqualTo(2L);
        assertThat(meta.get("applied_seq")).isEqualTo(2L);
        assertThat(deptNameInView("od-d2")).isEqualTo("研发中心-增量"); // 事实胜出叠加
    }

    /**
     * C-2 序二（增量先提交）：增量预合并先提交（推 committed 不推 applied）→
     * T7 发布合并其事实：Rule-A 首见跳过（增量事实零覆盖）、committed 单调
     * 收敛（H+1 → H+2 ≥ 增量停点）、applied 一次追平。
     */
    @Test
    void c2b_incrementPremergesFirstThenT7PublishMergesMonotonically() throws Exception {
        PgDirectoryMirrorStore storeB = store(dsB);
        GenerationHandle handle = buildGeneration(storeB, runnerB); // 开放 VALIDATED 代
        PgDirectoryIncrementApplier applierA = new PgDirectoryIncrementApplier(jdbcA,
                new PgDirectoryChangeLog(jdbcA));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<IncrementResult> a = new TxThread<>("c2b-incr", runnerA,
                () -> applierA.apply(SCOPE, new IncrementBatch("evt-c2b", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张一改", null, List.of("od-d2"))))),
                inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue();

        TxThread<DirectoryPublication> b = new TxThread<>("c2b-t7", runnerB,
                () -> storeB.publish(handle), null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // T7 阻塞于 metadata 行锁

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        IncrementResult inc = a.result.get();
        DirectoryPublication pub = b.result.get();
        // 增量预合并形态：推 committed 不推 applied，停点 H+1
        assertThat(inc.premerged()).isTrue();
        assertThat(inc.committedSeqAfter()).isEqualTo(1);
        assertThat(inc.appliedSeqAfter()).isZero();
        assertThat(inc.premergedSeq()).isEqualTo(1);
        // T7 合并：首见键 = od-d1/od-d2/ou-p2（ou-p1 已有增量事实 → 零覆盖），
        // committedBefore 读到增量停点 → H+1→H+2 单调、applied 追平
        assertThat(pub.factsInserted()).isEqualTo(3);
        assertThat(pub.committedSeqBefore()).isEqualTo(1);
        assertThat(pub.committedSeqAfter()).isEqualTo(2);
        assertThat(pub.appliedSeq()).isEqualTo(2);
        // 增量事实不被发布覆盖（origin/内容保持增量面）
        var fact = factRow("PERSON", "ou-p1");
        assertThat(fact.get("origin")).isEqualTo("INCREMENT");
        assertThat(fact.get("name")).isEqualTo("张一改");
        var meta = metadataRow();
        assertThat(meta.get("committed_seq")).isEqualTo(2L);
        assertThat(meta.get("applied_seq")).isEqualTo(2L);
        assertThat(personDisplayInView("ou-p1")).isEqualTo("张一改");
    }

    // ------------------------------------------------------------- C-3

    /**
     * C-3a：增量 × 增量（同对象）——metadata 行锁串行化：两 committed_seq
     * 各 +1（H→H+1→H+2）无丢失更新，后提交者内容胜出，双条 change_log 各落。
     */
    @Test
    void c3a_sameObjectIncrementsSerializeWithoutLostUpdate() throws Exception {
        publishBaseline(store(dsA), runnerA);
        PgDirectoryIncrementApplier applierA = new PgDirectoryIncrementApplier(jdbcA,
                new PgDirectoryChangeLog(jdbcA));
        PgDirectoryIncrementApplier applierB = new PgDirectoryIncrementApplier(jdbcB,
                new PgDirectoryChangeLog(jdbcB));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<IncrementResult> a = new TxThread<>("c3a-incr1", runnerA,
                () -> applierA.apply(SCOPE, new IncrementBatch("evt-c3a-1", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张一-v1", null, List.of("od-d1"))))),
                inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue();

        TxThread<IncrementResult> b = new TxThread<>("c3a-incr2", runnerB,
                () -> applierB.apply(SCOPE, new IncrementBatch("evt-c3a-2", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张一-v2", null, List.of("od-d1"))))),
                null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // 第二增量阻塞于 metadata 行锁

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        IncrementResult incA = a.result.get();
        IncrementResult incB = b.result.get();
        assertThat(incA.committedSeqAfter()).isEqualTo(2); // 基线 H=1 → H+1
        assertThat(incB.committedSeqAfter()).isEqualTo(3); // → H+2（读到 A 提交后世界）
        var meta = metadataRow();
        assertThat(meta.get("committed_seq")).isEqualTo(3L);
        assertThat(meta.get("applied_seq")).isEqualTo(3L);
        var fact = factRow("PERSON", "ou-p1");
        assertThat(fact.get("local_revision")).isEqualTo(3L); // 基线 1 + 两增量各 +1
        assertThat(fact.get("name")).isEqualTo("张一-v2"); // 后提交者胜出（串行化）
        assertThat(jdbc.queryForList(
                "SELECT committed_seq FROM directory_change_log ORDER BY committed_seq",
                Long.class)).containsExactly(2L, 3L); // 双条各落，无丢失
    }

    /**
     * C-3b：增量 × 增量（不同对象）——互不假串行： adjudication 按对象判定，
     * 部门/人员各落各变（零 SKIP、零覆盖），合计 H+2。
     */
    @Test
    void c3b_differentObjectIncrementsApplyIndependently() throws Exception {
        publishBaseline(store(dsA), runnerA);
        PgDirectoryIncrementApplier applierA = new PgDirectoryIncrementApplier(jdbcA,
                new PgDirectoryChangeLog(jdbcA));
        PgDirectoryIncrementApplier applierB = new PgDirectoryIncrementApplier(jdbcB,
                new PgDirectoryChangeLog(jdbcB));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<IncrementResult> a = new TxThread<>("c3b-dept", runnerA,
                () -> applierA.apply(SCOPE, new IncrementBatch("evt-c3b-1",
                        List.of(new DirectoryDepartment("od-d2", "od-d1", "研发中心-改")),
                        List.of())), inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue();

        TxThread<IncrementResult> b = new TxThread<>("c3b-person", runnerB,
                () -> applierB.apply(SCOPE, new IncrementBatch("evt-c3b-2", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张一-改", null, List.of("od-d1"))))),
                null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue();

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        assertThat(a.result.get().changed()).isTrue(); // 部门变更生效（未被人员增量假串行）
        assertThat(b.result.get().changed()).isTrue(); // 人员变更生效（未被部门增量假串行）
        var meta = metadataRow();
        assertThat(meta.get("committed_seq")).isEqualTo(3L); // 基线 1 + 各 +1
        assertThat(factRow("DEPARTMENT", "od-d2").get("name")).isEqualTo("研发中心-改");
        assertThat(factRow("PERSON", "ou-p1").get("name")).isEqualTo("张一-改");
        assertThat(changeLogCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------- C-4

    /**
     * C-4：回查 × 回查（同对象）——D7 跨实例 CAS 收敛（表 7-1③ 跨实例形态）：
     * 先者 CAS 成功提交（rev 1→2、H+1）；后者阻塞于 metadata 锁，解除后
     * CAS 基线失配 → stale 丢弃零覆盖（change_log 零追加、事件零发布）。
     */
    @Test
    void c4_concurrentRecheckSecondCasCommitIsStaleWithZeroOverwrite() throws Exception {
        publishBaseline(store(dsA), runnerA); // fact ou-p1 rev=1（FULL_SCAN）
        PgDirectoryRecheckCommitter committerA = new PgDirectoryRecheckCommitter(jdbcA,
                new PgDirectoryChangeLog(jdbcA));
        PgDirectoryRecheckCommitter committerB = new PgDirectoryRecheckCommitter(jdbcB,
                new PgDirectoryChangeLog(jdbcB));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<RecheckResult> a = new TxThread<>("c4-cas1", runnerA,
                () -> committerA.commitCas(SCOPE, new RecheckEvidence(
                        DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS,
                        1L, new DirectoryPerson("ou-p1", "权威-甲", null, List.of("od-d1")),
                        null, null)), inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue();

        TxThread<RecheckResult> b = new TxThread<>("c4-cas2", runnerB,
                () -> committerB.commitCas(SCOPE, new RecheckEvidence(
                        DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS,
                        1L, new DirectoryPerson("ou-p1", "权威-乙", null, List.of("od-d1")),
                        null, null)), null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // 后者阻塞于 metadata 行锁

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        RecheckResult resA = a.result.get();
        RecheckResult resB = b.result.get();
        assertThat(resA.stale()).isFalse();
        assertThat(resA.committedSeqAfter()).isEqualTo(2); // 基线 H=1 → H+1
        assertThat(resA.publishedEvents()).hasSize(1);
        // 后者 CAS 基线失配（rev 已 2 ≠ 期望 1）→ stale 丢弃零覆盖
        assertThat(resB.stale()).isTrue();
        assertThat(resB.changeLogRef()).isNull();
        assertThat(resB.publishedEvents()).isEmpty();
        assertThat(resB.committedSeqAfter()).isEqualTo(2); // 观察到先者世界后零动作
        var fact = factRow("PERSON", "ou-p1");
        assertThat(fact.get("local_revision")).isEqualTo(2L); // 仅先者一次推进
        assertThat(fact.get("name")).isEqualTo("权威-甲"); // 后者零覆盖
        var meta = metadataRow();
        assertThat(meta.get("committed_seq")).isEqualTo(2L); // 单步推进
        assertThat(changeLogCount()).isEqualTo(1); // 零追加
    }

    // ------------------------------------------------------------- C-5

    /**
     * C-5：Inbox claim × claim（两 worker 实例同批竞争）——{@code FOR UPDATE
     * SKIP LOCKED}：A 认领未提交（持行锁）下 B 不阻塞、即刻取得后续行
     * （W5-c D11 tiebreak：received_at, event_id）→ 批次不重叠、并集全覆盖、
     * 全部 PROCESSING 且租约在场；再次 claim 零收获。
     */
    @Test
    void c5_concurrentClaimsAreDisjointByTiebreakWithSkipLocked() throws Exception {
        PgDirectoryEventInbox inboxMain = new PgDirectoryEventInbox(jdbc);
        Instant now = Instant.now();
        inboxMain.persist(EventEnvelope.received("evt-c5-1", SCOPE,
                "contact.user.updated_v3", "{}", now.minusSeconds(4)));
        inboxMain.persist(EventEnvelope.received("evt-c5-2", SCOPE,
                "contact.user.updated_v3", "{}", now.minusSeconds(3)));
        inboxMain.persist(EventEnvelope.received("evt-c5-3", SCOPE,
                "contact.user.updated_v3", "{}", now.minusSeconds(2)));
        inboxMain.persist(EventEnvelope.received("evt-c5-4", SCOPE,
                "contact.user.updated_v3", "{}", now.minusSeconds(1)));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<List<EventEnvelope>> a = new TxThread<>("c5-claimA", runnerA,
                () -> new PgDirectoryEventInbox(jdbcA).claim(2, Duration.ofMinutes(5)),
                inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue(); // A 认领未提交（持行锁）

        TxThread<List<EventEnvelope>> b = new TxThread<>("c5-claimB", runnerB,
                () -> new PgDirectoryEventInbox(jdbcB).claim(2, Duration.ofMinutes(5)),
                null, null);
        b.start();
        b.thread.join(2000);
        // SKIP LOCKED：A 持锁未提交下 B 即刻取得后两行（与 C-1..C-4 的阻塞形态相反）
        assertThat(b.thread.isAlive()).isFalse();
        assertThat(b.failure.get()).isNull();

        release.countDown();
        a.join();
        assertThat(a.failure.get()).isNull();

        // D11 tiebreak：A 取最早两行、B 取后续两行——批次不重叠、并集全覆盖
        //（成员判定面：UPDATE..RETURNING 不保留子查询序——tiebreak 定成员非定序）
        assertThat(a.result.get()).extracting(EventEnvelope::eventId)
                .containsExactlyInAnyOrder("evt-c5-1", "evt-c5-2");
        assertThat(b.result.get()).extracting(EventEnvelope::eventId)
                .containsExactlyInAnyOrder("evt-c5-3", "evt-c5-4");
        // 两实例认领全部提交后：4 行 PROCESSING 且租约在场（至多一实例持有判据）
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_event_inbox "
                + "WHERE state='PROCESSING' AND lease_expires_at > now()", Integer.class))
                .isEqualTo(4);
        assertThat(new PgDirectoryEventInbox(jdbc).claim(10, Duration.ofMinutes(1)))
                .isEmpty(); // 无 RECEIVED 残留（批次不重叠的完备面）
    }

    // ------------------------------------------------------------- C-6

    /**
     * C-6：T7 接管 × 迟到旧持有者操作（M-14 接管纪律沿矩阵 8–13 的并发形态）：
     * A 在场写（联合校验通过后未提交）；拨钟过租约；B beginGeneration 阻塞于
     * metadata 行锁——<b>接管等待在场持有者提交</b>（A 的已过检写不丢）；B 提交
     * 后旧代 ABANDONED（lease-takeover）+ 新代新 token。随后 A（旧 token）
     * 的 completePaging/publish 均被租约联合校验拒绝（token ∧ 有效期 ∧ 状态）
     * 且零写入。
     */
    @Test
    void c6_takeoverWaitsForInFlightHolderThenLateOldTokenOpsAreRejected() throws Exception {
        PgDirectoryMirrorStoreTest.MutableClock clock = new PgDirectoryMirrorStoreTest.MutableClock();
        PgDirectoryMirrorStore storeA = new PgDirectoryMirrorStore(jdbcA, clock, TTL);
        PgDirectoryMirrorStore storeB = new PgDirectoryMirrorStore(jdbcB, clock, TTL);
        GenerationHandle handleA = runnerA.inTransaction(() -> storeA.beginGeneration(SCOPE));

        CountDownLatch inCrit = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TxThread<Object> a = new TxThread<>("c6-holder", runnerA, () -> {
            storeA.writeBatch(handleA, DirectoryBatchHolder.PERSONS);
            return null;
        }, inCrit, release);
        a.start();
        assertThat(inCrit.await(5, TimeUnit.SECONDS)).isTrue(); // 在场写已过联合校验

        clock.advance(TTL.plus(Duration.ofMinutes(1))); // 拨钟 → 租约过期（两实例共钟）

        TxThread<GenerationHandle> b = new TxThread<>("c6-takeover", runnerB,
                () -> storeB.beginGeneration(SCOPE), null, null);
        b.start();
        b.thread.join(400);
        assertThat(b.thread.isAlive()).isTrue(); // 接管等待在场持有者提交（metadata 行锁）

        release.countDown();
        a.join();
        b.join();
        assertThat(a.failure.get()).isNull();
        assertThat(b.failure.get()).isNull();

        GenerationHandle handleB = b.result.get();
        assertThat(handleB.leaseToken()).isNotEqualTo(handleA.leaseToken()); // 接管轮换 token
        assertThat(handleB.generationId()).isNotEqualTo(handleA.generationId());
        // 旧代 ABANDONED（lease-takeover）；A 的在场写保留（接管等待不丢已提交数据）
        assertThat(jdbc.queryForObject("SELECT state FROM directory_generation WHERE id=?",
                String.class, handleA.generationId())).isEqualTo("ABANDONED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_person_mirror WHERE generation_id=?",
                Integer.class, handleA.generationId())).isEqualTo(2);
        // 迟到旧持有者：token 失配 → 联合校验拒绝（写面/发布面双拒绝、零写入）
        assertThatThrownBy(() -> runnerA.inTransaction(() -> {
            storeA.completePaging(handleA);
            return null;
        })).isInstanceOf(DirectoryLeaseLostException.class);
        assertThatThrownBy(() -> runnerA.inTransaction(() -> {
            storeA.publish(handleA);
            return null;
        })).isInstanceOf(DirectoryLeaseLostException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_person_mirror WHERE generation_id=?",
                Integer.class, handleB.generationId())).isZero(); // 新代零污染
    }

    // ------------------------------------------------------------- 夹具/助手

    private PgDirectoryMirrorStore store(DataSource ds) {
        return new PgDirectoryMirrorStore(new JdbcTemplate(ds), Instant::now, TTL);
    }

    private static List<DirectoryDepartment> baselineDepts() {
        return List.of(new DirectoryDepartment("od-d1", null, "总部"),
                new DirectoryDepartment("od-d2", "od-d1", "研发"));
    }

    private static List<DirectoryPerson> baselinePersons() {
        return List.of(new DirectoryPerson("ou-p1", "张一", "Zhang One", List.of("od-d1", "od-d2")),
                new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1")));
    }

    /** 基线内容常量承接（C-6 资源句柄形态——线程闭包内引用静态持有避免重复构造）。 */
    private static final class DirectoryBatchHolder {
        static final com.gallop.fastddd.identity.core.domain.DirectoryBatch PERSONS =
                com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofPersons(baselinePersons());
    }

    /** 建代到 VALIDATED（沿 {@code PgDirectoryMirrorStoreTest.buildGeneration} 形态）。 */
    private GenerationHandle buildGeneration(PgDirectoryMirrorStore store,
                                             IdentityTransactionRunner runner) {
        GenerationHandle handle = runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> {
            store.writeBatch(handle,
                    com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofDepartments(baselineDepts()));
            return null;
        });
        runner.inTransaction(() -> {
            store.writeBatch(handle, DirectoryBatchHolder.PERSONS);
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
        return handle;
    }

    /** 基线代发布（H=1、facts=4：od-d1/od-d2/ou-p1/ou-p2 首见落库）。 */
    private DirectoryPublication publishBaseline(PgDirectoryMirrorStore store,
                                                 IdentityTransactionRunner runner) {
        GenerationHandle handle = buildGeneration(store, runner);
        return runner.inTransaction(() -> store.publish(handle));
    }

    private java.util.Map<String, Object> metadataRow() {
        return jdbc.queryForMap("SELECT active_generation, committed_seq, applied_seq "
                + "FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                SCOPE.tenantKey(), SCOPE.appScope());
    }

    private java.util.Map<String, Object> factRow(String objectType, String externalId) {
        return jdbc.queryForMap("SELECT local_revision, origin, fact->>'name' AS name "
                + "FROM directory_fact WHERE tenant_id=? AND app_scope=? AND object_type=? AND external_id=?",
                SCOPE.tenantKey(), SCOPE.appScope(), objectType, externalId);
    }

    private long changeLogCount() {
        return jdbc.queryForObject("SELECT count(*) FROM directory_change_log", Long.class);
    }

    private String deptNameInView(String departmentId) {
        return view.readEffective(SCOPE).departments().stream()
                .filter(d -> d.departmentId().equals(departmentId))
                .findFirst().orElseThrow().name();
    }

    private String personDisplayInView(String openId) {
        return view.readEffective(SCOPE).persons().stream()
                .filter(p -> p.openId().equals(openId))
                .findFirst().orElseThrow().displayName();
    }

    /**
     * 事务线程：work 在事务内执行；inCrit 非 null 时在 work 返回后（提交前）
     * 置位——持锁未提交可控点；release 非 null 时等待放行后随事务提交。
     * 失败时 inCrit 兜底置位防主线程死等。
     */
    static final class TxThread<T> {
        final Thread thread;
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        TxThread(String name, IdentityTransactionRunner runner,
                 IdentityTransactionRunner.IdentityTransactionCallback<T> work,
                 CountDownLatch inCrit, CountDownLatch release) {
            thread = new Thread(() -> {
                try {
                    result.set(runner.inTransaction(() -> {
                        T r = work.doInTransaction();
                        if (inCrit != null) {
                            inCrit.countDown();
                        }
                        if (release != null) {
                            try {
                                if (!release.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("release 栅栏超时（测试编排故障）");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("release 栅栏等待被中断（测试编排故障）", e);
                            }
                        }
                        return r;
                    }));
                } catch (Throwable x) {
                    if (inCrit != null) {
                        inCrit.countDown();
                    }
                    failure.set(x);
                }
            }, name);
        }

        void start() {
            thread.start();
        }

        void join() throws InterruptedException {
            thread.join(10_000);
        }
    }
}
