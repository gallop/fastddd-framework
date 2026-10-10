package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.IncrementResult;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
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
 * W5-c 增量落库 PG 验收测试（W5 设计 §6.1/§6.2 表 6-1 ①–⑤ 全场景）。
 *
 * <p>① 空闲态有效增量（含混合批分组事件）；② 空批/重复投递零写；③ 预合并 →
 * T7 发布 h 收敛；④ T7 先行后增量回落路径 A；⑤ 墓碑跳过 + 首增富化（扫描事实
 * 无 departmentIds → 首次增量必判变更）。</p>
 */
class PgDirectoryIncrementApplierTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-inc", "app");
    private static final String EVENT_PREFIX = "dir-change:" + SCOPE + ":";

    private DataSource dataSource;
    private PgDirectoryIncrementApplier applier;
    private PgDirectoryMirrorStore store;
    private com.gallop.fastddd.identity.core.port.IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        runner = Support.runner(dataSource);
        store = new PgDirectoryMirrorStore(new JdbcTemplate(dataSource), Instant::now, Duration.ofMinutes(10));
        applier = new PgDirectoryIncrementApplier(new JdbcTemplate(dataSource),
                new PgDirectoryChangeLog(new JdbcTemplate(dataSource)));
        jdbc = new JdbcTemplate(dataSource);
    }

    // ------------------------------------------------------------- 表 6-1①

    @Test
    void table61Row1_idleIncrementAdvancesBothWatermarksAndEmitsGroupedEvents() {
        IncrementBatch batch = new IncrementBatch("evt-1",
                List.of(new DirectoryDepartment("od-dept-9", null, "新部门")),
                List.of(new DirectoryPerson("ou-new-1", "张三", null, List.of("od-dept-9"))));
        IncrementResult result = runner.inTransaction(() -> applier.apply(SCOPE, batch));

        assertThat(result.changed()).isTrue();
        assertThat(result.premerged()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(1);
        assertThat(result.appliedSeqAfter()).isEqualTo(1);
        assertThat(result.premergedSeq()).isNull();
        assertThat(result.changeLogRef()).hasSize(26); // 预铸 ULID
        // 混合批按类型分组：人员/部门各一事件 id（§9.1 格式）
        assertThat(result.publishedEventIds()).containsExactly(
                EVENT_PREFIX + "1:PERSON_CHANGED", EVENT_PREFIX + "1:DEPARTMENT_CHANGED");

        var meta = jdbc.queryForMap("SELECT committed_seq, applied_seq FROM directory_publish_metadata "
                + "WHERE tenant_id=? AND app_scope=?", SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(meta.get("committed_seq")).isEqualTo(1L);
        assertThat(meta.get("applied_seq")).isEqualTo(1L);

        var person = jdbc.queryForMap("SELECT local_revision, tombstone, origin, change_ref, updated_seq "
                + "FROM directory_fact WHERE tenant_id=? AND app_scope=? AND object_type='PERSON' AND external_id='ou-new-1'",
                SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(person.get("local_revision")).isEqualTo(1L);
        assertThat(person.get("tombstone")).isEqualTo(false);
        assertThat(person.get("origin")).isEqualTo("INCREMENT");
        assertThat(person.get("change_ref")).isEqualTo(result.changeLogRef());
        assertThat(person.get("updated_seq")).isEqualTo(1L);

        var log = jdbc.queryForMap("SELECT kind, committed_seq FROM directory_change_log WHERE id=?",
                result.changeLogRef());
        assertThat(log.get("kind")).isEqualTo("INCREMENT_APPLIED");
        assertThat(log.get("committed_seq")).isEqualTo(1L);
    }

    @Test
    void table61Row1_repeatDeliveryOfSameBatchIsZeroWrite() {
        IncrementBatch batch = new IncrementBatch("evt-1",
                List.of(new DirectoryDepartment("od-dept-9", null, "新部门")),
                List.of(new DirectoryPerson("ou-new-1", "张三", null, List.of("od-dept-9"))));
        runner.inTransaction(() -> applier.apply(SCOPE, batch));
        long factsBefore = jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class);

        IncrementResult second = runner.inTransaction(() -> applier.apply(SCOPE, batch));
        assertThat(second.changed()).isFalse();
        assertThat(second.committedSeqAfter()).isEqualTo(1);
        assertThat(second.appliedSeqAfter()).isEqualTo(1);
        assertThat(second.changeLogRef()).isNull();
        assertThat(second.publishedEventIds()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class)).isEqualTo(factsBefore);
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(1);
    }

    // ------------------------------------------------------------- 表 6-1②

    @Test
    void table61Row2_emptyBatchIsZeroWriteZeroBootstrap() {
        IncrementResult result = runner.inTransaction(() ->
                applier.apply(SCOPE, new IncrementBatch("evt-empty", List.of(), List.of())));
        assertThat(result.changed()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(0);
        assertThat(result.appliedSeqAfter()).isEqualTo(0);
        assertThat(Support.count(dataSource, "directory_publish_metadata")).isEqualTo(0);
        assertThat(Support.count(dataSource, "directory_fact")).isEqualTo(0);

        assertThatThrownBy(() -> applier.apply(SCOPE, new IncrementBatch("evt-x", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directory-increment:apply");
    }

    // ------------------------------------------------------------- 表 6-1③（预合并 → T7 h 收敛）

    @Test
    void table61Row3_premergeThenPublishConvergesPremergedSeq() {
        // 基线：发布 gen0（D1 根部门 + P1）→ committed=applied=1
        publishGeneration(List.of(new DirectoryDepartment("od-d1", null, "D1")),
                List.of(new DirectoryPerson("ou-p1", "原始名", null, List.of("od-d1"))));
        long factsAtBaseline = jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class);

        // 建开放代 gen1（T6a 建代后未发布）→ 增量落入预合并
        runner.inTransaction(() -> store.beginGeneration(SCOPE));
        IncrementResult premerged = runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-2",
                List.of(), List.of(new DirectoryPerson("ou-p1", "改名后", null, List.of("od-d1"))))));

        assertThat(premerged.premerged()).isTrue();
        assertThat(premerged.committedSeqAfter()).isEqualTo(2);
        assertThat(premerged.appliedSeqAfter()).isEqualTo(1); // applied 不动
        assertThat(premerged.premergedSeq()).isEqualTo(2);
        assertThat(premerged.publishedEventIds()).isEmpty(); // 预合并不发事件
        var openGen = jdbc.queryForMap("SELECT premerged_seq FROM directory_generation "
                + "WHERE tenant_id=? AND app_scope=? AND state IN ('BUILDING','VALIDATING','VALIDATED')",
                SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(openGen.get("premerged_seq")).isEqualTo(2L);

        // T7 发布 gen1（同代镜像含 D1+P1；P1 事实已在预合并前移——④′ 抑制 + h 收敛）
        String openGenId = jdbc.queryForObject("SELECT id FROM directory_generation "
                + "WHERE tenant_id=? AND app_scope=? AND state IN ('BUILDING','VALIDATING','VALIDATED')",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
        runner.inTransaction(() -> {
            var handle = new GenerationHandle(SCOPE, openGenId, currentLeaseToken());
            store.writeBatch(handle, new DirectoryBatch(
                    List.of(new DirectoryDepartment("od-d1", null, "D1")),
                    List.of(new DirectoryPerson("ou-p1", "原始名", null, List.of("od-d1")))));
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        });

        var after = jdbc.queryForMap("SELECT committed_seq, applied_seq, active_generation FROM directory_publish_metadata "
                + "WHERE tenant_id=? AND app_scope=?", SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(after.get("committed_seq")).isEqualTo(2L); // 零新事实：H 不再前移
        assertThat(after.get("applied_seq")).isEqualTo(2L);   // applied 收敛至 H
        var gen1 = jdbc.queryForMap("SELECT state, premerged_seq FROM directory_generation WHERE id=?", openGenId);
        assertThat(gen1.get("state")).isEqualTo("PUBLISHED");
        assertThat(gen1.get("premerged_seq")).isEqualTo(2L);  // h 收敛 = H_after
        // 预合并改名在发布后可见（事实 updated_seq=2 ≤ applied=2）
        assertThat(jdbc.queryForObject("SELECT fact->>'name' FROM directory_fact "
                + "WHERE object_type='PERSON' AND external_id='ou-p1'", String.class)).isEqualTo("改名后");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class)).isEqualTo(factsAtBaseline);
    }

    // ------------------------------------------------------------- 表 6-1④（T7 先行 → 增量路径 A）

    @Test
    void table61Row4_publishFirstThenIncrementFollowsPathA() {
        publishGeneration(List.of(new DirectoryDepartment("od-d1", null, "D1")),
                List.of(new DirectoryPerson("ou-p1", "P1", null, List.of("od-d1"))));

        IncrementResult result = runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-2",
                List.of(), List.of(new DirectoryPerson("ou-p1", "P1 改", null, List.of("od-d1"))))));
        assertThat(result.premerged()).isFalse(); // 无开放代 → 路径 A
        assertThat(result.committedSeqAfter()).isEqualTo(2);
        assertThat(result.appliedSeqAfter()).isEqualTo(2);
        assertThat(result.publishedEventIds()).containsExactly(EVENT_PREFIX + "2:PERSON_CHANGED");
    }

    // ------------------------------------------------------------- 墓碑跳过 + 首增富化

    @Test
    void tombstonedFactIsSkippedByIncrementWithoutResurrection() {
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-1",
                List.of(), List.of(new DirectoryPerson("ou-p1", "张三", null, List.of())))));
        jdbc.update("UPDATE directory_fact SET tombstone = true WHERE external_id = 'ou-p1'");

        IncrementResult result = runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-2",
                List.of(), List.of(new DirectoryPerson("ou-p1", "张三改", null, List.of("od-x"))))));
        assertThat(result.changed()).isFalse(); // 唯一对象为墓碑 → 全批零写
        assertThat(jdbc.queryForObject("SELECT fact->>'name' FROM directory_fact WHERE external_id='ou-p1'",
                String.class)).isEqualTo("张三"); // 内容未被增量改动
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(1);
    }

    /**
     * R-3（W5-f f-3 §7.2；表 7-1 墓碑闭环弧补强）：墓碑后旧事件重放零复活——
     * 增量建档（张三）→ 回查确证删除（commitCas ABSENT rev1 → 墓碑 rev2）→
     * 旧事件重投双面：① 收件箱面（同 eventId 重投 persist=false 终态幂等 +
     * claim 零再取）② 增量面双形态（同批重投 changed=false + 新 eventId
     * 复活尝试内容仍被墓碑拦截——判别面：无墓碑时该内容必判变更）→
     * 再回查 ABSENT 幂等（墓碑保持 rev3）→ 有效视图全程排除 ou-p1。
     */
    @Test
    void r3_tombstoneThenOldEventReplayNeverRevives() {
        PgDirectoryRecheckCommitter committer = new PgDirectoryRecheckCommitter(jdbc,
                new PgDirectoryChangeLog(jdbc));
        PgDirectoryEventInbox inbox = new PgDirectoryEventInbox(jdbc);
        PgDirectoryEffectiveView view = new PgDirectoryEffectiveView(jdbc);

        // ① 扫描建档：发布基线代（od-d1 + ou-p1 张三）→ 事实 rev1 FULL_SCAN、
        // 视图在场（有效视图锚定活动代——未发布 scope 零伪造，故须经发布建档）
        publishGeneration(List.of(new DirectoryDepartment("od-d1", null, "总部")),
                List.of(new DirectoryPerson("ou-p1", "张三", null, List.of("od-d1"))));
        assertThat(view.readEffective(SCOPE).persons())
                .extracting(p -> p.openId()).containsExactly("ou-p1");

        // ② 回查确证删除：ABSENT rev1 → 墓碑 rev2 → 视图排除
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.ABSENT, 1L,
                null, null, null)));
        assertThat(factTombstone()).isTrue();
        assertThat(view.readEffective(SCOPE).persons()).isEmpty();

        // ③ 旧事件重投——收件箱面：同 eventId 重投 dedup + 终态后零再取
        assertThat(inbox.persist(EventEnvelope.received("evt-r3", SCOPE,
                "contact.user.deleted_v3", "{}", Instant.now()))).isTrue();
        assertThat(inbox.persist(EventEnvelope.received("evt-r3", SCOPE,
                "contact.user.deleted_v3", "{}", Instant.now()))).isFalse(); // eventId dedup
        assertThat(inbox.claim(10, Duration.ofMinutes(5)))
                .extracting(EventEnvelope::eventId).containsExactly("evt-r3");
        inbox.markProcessed("evt-r3");
        assertThat(inbox.claim(10, Duration.ofMinutes(5))).isEmpty();
        assertThat(inbox.persist(EventEnvelope.received("evt-r3", SCOPE,
                "contact.user.deleted_v3", "{}", Instant.now()))).isFalse(); // 终态幂等
        assertThat(inbox.claim(10, Duration.ofMinutes(5))).isEmpty();

        // ④ 旧事件重放——增量面双形态：同批重投零写 + 新 eventId 复活尝试内容
        //（≠ 事实内容 → 无墓碑时必判变更）仍被墓碑拦截（墓碑 > 内容差异）
        IncrementResult replay = runner.inTransaction(() -> applier.apply(SCOPE,
                new IncrementBatch("evt-r3", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张三", null, List.of())))));
        assertThat(replay.changed()).isFalse();
        IncrementResult reviveAttempt = runner.inTransaction(() -> applier.apply(SCOPE,
                new IncrementBatch("evt-r3-replay", List.of(),
                        List.of(new DirectoryPerson("ou-p1", "张三-复活尝试", null, List.of())))));
        assertThat(reviveAttempt.changed()).isFalse(); // 不复活
        var fact = jdbc.queryForMap("SELECT local_revision, tombstone, fact->>'name' AS name "
                + "FROM directory_fact WHERE external_id='ou-p1'");
        assertThat(fact.get("local_revision")).isEqualTo(2L); // 重放零推进
        assertThat(fact.get("tombstone")).isEqualTo(true);
        assertThat(fact.get("name")).isEqualTo("张三"); // 内容零污染
        assertThat(view.readEffective(SCOPE).persons()).isEmpty(); // 全程排除

        // ⑤ 回查幂等面：ABSENT rev2 → 墓碑保持（再确证删除 rev3）零复活
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.ABSENT, 2L,
                null, null, null)));
        assertThat(jdbc.queryForObject("SELECT local_revision FROM directory_fact "
                + "WHERE external_id='ou-p1'", Long.class)).isEqualTo(3L);
        assertThat(factTombstone()).isTrue();
        assertThat(view.readEffective(SCOPE).persons()).isEmpty();
    }

    private boolean factTombstone() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT tombstone FROM directory_fact WHERE external_id='ou-p1'", Boolean.class));
    }

    @Test
    void firstIncrementAfterScanEnrichesFactWithDepartmentIds() {
        // 扫描形态事实：FULL_SCAN 锚定，无 departmentIds
        publishGeneration(List.of(new DirectoryDepartment("od-d1", null, "D1")),
                List.of(new DirectoryPerson("ou-p1", "P1", null, List.of("od-d1"))));
        assertThat(jdbcTemplateFactHasDeptIds()).isFalse();

        IncrementResult result = runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-2",
                List.of(), List.of(new DirectoryPerson("ou-p1", "P1", null, List.of("od-d1"))))));
        assertThat(result.changed()).isTrue(); // 首增富化：判内容变更（登记行为）
        var row = jdbc.queryForMap("SELECT local_revision, origin, fact ? 'departmentIds' AS has_depts "
                + "FROM directory_fact WHERE external_id='ou-p1'");
        assertThat(row.get("local_revision")).isEqualTo(2L);
        assertThat(row.get("origin")).isEqualTo("INCREMENT");
        assertThat(row.get("has_depts")).isEqualTo(true);

        // 幂等：同批重投 → 零写
        IncrementResult repeat = runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-2",
                List.of(), List.of(new DirectoryPerson("ou-p1", "P1", null, List.of("od-d1"))))));
        assertThat(repeat.changed()).isFalse();
    }

    // ------------------------------------------------------------- 辅助

    /** 完整发布一代（begin → 单批 write → completePaging → validate → publish）。 */
    private void publishGeneration(List<DirectoryDepartment> departments, List<DirectoryPerson> persons) {
        runner.inTransaction(() -> {
            GenerationHandle handle = store.beginGeneration(SCOPE);
            store.writeBatch(handle, new DirectoryBatch(departments, persons));
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        });
    }

    private String currentLeaseToken() {
        return jdbc.queryForObject("SELECT lease_token FROM directory_publish_metadata "
                + "WHERE tenant_id=? AND app_scope=?", String.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private boolean jdbcTemplateFactHasDeptIds() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT fact ? 'departmentIds' FROM directory_fact WHERE external_id='ou-p1'", Boolean.class));
    }
}
