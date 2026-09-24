package com.gallop.fastddd.identity.pg.audit;

import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * 审计 writer①（默认，{@code fastddd.identity.audit.actor-columns=false}）：
 * 仅写旧列——username = principalId（协议 ≤50 恰好在 VARCHAR(50) 界内，W0-2 §3.3）。
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgLegacyIdentityAuditWriter implements IdentityAuditWriter {

    static final String INSERT_SQL = """
            INSERT INTO sys_audit_log (username, operation, operation_type, method, request_params,
                                       success, error_message, create_time)
            VALUES (?, ?, 'IDENTITY', 'identity', ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgLegacyIdentityAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(IdentityAuditRecord record) {
        jdbcTemplate.update(INSERT_SQL,
                record.actorPrincipalId().value(),
                record.operation(),
                record.detail(),
                record.success(),
                record.errorMessage(),
                Timestamp.from(record.occurredAt()));
    }
}
