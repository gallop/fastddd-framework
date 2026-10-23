package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.domain.Ulids;
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
 * W5-c 权威回查提交 PG 验收测试（W5 设计 §7.1 表 7-1；CAS 基线 + 四结论分支 +
 * 预合并同构 P1-2 + 携带约定校验）。
 */
class PgDirectoryRecheckCommitterTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-recheck", "app");
    private static final String EVENT_PREFIX = "dir-change:" + SCOPE + ":";

    private DataSource dataSource;
    private PgDirectoryRecheckCommitter committer;
    private PgDirectoryIncrementApplier applier;
    private PgDirectoryMirrorStore store;
    private IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        runner = Support.runner(dataSource);
        JdbcTemplate template = new JdbcTemplate(dataSource);
        PgDirectoryChangeLog changeLog = new PgDirectoryChangeLog(template);
        committer = new PgDirectoryRecheckCommitter(template, changeLog);
        applier = new PgDirectoryIncrementApplier(template, changeLog);
        store = new PgDirectoryMirrorStore(template, Instant::now, Duration.ofMinutes(10));
        jdbc = template;
    }

    /** 经增量落一条事实（rev=1）作为回查基线。 */
    private long seedPerson(String openId, String name) {
        runner.inTransaction(() -> applier.apply(SCOPE, new IncrementBatch("evt-seed",
                List.of(), List.of(new DirectoryPerson(openId, name, null, List.of("od-d1"))))));
        return 1L;
    }

    @Test
    void contentDiffersReplacesContentAndAdvancesPathA() {
        long baseline = seedPerson("ou-p1", "旧名");
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS, baseline,
                new DirectoryPerson("ou-p1", "权威新名", "En Name", List.of("od-d1", "od-d2")), null, "evt-src-1")));

        assertThat(result.stale()).isFalse();
        assertThat(result.premerged()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(2);
        assertThat(result.appliedSeqAfter()).isEqualTo(2);
        assertThat(result.changeLogRef()).hasSize(26);
        assertThat(result.publishedEventIds()).containsExactly(EVENT_PREFIX + "2:PERSON_CHANGED");

        var row = jdbc.queryForMap("SELECT local_revision, tombstone, origin, change_ref, updated_seq, "
                + "fact->>'name' AS name, fact->>'enName' AS en_name "
                + "FROM directory_fact WHERE external_id='ou-p1'");
        assertThat(row.get("local_revision")).isEqualTo(2L); // CAS 前移 rev+1
        assertThat(row.get("origin")).isEqualTo("RECHECK");
        assertThat(row.get("change_ref")).isEqualTo(result.changeLogRef());
        assertThat(row.get("updated_seq")).isEqualTo(2L);
        assertThat(row.get("name")).isEqualTo("权威新名");
        assertThat(row.get("en_name")).isEqualTo("En Name");
        var log = jdbc.queryForMap("SELECT kind, committed_seq FROM directory_change_log WHERE id=?",
                result.changeLogRef());
        assertThat(log.get("kind")).isEqualTo("RECHECK_COMMITTED");
        assertThat(log.get("committed_seq")).isEqualTo(2L);
    }

    @Test
    void contentDiffersOnAbsentRowInsertsConfirmedExistenceForm() {
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.DEPARTMENT, "od-brand-new", RecheckConclusion.CONTENT_DIFFERS, 0L,
                null, new DirectoryDepartment("od-brand-new", "od-parent", "权威确认存在"), null)));
        assertThat(result.stale()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT local_revision, origin, updated_seq "
                + "FROM directory_fact WHERE external_id='od-brand-new'");
        assertThat(row.get("local_revision")).isEqualTo(1L);
        assertThat(row.get("origin")).isEqualTo("RECHECK");
        assertThat(row.get("updated_seq")).isEqualTo(1L);
    }

    @Test
    void staleBaselineIsZeroWrite() {
        seedPerson("ou-p1", "旧名");
        long factsBefore = jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class);
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS, 99L,
                new DirectoryPerson("ou-p1", "不该写入", null, List.of()), null, null)));
        assertThat(result.stale()).isTrue();
        assertThat(result.committedSeqAfter()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class)).isEqualTo(factsBefore);
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(1); // 仅种子行

        // 无行 + 基线非 0 = 基线失真 → 同样 stale 零写
        RecheckResult phantom = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-none", RecheckConclusion.IDENTICAL, 5L, null, null, null)));
        assertThat(phantom.stale()).isTrue();
    }

    @Test
    void identicalOnlyResetsPendingConfirmWithZeroWatermark() {
        seedPerson("ou-p1", "旧名");
        jdbc.update("UPDATE directory_fact SET pending_confirm = true WHERE external_id='ou-p1'");

        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.IDENTICAL, 1L, null, null, "evt-src")));
        assertThat(result.stale()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(1); // 水位不动
        assertThat(result.changeLogRef()).isNull();
        assertThat(result.publishedEventIds()).isEmpty();

        assertThat(jdbc.queryForObject("SELECT pending_confirm FROM directory_fact WHERE external_id='ou-p1'",
                Boolean.class)).isFalse();
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(1); // 仅种子行，无新日志
        // pending 已 false 再 IDENTICAL → 条件更新零行（幂等）
        runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.IDENTICAL, 1L, null, null, null)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_change_log", Long.class)).isEqualTo(1L);
    }

    @Test
    void absentTombstonesExistingRowAndIsZeroWriteWhenRowMissing() {
        seedPerson("ou-p1", "旧名");
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.ABSENT, 1L, null, null, null)));
        assertThat(result.stale()).isFalse();
        assertThat(result.committedSeqAfter()).isEqualTo(2);
        assertThat(result.publishedEventIds()).containsExactly(EVENT_PREFIX + "2:PERSON_DEACTIVATED");
        var row = jdbc.queryForMap("SELECT tombstone, origin, local_revision, pending_confirm "
                + "FROM directory_fact WHERE external_id='ou-p1'");
        assertThat(row.get("tombstone")).isEqualTo(true);
        assertThat(row.get("origin")).isEqualTo("RECHECK");
        assertThat(row.get("local_revision")).isEqualTo(2L);
        assertThat(row.get("pending_confirm")).isEqualTo(false);

        // 无行 ABSENT → 零写空操作（非 stale）
        long before = jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class);
        RecheckResult noop = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-ghost", RecheckConclusion.ABSENT, 0L, null, null, null)));
        assertThat(noop.stale()).isFalse();
        assertThat(noop.committedSeqAfter()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_fact", Long.class)).isEqualTo(before);
    }

    @Test
    void deactivatedTombstonesSameFormAsAbsent() {
        seedPerson("ou-p1", "旧名");
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.DEACTIVATED, 1L, null, null, null)));
        assertThat(result.publishedEventIds()).containsExactly(EVENT_PREFIX + "2:PERSON_DEACTIVATED");
        assertThat(jdbc.queryForObject("SELECT tombstone FROM directory_fact WHERE external_id='ou-p1'",
                Boolean.class)).isTrue();
        // external_rejection 门禁链不在本适配器（W5-e 集成）：零拒绝行
        assertThat(Support.count(dataSource, "external_rejection")).isEqualTo(0);
    }

    @Test
    void premergeIsomorphismWhenOpenGenerationExists() {
        seedPerson("ou-p1", "旧名");
        runner.inTransaction(() -> store.beginGeneration(SCOPE));
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS, 1L,
                new DirectoryPerson("ou-p1", "预合并名", null, List.of("od-d1")), null, null)));
        assertThat(result.premerged()).isTrue();
        assertThat(result.committedSeqAfter()).isEqualTo(2);
        assertThat(result.appliedSeqAfter()).isEqualTo(1); // applied 不动
        assertThat(result.premergedSeq()).isEqualTo(2);
        assertThat(result.publishedEventIds()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT premerged_seq FROM directory_generation "
                + "WHERE state IN ('BUILDING','VALIDATING','VALIDATED')", Long.class)).isEqualTo(2L);
    }

    @Test
    void carriageContractIsEnforcedBeforeAnyWrite() {
        // CONTENT_DIFFERS 无观察对象 / 双观察对象 / 键不一致 / 非携带结论带对象
        assertThatThrownBy(() -> runner.inTransaction(() ->
                committer.commitCas(SCOPE, new RecheckEvidence(DirectoryObjectType.PERSON, "ou-p1",
                        RecheckConclusion.CONTENT_DIFFERS, 0L, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.inTransaction(() ->
                committer.commitCas(SCOPE, new RecheckEvidence(DirectoryObjectType.DEPARTMENT, "od-1",
                        RecheckConclusion.CONTENT_DIFFERS, 0L,
                        new DirectoryPerson("ou-x", "n", null, List.of()),
                        new DirectoryDepartment("od-1", null, "d"), null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.inTransaction(() ->
                committer.commitCas(SCOPE, new RecheckEvidence(DirectoryObjectType.PERSON, "ou-p1",
                        RecheckConclusion.CONTENT_DIFFERS, 0L,
                        new DirectoryPerson("ou-MISMATCH", "n", null, List.of()), null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.inTransaction(() ->
                committer.commitCas(SCOPE, new RecheckEvidence(DirectoryObjectType.PERSON, "ou-p1",
                        RecheckConclusion.IDENTICAL, 0L,
                        new DirectoryPerson("ou-p1", "n", null, List.of()), null, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Support.count(dataSource, "directory_publish_metadata")).isEqualTo(0); // 校验先于自举写

        // 事务外调用被 MANDATORY 门禁拒绝
        assertThatThrownBy(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.IDENTICAL, 0L, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directory-recheck:commitCas");
    }

    @Test
    void changelogSummaryCarriesNoPii() {
        seedPerson("ou-p1", "旧名");
        RecheckResult result = runner.inTransaction(() -> committer.commitCas(SCOPE, new RecheckEvidence(
                DirectoryObjectType.PERSON, "ou-p1", RecheckConclusion.CONTENT_DIFFERS, 1L,
                new DirectoryPerson("ou-p1", "新名", null, List.of("od-d1")), null, "evt-42")));
        String summary = jdbc.queryForObject("SELECT summary::text FROM directory_change_log WHERE id=?",
                String.class, result.changeLogRef());
        // jsonb 规范化输出（冒号后带空格）——按词元断言，不钉字面形态
        assertThat(summary).contains("CONTENT_DIFFERS")
                .contains("PERSON")
                .contains("evt-42")
                .doesNotContain("ou-p1").doesNotContain("新名");
        assertThat(Ulids.isValid(result.changeLogRef())).isTrue();
    }
}
