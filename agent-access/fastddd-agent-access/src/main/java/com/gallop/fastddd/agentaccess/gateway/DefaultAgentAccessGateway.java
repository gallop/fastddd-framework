package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.BusinessActionEnvelope;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.InternalReason;
import com.gallop.fastddd.agentaccess.spi.ActionNotExposedException;
import com.gallop.fastddd.agentaccess.spi.AgentAccessGateway;
import com.gallop.fastddd.agentaccess.spi.AgentActionRequest;
import com.gallop.fastddd.agentaccess.spi.EnvelopeBindingProjector;
import com.gallop.fastddd.agentaccess.spi.AuthorizationDecision;
import com.gallop.fastddd.agentaccess.spi.AuditProjection;
import com.gallop.fastddd.agentaccess.spi.DecisionKind;
import com.gallop.fastddd.agentaccess.spi.DescriptorRegistry;
import com.gallop.fastddd.agentaccess.spi.ExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.GatewayOutcome;
import com.gallop.fastddd.agentaccess.spi.PermitBindingView;
import com.gallop.fastddd.agentaccess.spi.PermitVerificationException;
import com.gallop.fastddd.agentaccess.spi.TargetAdmissionAuthorizationPort;

/**
 * {@link AgentAccessGateway} 默认编排实现：标准执行顺序（Contract §7）骨架
 * 子集步骤 3-6——验证许可（含绑定比对）→ resolve descriptor → 事务外准入
 * 预检。事务内步骤 7-13 归 FDD-AA-13/14 充实（如实标注，不在骨架期伪装）。
 *
 * <p>异常→结果码映射（AA-02：内部异常不外泄为堆栈，统一收敛为可审计拒绝）：</p>
 * <ul>
 *   <li>{@link PermitVerificationException} → 细因自带外码
 *       （{@code PERMIT_INVALID_OR_EXPIRED} / {@code PERMIT_BINDING_MISMATCH}）；</li>
 *   <li>{@link ActionNotExposedException} → {@code ACTION_NOT_EXPOSED}；</li>
 *   <li>准入预检 DENY / INDETERMINATE → {@code TARGET_AUTHORIZATION_DENIED}
 *       （deny-overrides；无法判定不放行）；</li>
 *   <li>{@code TARGET_AUTHENTICATION_FAILED} 属传输/凭据层认证失败，
 *       骨架期无独立触发点（caller 以已验证许可承载），归 FDD-AA-13
 *       Envelope 层。</li>
 * </ul>
 *
 * <p>准入 Port 防御性兜底：{@link TargetAdmissionAuthorizationPort} 契约约定
 * 「无法判定时返回 INDETERMINATE，不得抛错后默认放行」；若实现违约抛出运行时
 * 异常，本网关不吞、不放行、不放大——收敛为 {@code admission.port.error}
 * 细因的 {@code TARGET_AUTHORIZATION_DENIED}（语义等同 INDETERMINATE
 * fail-closed），保持唯一入口无未审计出口。</p>
 *
 * <p>装配约束（B2 如实口径）：本类是 public 实现类，宿主以显式
 * {@code @Configuration} 装配为 {@link AgentAccessGateway} 唯一实现
 * （kit Validator 断言恰一）；无 JPMS 下包可见性只是编译期边界，
 * 「.gateway 实现不被业务包依赖」由 kit ArchUnit 规则（B1）构建期断言。</p>
 */
public final class DefaultAgentAccessGateway implements AgentAccessGateway {

    private static final InternalReason ADMISSION_DENIED =
            new InternalReason("admission.denied", ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED);
    private static final InternalReason ADMISSION_INDETERMINATE =
            new InternalReason("admission.indeterminate", ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED);
    private static final InternalReason ADMISSION_PORT_ERROR =
            new InternalReason("admission.port.error", ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED);

    private final ExecutionPermitVerifier permitVerifier;
    private final DescriptorRegistry descriptorRegistry;
    private final TargetAdmissionAuthorizationPort admissionPort;

    public DefaultAgentAccessGateway(ExecutionPermitVerifier permitVerifier,
                                     DescriptorRegistry descriptorRegistry,
                                     TargetAdmissionAuthorizationPort admissionPort) {
        if (permitVerifier == null) {
            throw new IllegalArgumentException("permitVerifier 必选");
        }
        if (descriptorRegistry == null) {
            throw new IllegalArgumentException("descriptorRegistry 必选");
        }
        if (admissionPort == null) {
            throw new IllegalArgumentException("admissionPort 必选");
        }
        this.permitVerifier = permitVerifier;
        this.descriptorRegistry = descriptorRegistry;
        this.admissionPort = admissionPort;
    }

