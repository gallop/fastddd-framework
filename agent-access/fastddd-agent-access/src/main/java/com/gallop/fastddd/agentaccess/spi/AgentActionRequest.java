package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;

/**
 * Agent 动作进入 {@link AgentAccessGateway} 的骨架期请求（许可 + 绑定投影）。
 *
 * <p>骨架期入参是许可与 Envelope 治理字段投影，<b>不携带业务 payload</b>：
 * 完整 {@code BusinessActionEnvelope} wire DTO 归 FDD-AA-13 网关编排落地，
 * 届时本类型被 Envelope 适配替代或收窄为 Envelope 的治理投影来源。业务
 * payload 在骨架期不进入网关，审计与结果码映射因此天然零 payload。</p>
 *
 * <p>许可缺失是调用方编程错误（AAC-02：无 Permit 不放行），不是业务拒绝：
 * 本 record 紧致校验直接 fail-fast，不进入网关结果码路径。</p>
 *
 * @param permit  待验证的一次受限执行许可（必选）
 * @param binding Envelope 治理字段投影（必选；缺失即无法证明绑定一致）
 */
public record AgentActionRequest(ExecutionPermit permit, PermitBindingView binding) {

    public AgentActionRequest {
        if (permit == null) {
            throw new IllegalArgumentException("permit 必选（无 Permit 不放行）");
        }
        if (binding == null) {
            throw new IllegalArgumentException("binding 必选（无绑定投影即无法证明一致）");
        }
    }
}
