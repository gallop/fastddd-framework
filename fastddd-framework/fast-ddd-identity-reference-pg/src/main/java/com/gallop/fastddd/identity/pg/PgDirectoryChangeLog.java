package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * 不可变变更日志 PG 参考适配器（W5 设计 §4.1/§9.2，W5-c 交付）。
 *
 * <p><b>事务模型</b>：{@link #append} 须在提交事务内调用（T7/增量/回查事实写入
 * 同事务——首行经 {@link PgTransactionGate} 拒绝无事务调用，MANDATORY）；
 * {@link #hasGenerationActivation} 为普通读、无事务要求。</p>
 *
 * <p><b>不可变性承载</b>：仅 INSERT（无 UPDATE/DELETE 语句）；幂等防线 =
 * V4 部分唯一约束（GENERATION_ACTIVATED 按 (tenant, app, generation_id) 唯一、
 * INCREMENT_APPLIED/RECHECK_COMMITTED 按 (tenant, app, kind, committed_seq) 唯一，
 * B-5 双保险）——重复 append 直接抛唯一冲突（调用方 hasGenerationActivation
 * 预判为正常防重，约束兜底并发竞态）。</p>
 *
 * <p><b>id 契约</b>：{@code entry.id()} 必须为调用方<b>预铸</b> ULID（P2-5 路线 (i)，
 * 先铸后写——事实行 change_ref 在同一事务内直接引用该 id，无二次 UPDATE）；
 * 本适配器仅透传、不代铸。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryChangeLog implements DirectoryChangeLog {

    private static final String INSERT_SQL = """
            INSERT INTO directory_change_log
                (id, tenant_id, app_scope, kind, generation_id, committed_seq, summary, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

    private static final String HAS_GENERATION_ACTIVATION_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM directory_change_log
                WHERE tenant_id = ? AND app_scope = ? AND kind = 'GENERATION_ACTIVATED'
                  AND generation_id = ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PgTransactionGate gate;

    public PgDirectoryChangeLog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
    }

    @Override
    public String append(ChangeLogEntry entry) {
        gate.requireActiveTransaction("directory-change-log:append");
        Objects.requireNonNull(entry, "entry");
        String summary = entry.summary();
        jdbcTemplate.update(INSERT_SQL,
                entry.id(),
                entry.scope().tenantKey(),
                entry.scope().appScope(),
                entry.kind().name(),
                entry.generationId(),
                entry.committedSeq(),
                summary,
                Timestamp.from(Instant.now()));
        return entry.id();
    }

    @Override
    public boolean hasGenerationActivation(DirectoryScope scope, String generationId) {
        Boolean exists = jdbcTemplate.queryForObject(HAS_GENERATION_ACTIVATION_SQL,
                Boolean.class, scope.tenantKey(), scope.appScope(), generationId);
        return Boolean.TRUE.equals(exists);
    }
}
