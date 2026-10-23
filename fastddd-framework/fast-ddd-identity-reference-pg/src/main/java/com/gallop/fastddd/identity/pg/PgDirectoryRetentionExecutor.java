package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.domain.RetentionReport;
import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.DirectoryRetentionExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 目录事实回收执行器 PG 实现（W5 设计 §8.3 M-26；W5-f 设计 §3.3，W5-f f-1）：
 * 单类聚合四面 SQL（preview = 同谓词 SELECT count），JdbcTemplate 直构（沿
 * {@link PgExternalRejectionGate} 先例）。
 *
 * <p><b>面①谓词形态（C-1 钉死）</b>：三条镜像 DELETE 的「非 active」判定由语句
 * 自身在同一语句快照内联联读 {@code directory_publish_metadata.active_generation}
 * （JOIN + {@code IS DISTINCT FROM}），<b>无 active 绑定参数</b>——语句外预读
 * active 后参数注入存在 TOCTOU 窗口（预读后、DELETE 快照前并发发布切指针 →
 * 活动代镜像被整删），明文禁止。active 为 NULL（从未发布 scope）时
 * {@code IS DISTINCT FROM NULL} = true → ABANDONED 代镜像可清（O-6：不采
 * {@code <>} 恒 NULL 零删除路线）。state 谓词天然排除构建代。不需要对
 * metadata 行加锁——安全由谓词承载（两序封死论证见 W5-f 设计 §3.1）。
 * 三条 SQL 常量包可见：R-4 谓词形态守护断言读取面。</p>
 *
 * <p><b>面③水位（P3-1 锚定活动代）</b>：W = min(start_seq) OVER（非终态代 ∪
 * {活动代}）以语句内 {@code NOT EXISTS} 守护子查询承载（非终态代 ∨ 相关子查询
 * 活动代），{@code committed_seq} ≥ 保护代 start_seq 的条目恒保留——活动代激活
 * 条目与其发布前预合并条目不失守；无保护代时子查询恒假 → 保留期是唯一约束
 * （W 无界）。{@code changeLogSkippedByWatermark} = 到龄 ∧ 被水位锚定保留的
 * 条目在场（{@code EXISTS} 同守护子查询计数 &gt; 0）。</p>
 *
 * <p><b>事务形态（O-1：批内原子 + 跨批幂等收敛）</b>：{@code ctid IN (LIMIT :batch)}
 * 批上限循环至零行、批间提交；面①三条同批事务（批内三面原子），面②③各自独立
 * 短事务；{@link #execute} 入口拒绝外层事务（沿 {@code IdentityTransactionRunner}
 * 纪律）。{@link #preview} 沿 {@link PgDirectoryEffectiveView} 先例：调用方事务
 * 在场则加入同快照读，否则自管只读短事务。时钟经 {@link Supplier} 注入（沿
 * {@link PgDirectoryMirrorStore} 先例，装配缺省 {@code Instant::now}）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryRetentionExecutor implements DirectoryRetentionExecutor {

    /** 批上限（每语句每批 DELETE 行数上界；批间提交防长事务——O-1）。 */
    static final int BATCH_LIMIT = 500;

    /**
     * 面①：person 镜像终结非活动代回收（C-1：metadata 联读 + 无 active 绑参；
     * 包可见 = R-4 谓词形态守护断言面）。
     */
    static final String PURGE_PERSON_MIRROR_SQL = """
            DELETE FROM directory_person_mirror
            WHERE ctid IN (
                SELECT pm.ctid
                FROM directory_person_mirror pm
                JOIN directory_generation g ON g.id = pm.generation_id
                JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
                WHERE g.tenant_id = ? AND g.app_scope = ?
                  AND g.state IN ('PUBLISHED', 'ABANDONED')
                  AND g.id IS DISTINCT FROM m.active_generation
                LIMIT ?
            )
            """;

    /** 面①：department 镜像（同 {@link #PURGE_PERSON_MIRROR_SQL} 谓词；包可见 = R-4 守护面）。 */
    static final String PURGE_DEPARTMENT_MIRROR_SQL = """
            DELETE FROM directory_department_mirror
            WHERE ctid IN (
                SELECT dm.ctid
                FROM directory_department_mirror dm
                JOIN directory_generation g ON g.id = dm.generation_id
                JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
                WHERE g.tenant_id = ? AND g.app_scope = ?
                  AND g.state IN ('PUBLISHED', 'ABANDONED')
                  AND g.id IS DISTINCT FROM m.active_generation
                LIMIT ?
            )
            """;

    /** 面①：relation 镜像（同 {@link #PURGE_PERSON_MIRROR_SQL} 谓词；包可见 = R-4 守护面）。 */
    static final String PURGE_RELATION_MIRROR_SQL = """
            DELETE FROM directory_relation_mirror
            WHERE ctid IN (
                SELECT rm.ctid
                FROM directory_relation_mirror rm
                JOIN directory_generation g ON g.id = rm.generation_id
                JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
                WHERE g.tenant_id = ? AND g.app_scope = ?
                  AND g.state IN ('PUBLISHED', 'ABANDONED')
                  AND g.id IS DISTINCT FROM m.active_generation
                LIMIT ?
            )
            """;

    /** 面②：Inbox PROCESSED 到期（RECEIVED/PROCESSING/FAILED 不入谓词永不回收）。 */
    private static final String PURGE_INBOX_SQL = """
            DELETE FROM directory_event_inbox
            WHERE ctid IN (
                SELECT ctid FROM directory_event_inbox
                WHERE tenant_id = ? AND app_scope = ? AND state = 'PROCESSED' AND processed_at < ?
                LIMIT ?
            )
            """;

    /**
     * 面③：变更日志（到龄 ∧ committed_seq 低于水位 W——NOT EXISTS 守护子查询 =
     * 非终态代 ∨ 活动代，start_seq ≤ committed_seq 即锚定保留；无保护代时
     * 子查询恒假 = W 无界，保留期是唯一约束）。
     */
    private static final String PURGE_CHANGE_LOG_SQL = """
            DELETE FROM directory_change_log
            WHERE ctid IN (
                SELECT l.ctid
                FROM directory_change_log l
                WHERE l.tenant_id = ? AND l.app_scope = ? AND l.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM directory_generation g
                      WHERE g.tenant_id = l.tenant_id AND g.app_scope = l.app_scope
                        AND g.start_seq <= l.committed_seq
                        AND (g.state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
                             OR g.id = (SELECT m.active_generation FROM directory_publish_metadata m
                                        WHERE m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope)))
                LIMIT ?
            )
            """;

    /** 面① preview 计数（同 {@link #PURGE_PERSON_MIRROR_SQL} 谓词去 DELETE）。 */
    private static final String COUNT_PERSON_MIRROR_SQL = """
            SELECT count(*)
            FROM directory_person_mirror pm
            JOIN directory_generation g ON g.id = pm.generation_id
            JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
            WHERE g.tenant_id = ? AND g.app_scope = ?
              AND g.state IN ('PUBLISHED', 'ABANDONED')
              AND g.id IS DISTINCT FROM m.active_generation
            """;

    /** 面① preview 计数（department）。 */
    private static final String COUNT_DEPARTMENT_MIRROR_SQL = """
            SELECT count(*)
            FROM directory_department_mirror dm
            JOIN directory_generation g ON g.id = dm.generation_id
            JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
            WHERE g.tenant_id = ? AND g.app_scope = ?
              AND g.state IN ('PUBLISHED', 'ABANDONED')
              AND g.id IS DISTINCT FROM m.active_generation
            """;

    /** 面① preview 计数（relation）。 */
    private static final String COUNT_RELATION_MIRROR_SQL = """
            SELECT count(*)
            FROM directory_relation_mirror rm
            JOIN directory_generation g ON g.id = rm.generation_id
            JOIN directory_publish_metadata m ON m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope
            WHERE g.tenant_id = ? AND g.app_scope = ?
              AND g.state IN ('PUBLISHED', 'ABANDONED')
              AND g.id IS DISTINCT FROM m.active_generation
            """;

    /** 面② preview 计数。 */
    private static final String COUNT_INBOX_SQL = """
            SELECT count(*) FROM directory_event_inbox
            WHERE tenant_id = ? AND app_scope = ? AND state = 'PROCESSED' AND processed_at < ?
            """;

    /** 面③ preview 计数（同 {@link #PURGE_CHANGE_LOG_SQL} 判据）。 */
    private static final String COUNT_CHANGE_LOG_SQL = """
            SELECT count(*) FROM directory_change_log l
            WHERE l.tenant_id = ? AND l.app_scope = ? AND l.created_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM directory_generation g
                  WHERE g.tenant_id = l.tenant_id AND g.app_scope = l.app_scope
                    AND g.start_seq <= l.committed_seq
                    AND (g.state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
                         OR g.id = (SELECT m.active_generation FROM directory_publish_metadata m
                                    WHERE m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope)))
            """;

    /** 面③ 水位锚定保留登记计数（到龄 ∧ EXISTS 同守护子查询——report 标志来源）。 */
    private static final String COUNT_CHANGE_LOG_PROTECTED_SQL = """
            SELECT count(*) FROM directory_change_log l
            WHERE l.tenant_id = ? AND l.app_scope = ? AND l.created_at < ?
              AND EXISTS (
                  SELECT 1 FROM directory_generation g
                  WHERE g.tenant_id = l.tenant_id AND g.app_scope = l.app_scope
                    AND g.start_seq <= l.committed_seq
                    AND (g.state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
                         OR g.id = (SELECT m.active_generation FROM directory_publish_metadata m
                                    WHERE m.tenant_id = g.tenant_id AND m.app_scope = g.app_scope)))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Supplier<Instant> clock;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public PgDirectoryRetentionExecutor(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Instant::now);
    }

    public PgDirectoryRetentionExecutor(JdbcTemplate jdbcTemplate, Supplier<Instant> clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        DataSourceTransactionManager manager =
                new DataSourceTransactionManager(jdbcTemplate.getDataSource());
        this.writeTx = new TransactionTemplate(manager);
        this.writeTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readTx = new TransactionTemplate(manager);
        this.readTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readTx.setReadOnly(true);
    }

    @Override
    public RetentionReport execute(DirectoryScope scope, RetentionPolicy policy) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IdentityTransactionActiveException(
                    "directory-retention:execute 分面批间提交形态（O-1），入口拒绝加入外层事务");
        }
        // 面①：三镜像同批事务（批内三面原子）+ 批间提交幂等收敛
        long personRows = 0;
        long departmentRows = 0;
        long relationRows = 0;
        while (true) {
            long[] batch = writeTx.execute(status -> new long[]{
                    jdbcTemplate.update(PURGE_PERSON_MIRROR_SQL,
                            scope.tenantKey(), scope.appScope(), BATCH_LIMIT),
                    jdbcTemplate.update(PURGE_DEPARTMENT_MIRROR_SQL,
                            scope.tenantKey(), scope.appScope(), BATCH_LIMIT),
                    jdbcTemplate.update(PURGE_RELATION_MIRROR_SQL,
                            scope.tenantKey(), scope.appScope(), BATCH_LIMIT)});
            personRows += batch[0];
            departmentRows += batch[1];
            relationRows += batch[2];
            if (batch[0] == 0 && batch[1] == 0 && batch[2] == 0) {
                break;
            }
        }
        // 面②：Inbox PROCESSED 到期（缺省 7d；在途/FAILED 永不回收）
        Timestamp inboxCutoff = Timestamp.from(clock.get().minus(policy.inboxProcessedTtl()));
        long inboxRows = purgeCutoffLoop(PURGE_INBOX_SQL, scope, inboxCutoff);
        // 面③：变更日志（null = 不限期 = 零清理；水位守护全在语句内）
        long changeLogRows = 0;
        boolean skippedByWatermark = false;
        if (!policy.isChangeLogRetentionUnlimited()) {
            Timestamp cutoff = Timestamp.from(clock.get().minus(policy.changeLogRetention()));
            skippedByWatermark = countProtectedOldEntries(scope, cutoff) > 0;
            changeLogRows = purgeCutoffLoop(PURGE_CHANGE_LOG_SQL, scope, cutoff);
        }
        return new RetentionReport(scope, personRows, departmentRows, relationRows,
                inboxRows, changeLogRows, skippedByWatermark, false);
    }

    @Override
    public RetentionReport preview(DirectoryScope scope, RetentionPolicy policy) {
        // 沿 PgDirectoryEffectiveView 先例：调用方事务在场加入同快照读，否则自管只读短事务
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return doPreview(scope, policy);
        }
        return readTx.execute(status -> doPreview(scope, policy));
    }

    private RetentionReport doPreview(DirectoryScope scope, RetentionPolicy policy) {
        long personRows = count(COUNT_PERSON_MIRROR_SQL, scope);
        long departmentRows = count(COUNT_DEPARTMENT_MIRROR_SQL, scope);
        long relationRows = count(COUNT_RELATION_MIRROR_SQL, scope);
        Timestamp inboxCutoff = Timestamp.from(clock.get().minus(policy.inboxProcessedTtl()));
        Long inboxRows = jdbcTemplate.queryForObject(COUNT_INBOX_SQL, Long.class,
                scope.tenantKey(), scope.appScope(), inboxCutoff);
        long changeLogRows = 0;
        boolean skippedByWatermark = false;
        if (!policy.isChangeLogRetentionUnlimited()) {
            Timestamp cutoff = Timestamp.from(clock.get().minus(policy.changeLogRetention()));
            Long deletable = jdbcTemplate.queryForObject(COUNT_CHANGE_LOG_SQL, Long.class,
                    scope.tenantKey(), scope.appScope(), cutoff);
            changeLogRows = deletable == null ? 0 : deletable;
            skippedByWatermark = countProtectedOldEntries(scope, cutoff) > 0;
        }
        return new RetentionReport(scope, personRows, departmentRows, relationRows,
                inboxRows == null ? 0 : inboxRows, changeLogRows, skippedByWatermark, true);
    }

    /** 面②③批式回收：循环至零行，批间提交（O-1）。 */
    private long purgeCutoffLoop(String sql, DirectoryScope scope, Timestamp cutoff) {
        long total = 0;
        while (true) {
            Integer purged = writeTx.execute(status -> jdbcTemplate.update(sql,
                    scope.tenantKey(), scope.appScope(), cutoff, BATCH_LIMIT));
            total += purged == null ? 0 : purged;
            if (purged == null || purged == 0) {
                return total;
            }
        }
    }

    private long count(String sql, DirectoryScope scope) {
        Long rows = jdbcTemplate.queryForObject(sql, Long.class, scope.tenantKey(), scope.appScope());
        return rows == null ? 0 : rows;
    }

    /** 面③到龄 ∧ 水位锚定保留条目计数（{@code changeLogSkippedByWatermark} 来源）。 */
    private long countProtectedOldEntries(DirectoryScope scope, Timestamp cutoff) {
        Long rows = jdbcTemplate.queryForObject(COUNT_CHANGE_LOG_PROTECTED_SQL, Long.class,
                scope.tenantKey(), scope.appScope(), cutoff);
        return rows == null ? 0 : rows;
    }
}
