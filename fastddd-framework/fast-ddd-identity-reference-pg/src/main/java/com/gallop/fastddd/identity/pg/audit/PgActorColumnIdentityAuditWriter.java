package com.gallop.fastddd.identity.pg.audit;

import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;

/**
 * 审计 writer②（{@code fastddd.identity.audit.actor-columns=true}）：
 * 旧列 + actor 列（actor_principal_id/actor_type，VARCHAR(64) 与主体协议同源）。
 *
 * <p>启动联动检查：构造时核对 sys_audit_log 实际存在 actor 列，缺失即启动失败
 * （提示执行 optional/V1__sys_audit_log_actor_columns.sql），不以静默降级替代。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgActorColumnIdentityAuditWriter implements IdentityAuditWriter {

    static final String INSERT_SQL = """
            INSERT INTO sys_audit_log (username, operation, operation_type, method, request_params,
                                       success, error_message, create_time, actor_principal_id, actor_type)
            VALUES (?, ?, 'IDENTITY', 'identity', ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgActorColumnIdentityAuditWriter(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        verifyActorColumns(dataSource);
    }

    private void verifyActorColumns(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            boolean actorPrincipalId = false;
            boolean actorType = false;
            try (ResultSet columns = connection.getMetaData().getColumns(
                    null, null, "sys_audit_log", null)) {
                while (columns.next()) {
                    String column = columns.getString("COLUMN_NAME");
                    actorPrincipalId |= "actor_principal_id".equals(column);
                    actorType |= "actor_type".equals(column);
                }
            }
            if (!actorPrincipalId || !actorType) {
                throw new IllegalStateException("sys_audit_log 缺少 actor 列（actor_principal_id="
                        + actorPrincipalId + ", actor_type=" + actorType + "）；请先执行参考迁移 "
                        + "db/identity/optional/V1__sys_audit_log_actor_columns.sql，或将 "
                        + "fastddd.identity.audit.actor-columns 设为 false");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("无法核对 sys_audit_log 列结构（actor-columns 启动检查）", e);
        }
    }

    @Override
    public void write(IdentityAuditRecord record) {
        jdbcTemplate.update(INSERT_SQL,
                record.actorPrincipalId().value(),
                record.operation(),
                record.detail(),
                record.success(),
                record.errorMessage(),
                Timestamp.from(record.occurredAt()),
                record.actorPrincipalId().value(),
                hostActorType(record.actorType()));
    }

    /**
     * 宿主审计词表映射（§5.6）：HUMAN→USER、SYSTEM_JOB→SERVICE_ACCOUNT、SYSTEM→SYSTEM；
     * 共享同一 PrincipalId，不另建主体库。
     */
    static String hostActorType(com.gallop.fastddd.identity.core.domain.PrincipalType type) {
        return switch (type) {
            case HUMAN -> "USER";
            case SYSTEM_JOB -> "SERVICE_ACCOUNT";
            case SYSTEM -> "SYSTEM";
        };
    }
}
