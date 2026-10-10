package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EffectiveSnapshot;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.domain.RetentionReport;
import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-f f-1 R-4：目录事实回收执行器四面验收（W5 设计 §8.3 M-26；W5-f 设计
 * §3/§7.2-R4）。覆盖：终结非活动代镜像回收（active/构建代保持）+ fact 行集
 * 全等（含墓碑）+ readEffective 不变 + Inbox PROCESSED 到期清理（未到期/
 * RECEIVED/FAILED 保持）+ change_log 水位约束（活动代激活条目保留——P3-1
 * 锚定断言）+ 缺省不限期零清理 + preview 只读 + 幂等重跑收敛 + 面①谓词
 * SQL 形态守护（C-1：metadata 联读 ∧ 无 active 绑参）+ NULL-active scope
 * ABANDONED 代镜像可清（O-6 论证 (d)）。
 *
 * <p>代构造经真实端口（begin/writeBatch/completePaging/validate/publish/
 * abandon），change_log/inbox 行经直插 SQL 控参（created_at/processed_at
 * 用 {@code now() - interval} 老化）；时钟缺省 {@code Instant::now} 与库端
 * {@code now()} 同机对齐，30d/7d/14d 边距消解时差。</p>
 */
class PgDirectoryRetentionExecutorTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-ret", "app");
    private static final DirectoryScope VIRGIN_SCOPE = new DirectoryScope("tenant-ret", "virgin");

    /** 面③保留期（14d）：30d 老条目到龄、直插新条目未到龄。 */
    private static final RetentionPolicy POLICY = new RetentionPolicy(Duration.ofDays(7), Duration.ofDays(14));
    /** 缺省形态：inbox 7d + change-log 不限期（面③零清理）。 */
    private static final RetentionPolicy UNLIMITED_LOG_POLICY = new RetentionPolicy(Duration.ofDays(7), null);

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private IdentityTransactionRunner runner;
    private PgDirectoryMirrorStore store;
    private PgDirectoryEffectiveView view;
    private PgDirectoryIncrementApplier applier;
    private PgDirectoryRecheckCommitter committer;
    private PgDirectoryRetentionExecutor executor;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        runner = Support.runner(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        store = new PgDirectoryMirrorStore(jdbc, Instant::now, Duration.ofMinutes(10));
        view = new PgDirectoryEffectiveView(jdbc);
        PgDirectoryChangeLog changeLog = new PgDirectoryChangeLog(jdbc);
        applier = new PgDirectoryIncrementApplier(jdbc, changeLog);
        committer = new PgDirectoryRecheckCommitter(jdbc, changeLog);
        executor = new PgDirectoryRetentionExecutor(jdbc);
    }

    /** 全量发布一代（begin→write→complete→validate→publish 单事务）。 */
    private void publishGeneration(DirectoryScope scope, List<DirectoryDepartment> departments,
                                   List<DirectoryPerson> persons) {
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(scope);
            store.writeBatch(handle, new DirectoryBatch(departments, persons));
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        });
    }

    /** 写一批后放弃（ABANDONED 终结态，镜像行留存）。 */
    private void writeThenAbandon(DirectoryScope scope, List<DirectoryDepartment> departments,
                                  List<DirectoryPerson> persons) {
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(scope);
            store.writeBatch(handle, new DirectoryBatch(departments, persons));
            store.abandon(handle, "retention-test 放弃");
            return null;
        });
    }

    /** 镜像表无 scope 列（挂在 generation 上）——存活代归属经 generation JOIN 判定。 */
    private List<String> personMirrorGenerations(DirectoryScope scope) {
        return jdbc.queryForList("SELECT DISTINCT pm.generation_id FROM directory_person_mirror pm "
                        + "JOIN directory_generation g ON g.id = pm.generation_id "
                        + "WHERE g.tenant_id=? AND g.app_scope=? ORDER BY pm.generation_id",
                String.class, scope.tenantKey(), scope.appScope());
    }

    /** fact 行集快照（含墓碑与 updated_seq——行集全等断言载体）。 */
    private List<java.util.Map<String, Object>> factRowSet(DirectoryScope scope) {
        return jdbc.queryForList("SELECT object_type, external_id, tombstone, origin, updated_seq "
                + "FROM directory_fact WHERE tenant_id=? AND app_scope=? "
                + "ORDER BY object_type, external_id", scope.tenantKey(), scope.appScope());
    }

    private void insertInboxRow(String eventId, DirectoryScope scope, String state, String processedAtExpr) {
        jdbc.update("INSERT INTO directory_event_inbox (event_id, tenant_id, app_scope, event_type, payload, "
                        + "state, processed_at) VALUES (?, ?, ?, 'contact.user.updated_v3', '{}'::jsonb, ?, "
                        + (processedAtExpr == null ? "NULL" : processedAtExpr) + ")",
                eventId, scope.tenantKey(), scope.appScope(), state);
    }

    private void insertChangeLogRow(String id, DirectoryScope scope, String kind, String generationId,
                                    long committedSeq, String createdAtExpr) {
        jdbc.update("INSERT INTO directory_change_log (id, tenant_id, app_scope, kind, generation_id, "
                        + "committed_seq, summary, created_at) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, "
                        + createdAtExpr + ")",
                id, scope.tenantKey(), scope.appScope(), kind, generationId, committedSeq);
    }

    private List<String> survivingChangeLogKinds(DirectoryScope scope) {
        return jdbc.queryForList("SELECT kind || '@' || committed_seq FROM directory_change_log "
                        + "WHERE tenant_id=? AND app_scope=? ORDER BY committed_seq, kind",
                String.class, scope.tenantKey(), scope.appScope());
    }

    /**
     * 面① + 读者不变量：g1（PUBLISHED 非 active）与 g4（ABANDONED）镜像回收；
     * active 代 g2 与构建代 g3 镜像保持；fact 行集全等（含墓碑）+ readEffective
     * 快照全等（M-26「不删最新事实/墓碑、读者可见面零变化」）。
     */
    @Test
    void terminalNonActiveMirrorsPurgedWhileActiveAndBuildingKeptAndReaderFacesUnchanged() {
        // g1 发布（D1 根 + P1/P2∈D1）：H=1，applied=1，active=g1
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1")),
                        new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1"))));
        // 回查墓碑：P2 ABSENT → fact 墓碑行 @seq2（H=2）
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p2", RecheckConclusion.ABSENT, 1L, null, null, null)));
        // 增量新人员 P3∈D1：fact @seq3（H=3）
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-p3",
                List.of(), List.of(new DirectoryPerson("ou-p3", "王三", null, List.of("od-d1"))))));
        // g2 同内容发布：零新事实 → H=3、applied=3、active=g2（start_seq=3）；
        // g1 转 PUBLISHED 非 active（可回收）；P2 墓碑（seq2 ≤ applied3）自此可见
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1")),
                        new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1"))));
        // g4：写后放弃（ABANDONED，人员 P5 无部门挂靠 → 零关系行）
        writeThenAbandon(SCOPE,
                List.of(new DirectoryDepartment("od-d4", null, "临时")),
                List.of(new DirectoryPerson("ou-p5", "赵五", null, List.of())));
        // g3：开放构建代（BUILDING，镜像保持）
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(SCOPE);
            store.writeBatch(handle, new DirectoryBatch(
                    List.of(), List.of(new DirectoryPerson("ou-p4", "李四", null, List.of()))));
            return null;
        });
        String activeGenId = jdbc.queryForObject(
                "SELECT active_generation FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
        String buildingGenId = jdbc.queryForObject(
                "SELECT id FROM directory_generation WHERE tenant_id=? AND app_scope=? AND state='BUILDING'",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());

        List<java.util.Map<String, Object>> factBefore = factRowSet(SCOPE);
        EffectiveSnapshot effectiveBefore = view.readEffective(SCOPE);

        RetentionReport report = executor.execute(SCOPE, POLICY);

        // 面①：g1（person 2/dept 1/relation 2）+ g4（person 1/dept 1/relation 0）
        assertThat(report.preview()).isFalse();
        assertThat(report.mirrorPersonRows()).isEqualTo(3);
        assertThat(report.mirrorDepartmentRows()).isEqualTo(2);
        assertThat(report.mirrorRelationRows()).isEqualTo(2);
        // 存活镜像恰属 active g2 + 构建 g3；generation 行本体保留（F2）
        assertThat(personMirrorGenerations(SCOPE)).containsExactly(activeGenId, buildingGenId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_generation WHERE tenant_id=? AND app_scope=?",
                Integer.class, SCOPE.tenantKey(), SCOPE.appScope())).isEqualTo(4);
        // fact 行集全等（含 P2 墓碑）+ readEffective 快照全等
        assertThat(factRowSet(SCOPE)).isEqualTo(factBefore);
        assertThat(view.readEffective(SCOPE)).usingRecursiveComparison().isEqualTo(effectiveBefore);
    }

    /** 面②：仅 PROCESSED∧到期清理；未到期/RECEIVED/FAILED 保持 + 缺省不限期零清理（面③）。 */
    @Test
    void inboxProcessedPurgedOnlyWhenExpiredAndDefaultUnlimitedChangeLogKeepsEverything() {
        insertInboxRow("evt-old-processed", SCOPE, "PROCESSED", "now() - interval '30 days'");
        insertInboxRow("evt-new-processed", SCOPE, "PROCESSED", "now()");
        insertInboxRow("evt-old-received", SCOPE, "RECEIVED", null);
        insertInboxRow("evt-old-failed", SCOPE, "FAILED", "now() - interval '30 days'");
        insertChangeLogRow("chg-old-recheck", SCOPE, "RECHECK_COMMITTED", null, 5L,
                "now() - interval '30 days'");

        RetentionReport report = executor.execute(SCOPE, UNLIMITED_LOG_POLICY);

        assertThat(report.inboxRowsPurged()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT event_id FROM directory_event_inbox ORDER BY event_id", String.class))
                .containsExactly("evt-new-processed", "evt-old-failed", "evt-old-received");
        // 缺省不限期：老 change_log 条目零清理、无水位跳过登记
        assertThat(report.changeLogRowsPurged()).isZero();
        assertThat(report.changeLogSkippedByWatermark()).isFalse();
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(1);
    }

    /**
     * 面③水位（P3-1 锚定）：W = min(start_seq) over（非终态代 ∪ {活动代}）。
     * 布局：g1 发布（start 0）→ 增量×2（H=3）→ g2 同内容发布（active，start 3）
     * → g3 构建代（start 3）。老条目 committed_seq &lt; 3 可清；= 3 被活动代/
     * 构建代锚定保留；<b>GENERATION_ACTIVATED@g2 老条目保留 = 活动代激活条目
     * 锚定断言</b>（迟到同代重复发布 B-5 判据不失守）。
     */
    @Test
    void changeLogWatermarkKeepsActiveActivationAndProtectsBelowWatermark() {
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1"))));
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-inc-1",
                List.of(), List.of(new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1"))))));
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-inc-2",
                List.of(), List.of(new DirectoryPerson("ou-p3", "王三", null, List.of("od-d1"))))));
        String activeGenId = jdbc.queryForObject(
                "SELECT active_generation FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1"))));
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(SCOPE);
            store.writeBatch(handle, new DirectoryBatch(
                    List.of(), List.of(new DirectoryPerson("ou-p4", "李四", null, List.of()))));
            return null;
        });
        // 增量条目 @seq2/@seq3 老化；直插 @seq1 RECHECK 老条目（1 &lt; W=3 可清）、
        // @seq3 新 RECHECK（未到龄）、活动代激活条目 @seq3 老化（P3-1 锚定面）
        jdbc.update("UPDATE directory_change_log SET created_at = now() - interval '30 days' "
                + "WHERE tenant_id=? AND app_scope=?", SCOPE.tenantKey(), SCOPE.appScope());
        insertChangeLogRow("chg-old-recheck-1", SCOPE, "RECHECK_COMMITTED", null, 1L,
                "now() - interval '30 days'");
        insertChangeLogRow("chg-new-recheck-3", SCOPE, "RECHECK_COMMITTED", null, 3L, "now()");
        insertChangeLogRow("chg-activation-active", SCOPE, "GENERATION_ACTIVATED", activeGenId, 3L,
                "now() - interval '30 days'");

        RetentionReport report = executor.execute(SCOPE, POLICY);

        // 可清恰两条：RECHECK@1（老 ∧ 1<W）+ INCREMENT@2（老 ∧ 2<W）
        assertThat(report.changeLogRowsPurged()).isEqualTo(2);
        // 到龄 ∧ 水位锚定条目在场（INCREMENT@3 与 ACTIVATED@3）→ 登记标志置位
        assertThat(report.changeLogSkippedByWatermark()).isTrue();
        assertThat(survivingChangeLogKinds(SCOPE)).containsExactly(
                "GENERATION_ACTIVATED@3",   // P3-1：活动代激活条目（老）恒保留
                "INCREMENT_APPLIED@3",       // 老但 committed_seq=3 ≥ W=3（活动/构建代锚定）
                "RECHECK_COMMITTED@3");      // 未到龄（保留期是唯一约束）
    }

    /** O-6 论证 (d)：active 为 NULL（从未发布 scope）的 ABANDONED 代镜像可清。 */
    @Test
    void nullActiveScopeAbandonedMirrorsArePurged() {
        writeThenAbandon(VIRGIN_SCOPE,
                List.of(new DirectoryDepartment("od-v1", null, "虚部")),
                List.of(new DirectoryPerson("ou-v1", "范一", null, List.of("od-v1"))));
        assertThat(view.readEffective(VIRGIN_SCOPE).activeGenerationId()).isEmpty(); // active NULL 前提

        RetentionReport report = executor.execute(VIRGIN_SCOPE, POLICY);

        assertThat(report.mirrorPersonRows()).isEqualTo(1);
        assertThat(report.mirrorDepartmentRows()).isEqualTo(1);
        assertThat(report.mirrorRelationRows()).isEqualTo(1);
        assertThat(Support.count(dataSource, "directory_person_mirror")).isZero();
        assertThat(Support.count(dataSource, "directory_department_mirror")).isZero();
        assertThat(Support.count(dataSource, "directory_relation_mirror")).isZero();
    }

    /** preview 只读（表行数不变）且各面计数与随后 execute 一致。 */
    @Test
    void previewIsReadOnlyAndCountsMatchSubsequentExecute() {
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1"))));
        publishGeneration(SCOPE,
                List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张一", null, List.of("od-d1"))));
        writeThenAbandon(SCOPE,
                List.of(new DirectoryDepartment("od-d2", null, "临时")),
                List.of(new DirectoryPerson("ou-p5", "赵五", null, List.of("od-d2"))));
        insertInboxRow("evt-old-processed", SCOPE, "PROCESSED", "now() - interval '30 days'");

        int personMirrorBefore = Support.count(dataSource, "directory_person_mirror");
        RetentionReport preview = executor.preview(SCOPE, UNLIMITED_LOG_POLICY);

        assertThat(preview.preview()).isTrue();
        assertThat(preview.mirrorPersonRows()).isEqualTo(2); // g1 1 行 + g4 1 行
        assertThat(preview.mirrorDepartmentRows()).isEqualTo(2);
        assertThat(preview.mirrorRelationRows()).isEqualTo(2); // g1 P1∈D1 + g4 P5∈D2
        assertThat(preview.inboxRowsPurged()).isEqualTo(1);
        assertThat(preview.changeLogRowsPurged()).isZero();
        // 只读：全表行数不变
        assertThat(Support.count(dataSource, "directory_person_mirror")).isEqualTo(personMirrorBefore);
        assertThat(Support.count(dataSource, "directory_event_inbox")).isEqualTo(1);

        RetentionReport executed = executor.execute(SCOPE, UNLIMITED_LOG_POLICY);
        assertThat(executed.mirrorPersonRows()).isEqualTo(preview.mirrorPersonRows());
        assertThat(executed.mirrorDepartmentRows()).isEqualTo(preview.mirrorDepartmentRows());
        assertThat(executed.mirrorRelationRows()).isEqualTo(preview.mirrorRelationRows());
        assertThat(executed.inboxRowsPurged()).isEqualTo(preview.inboxRowsPurged());
    }

    /** 幂等重跑收敛（已清状态重跑全零）+ execute 入口拒绝外层事务（O-1 纪律）。 */
    @Test
    void reRunConvergesToZeroAndExecuteRejectsAmbientTransaction() {
        writeThenAbandon(SCOPE,
                List.of(new DirectoryDepartment("od-d2", null, "临时")),
                List.of(new DirectoryPerson("ou-p5", "赵五", null, List.of())));
        assertThat(executor.execute(SCOPE, POLICY).mirrorPersonRows()).isEqualTo(1);

        RetentionReport rerun = executor.execute(SCOPE, POLICY);
        assertThat(rerun.mirrorRowsTotal()).isZero();
        assertThat(rerun.inboxRowsPurged()).isZero();
        assertThat(rerun.changeLogRowsPurged()).isZero();
        assertThat(rerun.changeLogSkippedByWatermark()).isFalse();

        // 入口拒绝加入外层事务（分面批间提交形态不汇入调用方长事务）
        assertThatThrownBy(() -> runner.inTransaction(() ->
                        executor.execute(SCOPE, POLICY)))
                .isInstanceOf(IdentityTransactionActiveException.class)
                .hasMessageContaining("directory-retention:execute");
    }

    /**
     * C-1 面①谓词形态守护：三条镜像 DELETE 的 SQL 文本必须内联联读
     * directory_publish_metadata（JOIN）+ IS DISTINCT FROM 判非 active，且
     * 占位符恰 3 枚（tenant/app_scope/批上限）——active 值绝不出现在绑定参数
     * （语句外预读参数形态 = TOCTOU 窗口，明文禁止）。
     */
    @Test
    void faceOnePredicateSqlShapeGuard() {
        List<String> faceOneSqls = List.of(
                PgDirectoryRetentionExecutor.PURGE_PERSON_MIRROR_SQL,
                PgDirectoryRetentionExecutor.PURGE_DEPARTMENT_MIRROR_SQL,
                PgDirectoryRetentionExecutor.PURGE_RELATION_MIRROR_SQL);
        assertThat(faceOneSqls).allSatisfy(sql -> {
            assertThat(sql).contains("JOIN directory_publish_metadata");
            assertThat(sql).contains("IS DISTINCT FROM m.active_generation");
            assertThat(sql).doesNotContain("active_generation = ?"); // 无 active 绑参形态
            assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(3); // tenant/scope/LIMIT
        });
    }
}
