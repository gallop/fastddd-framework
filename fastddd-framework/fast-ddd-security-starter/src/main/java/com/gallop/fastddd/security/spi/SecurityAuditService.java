package com.gallop.fastddd.security.spi;

import com.gallop.fastddd.security.audit.AuditLogEntity;

/**
 * 审计服务 SPI：持久化审计日志
 *
 * <p>默认实现写入数据库（JdbcTemplate），可覆盖为写入消息队列等。</p>
 *
 * @author gallop
 * @since 2026-04-19
 */
public interface SecurityAuditService {

    /**
     * 持久化审计日志
     *
     * @param auditLog 审计日志实体
     */
    void persist(AuditLogEntity auditLog);
}
