package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RetentionReport;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.spring.directory.DirectoryRetentionTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 目录事实回收管理端点（W5 设计 §8.3 M-26 管理入口先例；W5-f 设计 §3.4，f-1）：
 * {@code POST /api/identity/admin/directory/retention}（路径常量沿
 * {@link IdentityAdminEndpoints} 先例冻结），body = scope 两键 + preview 标志。
 * 四态责任序沿 {@code IdentityAdminController} 模板：
 * <pre>
 * ① 认证在场（ActorResolver.currentActor() → 401 identity_unauthorized）
 * ② 请求协议校验（scope 两键非空白且宽度上界 + preview 标志必填 → 400）
 * ③ 逐操作策略裁决（Trigger 交互入口 RETENTION_RUN 裁决，缺 policy/actor
 *    双缺省拒绝 → 403 identity_admin_denied + 结构化 WARN 无 PII）
 * ④ 执行（executor.preview/execute → RetentionReport 200；异常照常传播
 *    fail-closed 500 不吞）
 * 审计 = IdentityAuditWriter 事务外 best-effort 直写（失败仅 ERROR 不阻断响应），
 * 操作码 "directory-retention-run"（preview 同码，detail 携带 preview 标记）
 * </pre>
 *
 * <p>preview 标志必填（null = 400）：回收是破坏性操作，预览/执行形态须显式
 * 声明，缺省不落执行侧。装配条件见
 * {@code IdentityDirectoryAutoConfiguration}（web ∧ executor/审计/Actor 端口
 * 齐备 ∧ directory.enabled；非 pg 宿主无 executor bean = 端点不装配——F5）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class DirectoryRetentionController {

    /** 审计稳定操作码（sys_audit_log.operation；preview 同码 + detail 标记）。 */
    static final String AUDIT_DIRECTORY_RETENTION = "directory-retention-run";

    /** V3 DDL 宽度上界（tenant_id VARCHAR(32) / app_scope VARCHAR(64)）。 */
    private static final int MAX_TENANT_KEY_LENGTH = 32;
    private static final int MAX_APP_SCOPE_LENGTH = 64;

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRetentionController.class);

    private final DirectoryRetentionTrigger trigger;
    private final IdentityAuditWriter auditWriter;
    private final ActorResolver actorResolver;

    public DirectoryRetentionController(DirectoryRetentionTrigger trigger,
                                        IdentityAuditWriter auditWriter,
                                        ActorResolver actorResolver) {
        this.trigger = trigger;
        this.auditWriter = auditWriter;
        this.actorResolver = actorResolver;
    }

    /** 目录事实回收（POST，已认证 + CSRF；安全链侧由宿主安全配置承担）。 */
    @PostMapping(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
    public ResponseEntity<Object> runRetention(@RequestBody RetentionRequest request) {
        // ① 认证在场
        Actor actor;
        try {
            actor = actorResolver.currentActor();
        } catch (IdentityRequiredException e) {
            LOG.warn("[directory-retention] 回收入口无认证身份：{}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "identity_unauthorized"));
        }
        // ② 请求协议校验（scope 两键 + preview 标志）
        String tenantKey = request.tenantKey() == null ? "" : request.tenantKey().trim();
        String appScope = request.appScope() == null ? "" : request.appScope().trim();
        if (tenantKey.isEmpty() || tenantKey.length() > MAX_TENANT_KEY_LENGTH
                || appScope.isEmpty() || appScope.length() > MAX_APP_SCOPE_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "identity_invalid_scope",
                    "tenantKeyLengthLimit", MAX_TENANT_KEY_LENGTH,
                    "appScopeLengthLimit", MAX_APP_SCOPE_LENGTH));
        }
        if (request.preview() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "identity_invalid_preview",
                    "message", "preview 标志必填：回收是破坏性操作，预览/执行形态须显式声明"));
        }
        // ③ 策略裁决（Trigger 交互入口：RETENTION_RUN + 缺 policy/actor 双缺省拒绝）
        // ④ 执行
        RetentionReport report;
        try {
            report = trigger.triggerInteractively(actor, new DirectoryScope(tenantKey, appScope),
                    request.preview());
        } catch (IdentityAdminDeniedException e) {
            LOG.warn("[directory-retention] 管理操作被拒：operation={} target={} reason={}",
                    e.operation(), e.targetPrincipalId(), e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", "identity_admin_denied"));
        }
        // ⑤ 审计（事务外 best-effort）+ 200
        writeAudit(actor, tenantKey, appScope, request.preview());
        LOG.info("[directory-retention] HTTP 回收完成：scope={}/{} preview={} person={} dept={} relation={} "
                        + "inbox={} changeLog={} skippedByWatermark={}",
                tenantKey, appScope, request.preview(), report.mirrorPersonRows(),
                report.mirrorDepartmentRows(), report.mirrorRelationRows(),
                report.inboxRowsPurged(), report.changeLogRowsPurged(), report.changeLogSkippedByWatermark());
        // 响应体平铺 Map（不外泄核心域对象——DirectoryScope 无 bean 形态，直接
        // 序列化 RetentionReport 会 500；W5-f f-2 R-8 首跑暴露 f-1 潜伏缺陷，
        // 边界映射修复 + f-4 登记）
        return ResponseEntity.ok(Map.of(
                "tenantKey", tenantKey,
                "appScope", appScope,
                "mirrorPersonRows", report.mirrorPersonRows(),
                "mirrorDepartmentRows", report.mirrorDepartmentRows(),
                "mirrorRelationRows", report.mirrorRelationRows(),
                "inboxRowsPurged", report.inboxRowsPurged(),
                "changeLogRowsPurged", report.changeLogRowsPurged(),
                "changeLogSkippedByWatermark", report.changeLogSkippedByWatermark(),
                "preview", report.preview()));
    }

    private void writeAudit(Actor actor, String tenantKey, String appScope, boolean preview) {
        try {
            auditWriter.write(new com.gallop.fastddd.identity.core.domain.IdentityAuditRecord(
                    actor.tenantId(), actor.principalId(), actor.principalType(),
                    AUDIT_DIRECTORY_RETENTION, "scope=" + tenantKey + "/" + appScope
                    + ",preview=" + preview, true, null, Instant.now()));
        } catch (Exception e) {
            LOG.error("[directory-retention] 审计写入失败（best-effort 不阻断响应）：scope={}/{}",
                    tenantKey, appScope, e);
        }
    }

    /** 请求体（scope 两键 + preview 标志；preview 必填——破坏性操作须显式声明形态）。 */
    record RetentionRequest(String tenantKey, String appScope, Boolean preview) {
    }
}
