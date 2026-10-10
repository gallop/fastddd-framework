package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.usecase.DirectoryRecheckManualTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 手动 pending_confirm 对账回查管理端点（W5 设计 §7.5 对账面；W5-f 设计
 * §3.4/§5 M-17，W5-f f-2）：{@code POST /api/identity/admin/directory/recheck}
 * （路径常量沿 {@link IdentityAdminEndpoints} 先例冻结），body = scope 两键。
 * 四态责任序沿 {@code IdentityAdminController}/{@code DirectoryRetentionController}
 * 模板：
 * <pre>
 * ① 认证在场（ActorResolver.currentActor() → 401 identity_unauthorized）
 * ② 请求协议校验（scope 两键非空白且宽度上界 → 400 identity_invalid_scope）
 * ③ 逐操作策略裁决（RECHECK_RUN，无固定目标 null 形态；缺 policy/缺
 *    Actor 双缺省拒绝 → 403 identity_admin_denied + 结构化 WARN 无 PII）
 * ④ 执行（Trigger.runPendingRecheck → ManualRecheckReport 200；异常照常
 *    传播 fail-closed 500 不吞——设计结论面无错误态，重跑幂等收敛）
 * 审计 = IdentityAuditWriter 事务外 best-effort 直写（失败仅 ERROR 不阻断
 * 响应），操作码 "directory-recheck-manual"
 * </pre>
 *
 * <p><b>裁决落点注记（f-4 登记项）</b>：retention 的裁决在
 * {@code DirectoryRetentionTrigger}（starter 组件内交互入口）；本端点裁决
 * 控制器内联——core 用例 {@link DirectoryRecheckManualTrigger} 五端口编排
 * <b>不进策略</b>（§9.3 铁律，与 {@code DirectorySyncUseCase} 同构），故
 * 无法复用 f-1 的「core 用例 + starter 交互壳」二件套形态（那要求裁决与
 * 编排同住 starter 组件；本端点编排住 core 以便 worker 形态复用）。</p>
 *
 * <p>装配条件见 {@code IdentityDirectoryAutoConfiguration}（web ∧ 回查五端口/
 * 审计/Actor 齐备 ∧ directory.enabled；非 pg 宿主缺 {@code DirectoryRecheckQuery}
 * bean = 端点不装配——F5）。策略缺席非条件：缺 {@code IdentityAdminPolicy}
 * bean 时端点在座但逐请求缺省拒绝（403）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@RestController
public class DirectoryRecheckManualController {

    /** 审计稳定操作码（sys_audit_log.operation）。 */
    static final String AUDIT_DIRECTORY_RECHECK = "directory-recheck-manual";

    /** V3 DDL 宽度上界（tenant_id VARCHAR(32) / app_scope VARCHAR(64)）。 */
    private static final int MAX_TENANT_KEY_LENGTH = 32;
    private static final int MAX_APP_SCOPE_LENGTH = 64;

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRecheckManualController.class);

    private final DirectoryRecheckManualTrigger trigger;
    private final IdentityAuditWriter auditWriter;
    private final ActorResolver actorResolver;
    private final ObjectProvider<IdentityAdminPolicy> adminPolicy;

    public DirectoryRecheckManualController(DirectoryRecheckManualTrigger trigger,
                                            IdentityAuditWriter auditWriter,
                                            ActorResolver actorResolver,
                                            ObjectProvider<IdentityAdminPolicy> adminPolicy) {
        this.trigger = trigger;
        this.auditWriter = auditWriter;
        this.actorResolver = actorResolver;
        this.adminPolicy = adminPolicy;
    }

    /** 手动对账回查（POST，已认证 + CSRF；安全链侧由宿主安全配置承担）。 */
    @PostMapping(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
    public ResponseEntity<Object> runRecheck(@RequestBody RecheckRequest request) {
        // ① 认证在场
        Actor actor;
        try {
            actor = actorResolver.currentActor();
        } catch (IdentityRequiredException e) {
            LOG.warn("[directory-recheck-manual] 回查入口无认证身份：{}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "identity_unauthorized"));
        }
        // ② 请求协议校验（scope 两键）
        String tenantKey = request.tenantKey() == null ? "" : request.tenantKey().trim();
        String appScope = request.appScope() == null ? "" : request.appScope().trim();
        if (tenantKey.isEmpty() || tenantKey.length() > MAX_TENANT_KEY_LENGTH
                || appScope.isEmpty() || appScope.length() > MAX_APP_SCOPE_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "identity_invalid_scope",
                    "tenantKeyLengthLimit", MAX_TENANT_KEY_LENGTH,
                    "appScopeLengthLimit", MAX_APP_SCOPE_LENGTH));
        }
        // ③ 策略裁决（RECHECK_RUN 无固定目标；缺 policy/缺 Actor 双缺省拒绝）
        IdentityAdminPolicy policy = adminPolicy.getIfAvailable();
        if (policy == null) {
            LOG.warn("[directory-recheck-manual] 管理操作被拒：operation=RECHECK_RUN "
                    + "target=null reason=目录回查交互式触发需要 IdentityAdminPolicy bean——无策略即拒绝");
            return ResponseEntity.status(403).body(Map.of("error", "identity_admin_denied"));
        }
        try {
            policy.checkPrincipalAdmin(IdentityAdminOperation.RECHECK_RUN, actor, null);
        } catch (IdentityAdminDeniedException e) {
            LOG.warn("[directory-recheck-manual] 管理操作被拒：operation={} target={} reason={}",
                    e.operation(), e.targetPrincipalId(), e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", "identity_admin_denied"));
        }
        // ④ 执行（异常照常传播 fail-closed）
        DirectoryRecheckManualTrigger.ManualRecheckReport report =
                trigger.runPendingRecheck(new DirectoryScope(tenantKey, appScope));
        // ⑤ 审计（事务外 best-effort）+ 200（响应体平铺 Map——scope 键平铺、
        // outcomes 为纯 record 列表可直接序列化；不外泄 DirectoryScope 核心域
        // 对象，retention 端点同款边界映射）
        writeAudit(actor, tenantKey, appScope, report.outcomes().size());
        LOG.info("[directory-recheck-manual] HTTP 对账回查完成：scope={}/{} outcomes={}",
                tenantKey, appScope, report.outcomes().size());
        return ResponseEntity.ok(Map.of(
                "tenantKey", tenantKey,
                "appScope", appScope,
                "outcomes", report.outcomes()));
    }

    private void writeAudit(Actor actor, String tenantKey, String appScope, int outcomeCount) {
        try {
            auditWriter.write(new IdentityAuditRecord(
                    actor.tenantId(), actor.principalId(), actor.principalType(),
                    AUDIT_DIRECTORY_RECHECK, "scope=" + tenantKey + "/" + appScope
                    + ",outcomes=" + outcomeCount, true, null, Instant.now()));
        } catch (Exception e) {
            LOG.error("[directory-recheck-manual] 审计写入失败（best-effort 不阻断响应）：scope={}/{}",
                    tenantKey, appScope, e);
        }
    }

    /** 请求体（scope 两键；无 preview 形态——回查非破坏性操作，重跑幂等收敛）。 */
    record RecheckRequest(String tenantKey, String appScope) {
    }
}
