package com.gallop.fastddd.identity.spring.bridge;

import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.security.audit.AuditLogEntity;
import com.gallop.fastddd.security.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 身份模式审计落库服务（v0.2 §5.6 / W2-1 修复轮 W21-04）：实现 security-starter
 * 的 {@link AuditLogService}，使默认 {@code AuditLogServiceImpl} 以 @CoM 退让——
 * 业务 {@code @AuditLog} 记录经选定 {@link IdentityAuditWriter} 写入
 * （仅旧列 / 旧列 + actor 列由 {@code fastddd.identity.audit.actor-columns} 与 DDL 决定）。
 *
 * <p>Actor 已由切面在业务执行前捕获到实体字段（含异步路径）；本服务不做上下文重读。
 * 字段映射：username/actor_principal_id=principalId，operation=审计操作名，
 * detail=方法标识+请求参数（脱敏由切面完成）。写入失败仅记录错误（尽力而为 T5，
 * 不影响业务结果，与旧服务容错语义一致）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityAuditLogService implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(IdentityAuditLogService.class);

    private final IdentityAuditWriter writer;

    public IdentityAuditLogService(IdentityAuditWriter writer) {
        this.writer = writer;
    }

    @Override
    public void saveAuditLog(AuditLogEntity auditLog) {
        if (auditLog.getActorPrincipalId() == null) {
            // 身份缺失被拒绝的业务调用（切面未捕获到 Actor）：无归属不落库
            log.warn("identity 审计记录缺少 Actor（operation={}），不落库——身份缺失的业务调用已在切面拒绝",
                    auditLog.getOperation());
            return;
        }
        try {
            writer.write(new IdentityAuditRecord(
                    auditLog.getActorTenantId(),
                    PrincipalId.of(auditLog.getActorPrincipalId()),
                    actorType(auditLog.getActorType()),
                    auditLog.getOperation(),
                    detail(auditLog),
                    Boolean.TRUE.equals(auditLog.getSuccess()),
                    auditLog.getErrorMessage(),
                    auditLog.getCreateTime() == null
                            ? Instant.now()
                            : Timestamp.valueOf(auditLog.getCreateTime()).toInstant()));
        } catch (Exception e) {
            log.error("identity 审计写入失败（尽力而为，不影响业务）：{}", e.getMessage(), e);
        }
    }

    @Override
    @Async("auditLogExecutor")
    public void saveAuditLogAsync(AuditLogEntity auditLog) {
        saveAuditLog(auditLog);
    }

    @Override
    public void persist(AuditLogEntity auditLog) {
        saveAuditLog(auditLog);
    }

    private static PrincipalType actorType(String actorType) {
        // 桥写入的一定是 PrincipalType.name()；外来词表按异常处理（不静默改归属）
        return PrincipalType.valueOf(actorType);
    }

    private static String detail(AuditLogEntity auditLog) {
        String method = auditLog.getMethod();
        String params = auditLog.getRequestParams();
        if (method == null) {
            return params;
        }
        return params == null ? method : method + " " + params;
    }
}
