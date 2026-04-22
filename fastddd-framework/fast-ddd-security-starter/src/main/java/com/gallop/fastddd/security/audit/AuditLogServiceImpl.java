package com.gallop.fastddd.security.audit;

import com.gallop.fastddd.common.database.DatabaseDialect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;

import javax.sql.DataSource;

/**
 * 审计日志服务实现类
 *
 * <p>当 JdbcTemplate 可用时，将审计日志持久化到 sys_audit_log 表；
 * 否则 fallback 到 SLF4J 日志输出。</p>
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private volatile JdbcTemplate jdbcTemplate;
    private volatile DataSource dataSource;

    private static final String INSERT_SQL = "INSERT INTO sys_audit_log " +
            "(username, operation, operation_type, method, request_url, request_params, " +
            "result, client_ip, user_agent, execution_time, success, error_message, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, %s)";

    private volatile String resolvedInsertSql;

    private volatile boolean tableInitialized = false;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveAuditLog(AuditLogEntity auditLog) {
        if (jdbcTemplate != null) {
            try {
                ensureTableExists();
                String sql = resolvedInsertSql != null ? resolvedInsertSql
                        : String.format(INSERT_SQL, "NOW()");
                jdbcTemplate.update(sql,
                        auditLog.getUsername(),
                        auditLog.getOperation(),
                        auditLog.getOperationType(),
                        auditLog.getMethod(),
                        auditLog.getRequestUrl(),
                        auditLog.getRequestParams(),
                        auditLog.getResult(),
                        auditLog.getClientIp(),
                        auditLog.getUserAgent(),
                        auditLog.getExecutionTime(),
                        auditLog.getSuccess(),
                        auditLog.getErrorMessage());
            } catch (Exception e) {
                log.warn("审计日志入库失败，fallback到日志输出: {}", e.getMessage());
                logToSlf4j(auditLog);
            }
        } else {
            logToSlf4j(auditLog);
        }
    }

    @Override
    @Async("auditLogExecutor")
    public void saveAuditLogAsync(AuditLogEntity auditLog) {
        saveAuditLog(auditLog);
    }

    private void ensureTableExists() {
        if (!tableInitialized) {
            synchronized (this) {
                if (!tableInitialized) {
                    try {
                        String sql = buildCreateTableSql();
                        jdbcTemplate.execute(sql);
                        log.info("自动创建 sys_audit_log 表成功");
                    } catch (Exception e) {
                        log.debug("sys_audit_log 表已存在或自动创建失败: {}", e.getMessage());
                    }
                    if (dataSource != null) {
                        DatabaseDialect dialect = DatabaseDialect.detect(dataSource);
                        resolvedInsertSql = String.format(INSERT_SQL, dialect.getNowFunction());
                    }
                    tableInitialized = true;
                }
            }
        }
    }

    private String buildCreateTableSql() {
        DatabaseDialect dialect = dataSource != null
                ? DatabaseDialect.detect(dataSource)
                : new com.gallop.fastddd.common.database.MySqlDialect();
        return "CREATE TABLE IF NOT EXISTS sys_audit_log (" +
                dialect.getPkDefinition() + ", " +
                "username VARCHAR(50), " +
                "operation VARCHAR(200), " +
                "operation_type VARCHAR(50), " +
                "method VARCHAR(10), " +
                "request_url VARCHAR(500), " +
                "request_params " + dialect.getClobType() + ", " +
                "result " + dialect.getClobType() + ", " +
                "client_ip VARCHAR(50), " +
                "user_agent VARCHAR(500), " +
                "execution_time BIGINT, " +
                "success " + dialect.getBooleanType() + " DEFAULT 1, " +
                "error_message " + dialect.getClobType() + ", " +
                "create_time " + dialect.getTimestampType() + " NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")" + dialect.getTableOptions();
    }

    private void logToSlf4j(AuditLogEntity auditLog) {
        log.info("Audit Log: User[{}] performed [{}] on [{}], IP: {}, Success: {}",
                auditLog.getUsername(),
                auditLog.getOperation(),
                auditLog.getRequestUrl(),
                auditLog.getClientIp(),
                auditLog.getSuccess());
        if (auditLog.getErrorMessage() != null) {
            log.error("Audit Error: {}", auditLog.getErrorMessage());
        }
    }

    @Override
    public void persist(AuditLogEntity auditLog) {
        saveAuditLog(auditLog);
    }
}
