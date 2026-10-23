package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.error.InvalidPrincipalIdException;
import com.gallop.fastddd.identity.core.error.PrincipalNotFoundException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * identity 本地生命周期管理控制器（W3 设计 v3 §9.2–9.3，W3-c）。
 *
 * <p><b>四态责任序（§9.3 调用者结果表）</b>：</p>
 * <ol>
 *   <li>控制器级<b>认证在场检查</b>（先于策略，独立于链——默认链下匿名请求已被
 *       C 链拦截，宿主链下本层是链无关防线）：{@link ActorResolver} 抛
 *       {@code IdentityRequiredException} → 401 {@code identity_unauthorized}，
 *       零状态变化；</li>
 *   <li>目标 principalId 协议校验（{@link PrincipalId#of}：空白/超长/控制字符）→
 *       400 {@code identity_invalid_principal_id}（设计未枚举 malformed 输入态，
 *       以客户端错误承接而非放任 500——W3-c 执行记录登记）；</li>
 *   <li><b>逐操作</b>策略裁决（每次操作执行前，非登录时快照）：
 *       {@link IdentityAdminPolicy} 拒绝 → 403 {@code identity_admin_denied} +
 *       结构化安全 WARN（字段白名单：operation/目标主体/操作者 principalId/
 *       tenantId，无姓名等 PII），principal/epoch/会话零变化；</li>
 *   <li>执行（{@link PrincipalLifecycleUseCase}，T4 既有语义）：目标不存在 →
 *       404 {@code identity_principal_not_found}；成功 → 200 + 审计行 + 结构化 INFO。</li>
 * </ol>
 *
 * <p><b>异常传播语义（fail-closed）</b>：策略/Actor 解析器抛出的非协议异常（如
 * 裁决依赖的存储不可达）不在本层捕获——照常传播并由 HTTP 层以 500 呈现。框架不吞
 * 裁决基础设施失败，也不将其伪装成 401/403/404 任何协议态（零状态变化由异常中止
 * 保证；W3-c 复评观察 2 登记）。</p>
 *
 * <p><b>操作人审计（§9.3 成功必写）</b>：经 {@link IdentityAuditWriter} 直写语义化
 * 审计行——与 W2-1 桥（{@code IdentityAuditLogService}）同一终端 writer 与列映射
 * （username=操作者 principalId、operation=稳定操作码、request_params=目标主体），
 * 管理端点是框架代码而非宿主业务 {@code @AuditLog} 方法，走切面只会得到 HTTP 形态
 * 记录（method+请求参数），故直写操作语义记录（W3-c 执行记录登记的解释性落地）。
 * 事务外 best-effort：写入失败仅 ERROR 不阻断成功响应（对齐桥的容错语义）；
 * 拒绝/未找到路径只落结构化安全日志，不写审计行（§9.3）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class IdentityAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityAdminController.class);

    /** 审计稳定操作码（sys_audit_log.operation；与错误体 operation 字段同词表）。 */
    static final String AUDIT_DEACTIVATE = "identity-admin-deactivate";
    static final String AUDIT_REACTIVATE = "identity-admin-reactivate";

    private final PrincipalLifecycleUseCase lifecycleUseCase;
    private final IdentityAdminPolicy adminPolicy;
    private final IdentityAuditWriter auditWriter;
    private final ActorResolver actorResolver;

    public IdentityAdminController(PrincipalLifecycleUseCase lifecycleUseCase,
                                   IdentityAdminPolicy adminPolicy,
                                   IdentityAuditWriter auditWriter,
                                   ActorResolver actorResolver) {
        this.lifecycleUseCase = lifecycleUseCase;
        this.adminPolicy = adminPolicy;
        this.auditWriter = auditWriter;
        this.actorResolver = actorResolver;
    }

    /** 本地受控停用（POST，已认证 + CSRF）。 */
    @PostMapping(IdentityAdminEndpoints.DEACTIVATE_PATH)
    ResponseEntity<Map<String, String>> deactivate(@PathVariable("principalId") String principalId) {
        return execute(IdentityAdminOperation.DEACTIVATE, AUDIT_DEACTIVATE, principalId,
                lifecycleUseCase::deactivate);
    }

    /** 本地受控启用（POST，已认证 + CSRF；与停用独立裁决）。 */
    @PostMapping(IdentityAdminEndpoints.REACTIVATE_PATH)
    ResponseEntity<Map<String, String>> reactivate(@PathVariable("principalId") String principalId) {
        return execute(IdentityAdminOperation.REACTIVATE, AUDIT_REACTIVATE, principalId,
                lifecycleUseCase::reactivate);
    }

    private ResponseEntity<Map<String, String>> execute(IdentityAdminOperation operation,
                                                        String operationCode,
                                                        String principalId,
                                                        Function<String, VerifiedPrincipal> action) {
        // ① 认证在场检查（先于策略，独立于链）
        Actor actor;
        try {
            actor = actorResolver.currentActor();
        } catch (IdentityRequiredException e) {
            LOG.warn("[admin] 管理端点拒绝匿名调用（认证在场检查失败）：operation={}, detail={}",
                    operationCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "identity_unauthorized",
                    "message", "管理操作要求已认证身份"));
        }
        // ② 目标 principalId 协议校验（先于策略：策略裁决以 PrincipalId 为词表）
        PrincipalId target;
        try {
            target = PrincipalId.of(principalId);
        } catch (InvalidPrincipalIdException e) {
            LOG.warn("[admin] 管理端点拒绝非法目标主体 id：operation={}, actorPrincipalId={}, detail={}",
                    operationCode, actor.principalId().value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "identity_invalid_principal_id",
                    "message", e.getMessage()));
        }
        // ③ 逐操作策略裁决（拒绝即中止，零状态变化）
        try {
            adminPolicy.checkPrincipalAdmin(operation, actor, target);
        } catch (IdentityAdminDeniedException e) {
            LOG.warn("[admin] 管理操作被策略拒绝：operation={}, targetPrincipalId={}, "
                            + "actorPrincipalId={}, tenantId={}, reason={}",
                    e.operation(), e.targetPrincipalId(), actor.principalId().value(),
                    actor.tenantId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "identity_admin_denied",
                    "operation", e.operation(),
                    "message", "管理操作被授权策略拒绝"));
        }
        // ④ 执行（T4 既有语义；意外异常照常传播 = 500，框架不吞执行失败）
        VerifiedPrincipal principal;
        try {
            principal = action.apply(principalId);
        } catch (PrincipalNotFoundException e) {
            LOG.info("[admin] 管理操作目标主体不存在：operation={}, targetPrincipalId={}, actorPrincipalId={}",
                    operationCode, principalId, actor.principalId().value());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "identity_principal_not_found",
                    "principalId", principalId,
                    "message", "主体不存在"));
        }
        // ⑤ 审计行（成功必写，事务外 best-effort）+ 结构化日志
        writeAudit(actor, operationCode, principalId);
        LOG.info("[admin] 管理操作成功：operation={}, targetPrincipalId={}, actorPrincipalId={}, "
                        + "tenantId={}, status={}, authEpoch={}",
                operationCode, principalId, actor.principalId().value(), actor.tenantId(),
                principal.status().name(), principal.authEpoch());
        return ResponseEntity.ok(Map.of(
                "principalId", principal.principalId().value(),
                "status", principal.status().name(),
                "authEpoch", String.valueOf(principal.authEpoch())));
    }

    private void writeAudit(Actor actor, String operationCode, String targetPrincipalId) {
        try {
            auditWriter.write(new IdentityAuditRecord(
                    actor.tenantId(),
                    actor.principalId(),
                    actor.principalType(),
                    operationCode,
                    "targetPrincipalId=" + targetPrincipalId,
                    true,
                    null,
                    Instant.now()));
        } catch (Exception e) {
            LOG.error("[admin] 管理操作审计写入失败（尽力而为，不影响响应）：operation={}, target={}",
                    operationCode, targetPrincipalId, e);
        }
    }
}
