package com.gallop.fastddd.security.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;

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

    private JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = "INSERT INTO sys_audit_log " +
            "(username, operation, operation_type, method, request_url, request_params, " +
            "result, client_ip, user_agent, execution_time, success, error_message, create_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS sys_audit_log (" +
            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
            "username VARCHAR(50), " +
            "operation VARCHAR(200), " +
            "operation_type VARCHAR(50), " +
            "method VARCHAR(10), " +
            "request_url VARCHAR(500), " +
            "request_params TEXT, " +
            "result TEXT, " +
            "client_ip VARCHAR(50), " +
            "user_agent VARCHAR(500), " +
            "execution_time BIGINT, " +
            "success TINYINT(1) DEFAULT 1, " +
            "error_message TEXT, " +
            "create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private volatile boolean tableInitialized = false;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAuditLog(AuditLogEntity auditLog) {
        if (jdbcTemplate != null) {
            try {
                ensureTableExists();
                jdbcTemplate.update(INSERT_SQL,
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
                        jdbcTemplate.execute(CREATE_TABLE_SQL);
                        log.info("自动创建 sys_audit_log 表成功");
                    } catch (Exception e) {
                        log.debug("sys_audit_log 表已存在或自动创建失败: {}", e.getMessage());
                    }
                    tableInitialized = true;
                }
            }
        }
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
