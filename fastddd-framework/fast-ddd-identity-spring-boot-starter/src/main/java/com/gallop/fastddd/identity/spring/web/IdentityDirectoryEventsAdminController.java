package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryEventInboxAdmin;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 目录增量事件管理控制器（W5 设计 §5.1 管理面，W5-d）：FAILED 终态事件显式
 * 重放——复位 RECEIVED、attempts 归零、租约/错误清空（存储面原子 UPDATE）。
 *
 * <p><b>四态责任序（同 {@link IdentityAdminController}——§9.3 调用者结果表）</b>：
 * ① 认证在场检查（链无关防线）→ 401；② eventId 协议校验（非空白且 ≤ 128——
 * V4 主键宽度内）→ 400；③ 逐操作策略裁决
 * （{@link IdentityAdminOperation#INBOX_REPLAY}，<b>无固定目标主体</b>——
 * {@code targetPrincipalId=null}，SYNC_TRIGGER 先例：<b>冻结
 * IdentityAdminPolicy 签名零变更</b>，W5-d 差异登记）→ 403；④ 执行：
 * 不存在或非 FAILED 终态（无可重放行）→ 404；成功 → 200 + 审计行 + 结构化
 * INFO。</p>
 *
 * <p><b>审计（成功必写）</b>：与主体管理端点同终端 writer 同列映射
 * （username=操作者 principalId、operation=稳定操作码、request_params=
 * eventId）；事务外 best-effort，失败仅 ERROR 不阻断成功响应。拒绝/未找到
 * 路径只落结构化安全日志（§9.3）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class IdentityDirectoryEventsAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityDirectoryEventsAdminController.class);

    /** 审计稳定操作码（sys_audit_log.operation；与错误体 operation 字段同词表）。 */
    static final String AUDIT_INBOX_REPLAY = "identity-admin-inbox-replay";

    /** eventId 协议上界（V4 event_id 主键宽度内，留余量）。 */
    private static final int MAX_EVENT_ID_LENGTH = 128;

    private final DirectoryEventInboxAdmin inboxAdmin;
    private final IdentityAdminPolicy adminPolicy;
    private final IdentityAuditWriter auditWriter;
    private final ActorResolver actorResolver;

    public IdentityDirectoryEventsAdminController(DirectoryEventInboxAdmin inboxAdmin,
                                                  IdentityAdminPolicy adminPolicy,
                                                  IdentityAuditWriter auditWriter,
                                                  ActorResolver actorResolver) {
        this.inboxAdmin = inboxAdmin;
        this.adminPolicy = adminPolicy;
        this.auditWriter = auditWriter;
        this.actorResolver = actorResolver;
    }

    /** FAILED 终态事件显式重放（POST，已认证 + CSRF）。 */
    @PostMapping(IdentityAdminEndpoints.EVENTS_REPLAY_PATH)
    ResponseEntity<Map<String, String>> replay(@PathVariable("eventId") String eventId) {
        // ① 认证在场检查（先于策略，独立于链）
        Actor actor;
        try {
            actor = actorResolver.currentActor();
        } catch (IdentityRequiredException e) {
            LOG.warn("[admin] 增量事件管理端点拒绝匿名调用：operation={}, detail={}",
                    AUDIT_INBOX_REPLAY, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "identity_unauthorized",
                    "message", "管理操作要求已认证身份"));
        }
        // ② eventId 协议校验（非空白 + 宽度上界；对象键非凭据，可入日志）
        if (eventId == null || eventId.isBlank() || eventId.length() > MAX_EVENT_ID_LENGTH) {
            LOG.warn("[admin] 增量事件管理端点拒绝非法 eventId：operation={}, actorPrincipalId={}",
                    AUDIT_INBOX_REPLAY, actor.principalId().value());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "identity_invalid_event_id",
                    "message", "eventId 必须为非空白且长度 ≤ " + MAX_EVENT_ID_LENGTH));
        }
        // ③ 逐操作策略裁决（无固定目标主体：null——SYNC_TRIGGER 先例，策略签名零变更）
        try {
            adminPolicy.checkPrincipalAdmin(IdentityAdminOperation.INBOX_REPLAY, actor, null);
        } catch (IdentityAdminDeniedException e) {
            LOG.warn("[admin] 增量事件重放被策略拒绝：operation={}, actorPrincipalId={}, tenantId={}, reason={}",
                    e.operation(), actor.principalId().value(), actor.tenantId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "identity_admin_denied",
                    "operation", e.operation(),
                    "message", "管理操作被授权策略拒绝"));
        }
        // ④ 执行：false = 事件不存在或非 FAILED 终态（无可重放行——不区分两者，
        // 均为"无可重放"终局语义）
        boolean replayed = inboxAdmin.replay(eventId);
        if (!replayed) {
            LOG.info("[admin] 事件重放未执行（不存在或非 FAILED 终态）：eventId={}, actorPrincipalId={}",
                    eventId, actor.principalId().value());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "identity_event_not_replayable",
                    "eventId", eventId,
                    "message", "事件不存在或不在 FAILED 终态（无可重放行）"));
        }
        // ⑤ 审计行（成功必写，事务外 best-effort）+ 结构化日志
        writeAudit(actor, eventId);
        LOG.info("[admin] 事件重放成功：eventId={}, actorPrincipalId={}, tenantId={}",
                eventId, actor.principalId().value(), actor.tenantId());
        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "outcome", "requeued"));
    }

    private void writeAudit(Actor actor, String eventId) {
        try {
            auditWriter.write(new IdentityAuditRecord(
                    actor.tenantId(),
                    actor.principalId(),
                    actor.principalType(),
                    AUDIT_INBOX_REPLAY,
                    "eventId=" + eventId,
                    true,
                    null,
                    Instant.now()));
        } catch (Exception e) {
            LOG.error("[admin] 增量事件重放审计写入失败（尽力而为，不影响响应）：eventId={}",
                    eventId, e);
        }
    }
}
