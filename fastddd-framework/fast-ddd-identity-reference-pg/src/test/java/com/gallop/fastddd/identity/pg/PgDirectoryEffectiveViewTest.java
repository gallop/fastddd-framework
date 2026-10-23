package com.gallop.fastddd.identity.pg;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * W5-c 有效视图 PG 验收测试（W5 设计 §8.1 四要点 a–d：阈值门 / 事实胜出 /
 * 拒绝排除 / 成员并集与墓碑排除）。
 */
class PgDirectoryEffectiveViewTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-eff", "app");
    private static final DirectoryScope VIRGIN_SCOPE = new DirectoryScope("tenant-eff", "virgin");

    private DataSource dataSource;
    private PgDirectoryEffectiveView view;
    private PgDirectoryMirrorStore store;
    private PgDirectoryIncrementApplier applier;
    private PgDirectoryRecheckCommitter committer;
    private IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        runner = Support.runner(dataSource);
        JdbcTemplate template = new JdbcTemplate(dataSource);
        view = new PgDirectoryEffectiveView(template);
        store = new PgDirectoryMirrorStore(template, Instant::now, Duration.ofMinutes(10));
        PgDirectoryChangeLog changeLog = new PgDirectoryChangeLog(template);
        applier = new PgDirectoryIncrementApplier(template, changeLog);
        committer = new PgDirectoryRecheckCommitter(template, changeLog);
        jdbc = template;
        publishBaseline();
    }

    /** 基线代：D1 根、D2(父=D1)、P1∈{D1,D2}、P2∈{D1}——发布后 applied=1。 */
    private void publishBaseline() {
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(SCOPE);
            store.writeBatch(handle, new DirectoryBatch(
                    List.of(new DirectoryDepartment("od-d1", null, "总部"),
                            new DirectoryDepartment("od-d2", "od-d1", "研发")),
                    List.of(new DirectoryPerson("ou-p1", "张一", "Zhang One", List.of("od-d1", "od-d2")),
                            new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1")))));
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        });
    }

    private EffectiveSnapshot read() {
        return view.readEffective(SCOPE);
    }

    @Test
    void pointA_thresholdGateHidesUnappliedFactsUntilWatermarkCatchesUp() {
        // 预合并（路径 B）：事实 updated_seq=2 但 applied=1 → 有效视图不可见
        runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS, 1L,
                new DirectoryPerson("ou-p1", "权威改名", null, List.of("od-d1")), null, null)));

        EffectiveSnapshot gated = read();
        assertThat(gated.appliedSeq()).isEqualTo(1);
        assertThat(gated.persons()).extracting(p -> p.displayName())
                .containsExactly("张一", "李二"); // 旧世界（事实被阈值门挡住）

        // T7 发布 → applied=2 → 事实生效
        finishOpenGeneration();
        EffectiveSnapshot visible = read();
        assertThat(visible.appliedSeq()).isEqualTo(2);
        assertThat(visible.persons()).extracting(p -> p.displayName())
                .containsExactly("权威改名", "李二");
    }

    @Test
    void pointB_factWinsDisplayAndMembershipForRecheckOrIncrementFacts() {
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-1",
                List.of(new DirectoryDepartment("od-d2", "od-d1", "研发中心")), // 部门名变更
                List.of(new DirectoryPerson("ou-p1", "张一改", "Zhang One", List.of("od-d2")))))); // 人员收敛单部门

        EffectiveSnapshot snapshot = read();
        var p1 = snapshot.persons().stream().filter(p -> p.openId().equals("ou-p1")).findFirst().orElseThrow();
        assertThat(p1.displayName()).isEqualTo("张一改"); // 事实胜出（raw 为"张一"）
        assertThat(p1.departmentIds()).containsExactly("od-d2"); // 事实集替换 raw 关系 {d1,d2}
        var p2 = snapshot.persons().stream().filter(p -> p.openId().equals("ou-p2")).findFirst().orElseThrow();
        assertThat(p2.displayName()).isEqualTo("李二"); // raw 人员：raw 胜出
        assertThat(p2.departmentIds()).containsExactly("od-d1"); // 关系镜像源
        var d2 = snapshot.departments().stream().filter(d -> d.departmentId().equals("od-d2")).findFirst().orElseThrow();
        assertThat(d2.name()).isEqualTo("研发中心"); // 部门事实胜出
    }

    /**
     * R-6（W5-f f-3；M-13 变更差集精确断言）：变更前后差集恰等该变更 +
     * 预合并期不进差集（applied 推进后进）——路径 B 全弧：开放代在场 →
     * 增量预合并（updated_seq=2 但 applied=1）→ 快照与基线全等（差集为空，
     * 生效门槛半面）→ T7 发布 → applied=2 → 差集恰为 {od-d2 部门名变更,
     * ou-p1 显示名/部门集变更} 两条目、零添加零删除零多余（全清单逐字段
     * tuple 断言 = 差集断言的最强形态；EffectivePerson/EffectiveDepartment
     * 无结构化 equals——身份相等，沿模块 extracting 先例）。
     */
    @Test
    void r6m13DiffSetEqualsExactlyTheChangeAndPremergeStaysGatedUntilApplied() {
        // 预合并（路径 B）：开放代在场 → 增量事实 updated_seq=2、applied=1
        runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-r6",
                List.of(new DirectoryDepartment("od-d2", "od-d1", "研发中心")),
                List.of(new DirectoryPerson("ou-p1", "张一改", "Zhang One", List.of("od-d2"))))));

        // 生效门槛半面：applied 推进前变更不进差集（快照与基线逐字段全等）
        EffectiveSnapshot gated = read();
        assertThat(gated.appliedSeq()).isEqualTo(1);
        assertThat(gated.persons()).extracting(
                        EffectiveSnapshot.EffectivePerson::openId,
                        EffectiveSnapshot.EffectivePerson::displayName,
                        EffectiveSnapshot.EffectivePerson::departmentIds)
                .containsExactly(
                        tuple("ou-p1", "张一", List.of("od-d1", "od-d2")),
                        tuple("ou-p2", "李二", List.of("od-d1")));
        assertThat(gated.departments()).extracting(
                        EffectiveSnapshot.EffectiveDepartment::departmentId,
                        EffectiveSnapshot.EffectiveDepartment::parentDepartmentId,
                        EffectiveSnapshot.EffectiveDepartment::name)
                .containsExactly(
                        tuple("od-d1", null, "总部"),
                        tuple("od-d2", "od-d1", "研发"));

        // T7 发布 → applied 推进 → 差集恰等该变更（ou-p1 事实胜出 + od-d2 事实胜出）
        finishOpenGeneration();
        EffectiveSnapshot after = read();
        assertThat(after.appliedSeq()).isEqualTo(2);
        assertThat(after.persons()).extracting(
                        EffectiveSnapshot.EffectivePerson::openId,
                        EffectiveSnapshot.EffectivePerson::displayName,
                        EffectiveSnapshot.EffectivePerson::departmentIds)
                .containsExactly(
                        tuple("ou-p1", "张一改", List.of("od-d2")),
                        tuple("ou-p2", "李二", List.of("od-d1")));
        assertThat(after.departments()).extracting(
                        EffectiveSnapshot.EffectiveDepartment::departmentId,
                        EffectiveSnapshot.EffectiveDepartment::parentDepartmentId,
                        EffectiveSnapshot.EffectiveDepartment::name)
                .containsExactly(
                        tuple("od-d1", null, "总部"),
                        tuple("od-d2", "od-d1", "研发中心"));
    }

    @Test
    void pointC_openIdRejectionExcludesPersonUntilCleared() {
        jdbc.update("INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason) "
                + "VALUES ('feishu', ?, ?, 'open_id', 'ou-p1', '明确停用')", SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p2"); // P1 被排除

        jdbc.update("UPDATE external_rejection SET cleared_at = now() WHERE external_id = 'ou-p1'");
        assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p1", "ou-p2"); // 清除后回归

        // 非 open_id 型拒绝不影响人员面（union_id 型仅登拒绝面）
        jdbc.update("INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason) "
                + "VALUES ('feishu', ?, ?, 'union_id', 'on_p1', 'union 拒绝')", SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p1", "ou-p2");
    }

    /**
     * R6-P2-1 登记面（W5-f f-2 R-2；W5-d 复审 D13 承接）：非 open_id 型活跃
     * 拒绝行不剔除 + rejection-keydomain-skipped 结构化日志在场（unmappedTypes
     * 类型名清单 O-3/unmappedRowCount/note 固定语义）；open_id 照旧剔除（双型
     * 并存双日志各得其所）；清除后登记零输出（零在场零噪声）。
     */
    @Test
    void r6p21UnmappedRejectionTypesAreNotExcludedButLogged() {
        Logger viewLogger = (Logger) org.slf4j.LoggerFactory.getLogger(PgDirectoryEffectiveView.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        viewLogger.addAppender(appender);
        try {
            // 仅 union_id 型活跃拒绝行：人员不被剔除 + 登记日志在场
            jdbc.update("INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason) "
                            + "VALUES ('feishu', ?, ?, 'union_id', 'on_p1', 'union 拒绝')",
                    SCOPE.tenantKey(), SCOPE.appScope());
            assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p1", "ou-p2");
            String skipped = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("rejection-keydomain-skipped"))
                    .findFirst().orElseThrow();
            assertThat(skipped)
                    .contains("scope=tenant-eff/app")
                    .contains("unmappedTypes={union_id}")
                    .contains("unmappedRowCount=1")
                    .contains("note=not-excluded-per-key-mapping");
            assertThat(appender.list)
                    .noneMatch(e -> e.getFormattedMessage().contains("rejection-exclusion"));

            // 双型并存：open_id 剔除照旧（rejection-exclusion）+ 登记日志各得其所
            jdbc.update("INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason) "
                            + "VALUES ('feishu', ?, ?, 'open_id', 'ou-p2', '明确停用')",
                    SCOPE.tenantKey(), SCOPE.appScope());
            assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p1");
            assertThat(appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("rejection-exclusion")).count()).isEqualTo(1);
            assertThat(appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("rejection-keydomain-skipped")).count()).isEqualTo(2);

            // 清除 union_id 行后：登记日志不再输出（零在场零噪声）
            jdbc.update("UPDATE external_rejection SET cleared_at = now() WHERE external_id_type = 'union_id'");
            read();
            assertThat(appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("rejection-keydomain-skipped")).count()).isEqualTo(2);
        } finally {
            viewLogger.detachAppender(appender);
        }
    }

    @Test
    void pointD_inThresholdTombstoneExcludesObjectAndUnionFallbackIncludesFactOnlyObjects() {
        // 阈值内墓碑：P2 被整体排除（raw 侧同剔）
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p2", RecheckConclusion.ABSENT, 1L, null, null, null)));
        assertThat(read().persons()).extracting(p -> p.openId()).containsExactly("ou-p1");

        // union 兜底：FULL_SCAN 事实对象（不在任何镜像代）仍纳入有效面
        jdbc.update("INSERT INTO directory_fact (tenant_id, app_scope, object_type, external_id, local_revision, "
                        + "fact, tombstone, origin, change_ref, pending_confirm, source_generation, updated_seq) "
                        + "VALUES (?, ?, 'DEPARTMENT', 'od-legacy', 1, "
                        + "'{\"objectType\":\"DEPARTMENT\",\"externalId\":\"od-legacy\",\"firstSeenGeneration\":\"g0\"}'::jsonb, "
                        + "false, 'FULL_SCAN', NULL, false, 'g0', 0)",
                SCOPE.tenantKey(), SCOPE.appScope());
        EffectiveSnapshot snapshot = read();
        assertThat(snapshot.departments()).extracting(d -> d.departmentId())
                .containsExactly("od-d1", "od-d2", "od-legacy"); // 排序稳定
        var legacy = snapshot.departments().stream()
                .filter(d -> d.departmentId().equals("od-legacy")).findFirst().orElseThrow();
        assertThat(legacy.name()).isNull(); // FULL_SCAN 部门事实无 name，兜底形态
    }

    @Test
    void neverPublishedScopeYieldsEmptySnapshotWithoutFabricating() {
        EffectiveSnapshot empty = view.readEffective(VIRGIN_SCOPE);
        assertThat(empty.activeGenerationId()).isEmpty();
        assertThat(empty.appliedSeq()).isEqualTo(0);
        assertThat(empty.departments()).isEmpty();
        assertThat(empty.persons()).isEmpty();
    }

    @Test
    void readEffectiveJoinsCallerTransactionWhenPresent() {
        // 调用方事务在场 → 汇入（不嵌套自开）；同批内读到本事务写入的拒绝行
        EffectiveSnapshot inTx = runner.inTransaction(() -> {
            jdbc.update("INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason) "
                    + "VALUES ('feishu', ?, ?, 'open_id', 'ou-p2', 'tx 内拒绝')", SCOPE.tenantKey(), SCOPE.appScope());
            return view.readEffective(SCOPE);
        });
        assertThat(inTx.persons()).extracting(p -> p.openId()).containsExactly("ou-p1"); // 本事务拒绝已生效
    }

    /** 完成开放代（与基线同镜像内容）→ 发布使 applied 收敛。 */
    private void finishOpenGeneration() {
        String openGenId = jdbc.queryForObject("SELECT id FROM directory_generation "
                + "WHERE tenant_id=? AND app_scope=? AND state IN ('BUILDING','VALIDATING','VALIDATED')",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
        String token = jdbc.queryForObject("SELECT lease_token FROM directory_publish_metadata "
                + "WHERE tenant_id=? AND app_scope=?", String.class, SCOPE.tenantKey(), SCOPE.appScope());
        runner.inTransaction(() -> {
            GenerationHandle handle = new GenerationHandle(SCOPE, openGenId, token);
            store.writeBatch(handle, new DirectoryBatch(
                    List.of(new DirectoryDepartment("od-d1", null, "总部"),
                            new DirectoryDepartment("od-d2", "od-d1", "研发")),
                    List.of(new DirectoryPerson("ou-p1", "张一", "Zhang One", List.of("od-d1", "od-d2")),
                            new DirectoryPerson("ou-p2", "李二", null, List.of("od-d1")))));
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        });
    }
}
