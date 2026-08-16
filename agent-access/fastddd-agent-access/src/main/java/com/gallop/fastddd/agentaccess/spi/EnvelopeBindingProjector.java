package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionEnvelope;

/**
 * {@link BusinessActionEnvelope} → {@link PermitBindingView} 投影适配器
 * （§7 步骤 1/4：解析 Envelope 后，将其治理字段投影为许可绑定视图，
 * 由 {@code ExecutionPermitVerifier} 与许可逐项 fail-closed 比对）。
 *
 * <p>投影映射：Envelope 的同名治理字段（tenant/environment/action/version/
 * effect/purpose/businessOperationId/externalIdempotencyKey/三摘要）直投；
 * {@code requestId}/{@code attemptId}/{@code dispatchId}/{@code traceId} 等
 * 可变调度信息<b>不进入投影</b>（§5.5 规则 2：不进 effectDigest、不参与
 * 许可比对——重试稳定性由稳定字段承载）。</p>
 *
 * <p><b>修订（2026-08-16，FDD-AA-13 收口勘误）</b>：Envelope 增列
 * {@code preconditionsHash} 与 {@code expectedResourceVersions} 可选组件后，
 * 本投影对两项<b>直投</b>（此前恒投 null 的契约缝隙已闭合）。提供状态与
 * Permit 侧的敏感比对语义不变：单方提供即拒（PERMIT_BINDING_MISMATCH，
 * fail-closed）。直携对齐依据：FastAgent V04-04 真实载体直携两项并与
 * Permit 构造期交叉校验——无推导规则需要发明。</p>
 */
public final class EnvelopeBindingProjector {

    private EnvelopeBindingProjector() {
    }

    /** Envelope 治理字段 → 许可绑定投影（13 同名字段直投；提供状态敏感比对归验证器）。 */
    public static PermitBindingView project(BusinessActionEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope 必选");
        }
        return new PermitBindingView(
                envelope.tenantId(),
                envelope.environment(),
                envelope.actionId(),
                envelope.actionVersion(),
                envelope.effect(),
                envelope.purpose(),
                envelope.businessOperationId(),
                envelope.externalIdempotencyKey(),
                envelope.preconditionsHash(),
                envelope.expectedResourceVersions(),
                envelope.effectDigest(),
                envelope.authorizationDigest(),
                envelope.authorizationBindingDigest());
    }
}