    /**
     * Envelope 入口（§7 步骤 1-6 完整骨架，FDD-AA-13 编排充实落点）：
     * 步骤 1-2 解析与校验随 {@link BusinessActionEnvelope} record 紧致校验完成；
     * 步骤 3-6 复用 {@link #admitAgentAction(AgentActionRequest)}——许可验证
     * <b>先于任何业务读取</b>（verify → resolve descriptor → 事务外准入预检），
     * 信封治理字段经 {@link EnvelopeBindingProjector} 投影后与许可逐项比对。
     *
     * <p><b>permitRef 路径与未知载体形态骨架期显式失败</b>（fail-closed，AAC-02
     * 「无 Permit 不放行」）：permitRef 是合法 wire 形态（§5.5），但骨架期既无
     * 解引用通道，也无许可本体承载的审计责任链来源（{@code AuditProjection}
     * 白名单字段取自许可本体）——无法验证即无法产出可审计拒绝。同理，
     * {@code permit} 组件的静态类型是 {@code ExecutionPermitCarrier}，骨架期
     * 唯一已知载体形态是许可本体（{@code ExecutionPermit} 直接实现该接口）；
     * 其他 carrier 形态（引用解引用、远程 token）无法验证即拒绝。两者均归
     * FastAgent V04-04 真实 carrier + 契约勘误冻结后落地；在此之前以
     * {@code IllegalArgumentException} 显式失败，优于不可审计的静默拒绝。</p>
     */
    public GatewayOutcome admitAgentAction(BusinessActionEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope 必选");
        }
        if (envelope.permit() == null) {
            throw new IllegalArgumentException(
                    "permitRef 路径骨架期不支持：无许可载体即无法验证（AAC-02 fail-closed），"
                            + "解引用通道与审计责任链承载归 V04-04 真实 carrier（显式失败优于不可审计拒绝）");
        }
        if (!(envelope.permit() instanceof ExecutionPermit permit)) {
            throw new IllegalArgumentException(
                    "未知 permit 载体形态骨架期不支持：仅接受许可本体承载（ExecutionPermit），"
                            + "其他 carrier 归 V04-04 真实 carrier 冻结后接入（显式失败优于不可审计拒绝）");
        }
        return admitAgentAction(new AgentActionRequest(
                permit, EnvelopeBindingProjector.project(envelope)));
    }

    @Override
    public GatewayOutcome admitAgentAction(AgentActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 必选");
        }
        ExecutionPermit permit = request.permit();
        PermitBindingView binding = request.binding();

        // 步骤 3-4：验证许可（时间窗/信任/绑定比对）；拒绝细因自带外码
        try {
            permitVerifier.verify(permit, binding);
        } catch (PermitVerificationException e) {
            return new GatewayOutcome.Rejected(e.internalReason().outcome(), e.internalReason(),
                    auditOf(permit, e.internalReason().outcome()));
        }

        // 步骤 5：resolve descriptor（未注册/disabled/版本漂移统一 ACTION_NOT_EXPOSED）
        BusinessActionDescriptor descriptor;
        try {
            descriptor = descriptorRegistry.resolve(permit.actionId(), permit.actionVersion());
        } catch (ActionNotExposedException e) {
            return new GatewayOutcome.Rejected(e.outcomeCode(), e.internalReason(),
                    auditOf(permit, e.outcomeCode()));
        }

        // 步骤 6：事务外准入预检（deny-overrides：DENY 与 INDETERMINATE 均不放行）
        AuthorizationDecision admission;
        try {
            admission = admissionPort.authorizeAdmission(new TargetAdmissionAuthorizationPort.AdmissionQuery(
                    permit.caller(), permit.actionId(), permit.actionVersion(), permit.effect()));
        } catch (RuntimeException e) {
            // Port 契约为「无法判定返回 INDETERMINATE」；实现违约抛错时收敛为
            // 等价 fail-closed 拒绝（不吞、不放行、保持出口可审计）
            return new GatewayOutcome.Rejected(ADMISSION_PORT_ERROR.outcome(), ADMISSION_PORT_ERROR,
                    auditOf(permit, ADMISSION_PORT_ERROR.outcome()));
        }
        if (admission == null || admission.kind() != DecisionKind.PERMIT) {
            InternalReason reason = admission == null || admission.kind() == DecisionKind.INDETERMINATE
                    ? ADMISSION_INDETERMINATE
                    : ADMISSION_DENIED;
            return new GatewayOutcome.Rejected(reason.outcome(), reason, auditOf(permit, reason.outcome()));
        }

        // 准入通过：不是执行终态（事务内 7-13 归 FDD-AA-13/14）
        return new GatewayOutcome.Admitted(descriptor, auditOf(permit, null));
    }

    /** §10 白名单审计投影：字段取自许可本体（非请求自报绑定投影），零 payload，零摘要。 */
    private static AuditProjection auditOf(ExecutionPermit permit, ActionOutcomeCode outcomeCode) {
        return new AuditProjection(
                permit.caller().tenantId(),
                permit.businessOperationId(),
                permit.actionId(),
                permit.actionVersion(),
                permit.caller().subjectId(),
                permit.caller().actorId(),
                permit.caller().workloadId(),
                permit.permitId(),
                null,
                outcomeCode);
    }
}
