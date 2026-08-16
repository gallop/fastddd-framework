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
 * <p><b>契约缝隙如实标注（不得在代码中自选语义）</b>：Permit 的
 * {@code preconditionsHash} 与 {@code expectedResourceVersions} 在 Envelope
 * §5.5 字段表中无同名 wire 承载（Envelope 携带结构化
 * {@code preconditions} 列表，两者的推导规则未在契约中冻结）。本投影对这两项
 * 投 {@code null}：「Envelope 侧不主张」与 Permit 侧的比对是提供状态敏感的
 * ——Permit 携带任一项而 Envelope 无法对等证明时验证器拒绝
 * （PERMIT_BINDING_MISMATCH，fail-closed）。真实推导规则的冻结归
 * Contract 勘误与 FastAgent V04-04 真实 carrier 对齐，不在骨架期补造。</p>
 */
public final class EnvelopeBindingProjector {

    private EnvelopeBindingProjector() {
    }

    /** Envelope 治理字段 → 许可绑定投影（11 同名字段直投 + 两项按 Descriptor 字段不主张）。 */
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
                null,
                null,
                envelope.effectDigest(),
                envelope.authorizationDigest(),
                envelope.authorizationBindingDigest());
    }
}
