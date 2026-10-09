package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.Ulids;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-c 变更日志 PG 验收测试（W5 设计 §4.1/§9.2；不可变只 INSERT + V4 部分唯一
 * 约束双保险——B-5）。
 */
class PgDirectoryChangeLogTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-chglog", "app");

    private DataSource dataSource;
    private PgDirectoryChangeLog changeLog;
    private IdentityTransactionRunner runner;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        changeLog = new PgDirectoryChangeLog(new JdbcTemplate(dataSource));
        runner = Support.runner(dataSource);
    }

    @Test
    void appendReturnsPreCastUlidAndRequiresTransaction() {
        String ulid = Ulids.newUlid();
        String returned = runner.inTransaction(() -> changeLog.append(new ChangeLogEntry(ulid, SCOPE,
                ChangeLogEntry.Kind.INCREMENT_APPLIED, null, 7L, "{\"personsChanged\":1}")));
        assertThat(returned).isEqualTo(ulid); // 预铸透传，不代铸

        assertThatThrownBy(() -> changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                ChangeLogEntry.Kind.RECHECK_COMMITTED, null, 8L, "{}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("directory-change-log:append");
    }

    @Test
    void hasGenerationActivationReflectsOnlyThatGenerationRow() {
        assertThat(changeLog.hasGenerationActivation(SCOPE, "gen-1")).isFalse();
        runner.inTransaction(() -> changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                ChangeLogEntry.Kind.GENERATION_ACTIVATED, "gen-1", 3L,
                "{\"factsInserted\":2}")));
        assertThat(changeLog.hasGenerationActivation(SCOPE, "gen-1")).isTrue();
        assertThat(changeLog.hasGenerationActivation(SCOPE, "gen-2")).isFalse();
    }

    @Test
    void generationActivationUniquePerGenerationAcrossDifferentIds() {
        runner.inTransaction(() -> changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                ChangeLogEntry.Kind.GENERATION_ACTIVATED, "gen-1", 3L, "{}")));
        // 同代二次激活（不同条目 id、不同 seq）→ 部分唯一约束拒绝（B-5 兜底）
        assertThatThrownBy(() -> runner.inTransaction(() ->
                changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                        ChangeLogEntry.Kind.GENERATION_ACTIVATED, "gen-1", 4L, "{}"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incrementUniquePerKindAndCommittedSeq() {
        runner.inTransaction(() -> changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                ChangeLogEntry.Kind.INCREMENT_APPLIED, null, 9L, "{}")));
        // 同 (kind, committed_seq) 不同 id → 拒绝
        assertThatThrownBy(() -> runner.inTransaction(() ->
                changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                        ChangeLogEntry.Kind.INCREMENT_APPLIED, null, 9L, "{}"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentKindSameSeqAndDifferentScopeAreAllowed() {
        runner.inTransaction(() -> {
            changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                    ChangeLogEntry.Kind.INCREMENT_APPLIED, null, 9L, "{}"));
            changeLog.append(new ChangeLogEntry(Ulids.newUlid(), SCOPE,
                    ChangeLogEntry.Kind.RECHECK_COMMITTED, null, 9L, "{}")); // 同 seq 异 kind
            changeLog.append(new ChangeLogEntry(Ulids.newUlid(),
                    new DirectoryScope("tenant-chglog", "other"),
                    ChangeLogEntry.Kind.INCREMENT_APPLIED, null, 9L, "{}")); // 异 scope
            return null;
        });
        assertThat(Support.count(dataSource, "directory_change_log")).isEqualTo(3);
    }
}
