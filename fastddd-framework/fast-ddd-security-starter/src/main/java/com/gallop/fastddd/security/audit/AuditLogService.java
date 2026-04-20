package com.gallop.fastddd.security.audit;

import com.gallop.fastddd.security.spi.SecurityAuditService;

/**
 * 审计日志服务接口
 *
 * <p>继承 SecurityAuditService SPI，同时提供同步和异步保存能力。
 * 用户只需提供一个 AuditLogService bean 即可覆盖所有审计持久化行为。</p>
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
public interface AuditLogService extends SecurityAuditService {

    /**
     * 保存审计日志
     *
     * @param auditLog 审计日志实体
     */
    void saveAuditLog(AuditLogEntity auditLog);

    /**
     * 异步保存审计日志
     *
     * @param auditLog 审计日志实体
     */
    void saveAuditLogAsync(AuditLogEntity auditLog);
}
