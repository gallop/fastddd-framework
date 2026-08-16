package com.gallop.fastddd.agentaccess.spi;

/**
 * Agent 路径唯一入口公共接缝（FDD-AA-04 §3.2：Agent/LLM 输出不得绕过网关
 * 触达 Command/CRUD/Repository）。
 *
 * <p>骨架期覆盖标准执行顺序（Contract §7）步骤 3-6：</p>
 * <ol start="3">
 *   <li>验证许可（{@link ExecutionPermitVerifier}，含 Envelope 绑定投影逐项比对）；</li>
 *   <li>resolve descriptor（{@link DescriptorRegistry}：未注册/disabled/版本漂移
 *       统一 {@code ACTION_NOT_EXPOSED}）；</li>
 *   <li>事务外准入预检（{@link TargetAdmissionAuthorizationPort}；DENY 与
 *       INDETERMINATE 均不放行）。</li>
 * </ol>
 *
 * <p>事务内步骤 7-13（幂等去重、资源版本终检、聚合执行、Outbox、Receipt）
 * 归 FDD-AA-13/14 充实；骨架期「准入通过」不等于执行授权终态。事务内
 * {@link ResourceAuthorizationPort} 终检关闭预检-执行 TOCTOU 窗口（TM-19）。</p>
 *
 * <p>异常语义：业务拒绝一律以 {@link GatewayOutcome.Rejected} 返回（fail-closed
 * 且可审计）；{@code request} 缺失是调用方编程错误（{@code IllegalArgumentException}）。
 * 审计白名单投影（§10 零 payload）在每个出口产生，拒绝与通过同样可审计。</p>
 */
public interface AgentAccessGateway {

    /**
     * 准入一个 Agent 动作（验证许可 → 绑定比对 → resolve → 事务外预检）。
     *
     * @param request 许可 + Envelope 治理字段投影（骨架期不携带业务 payload）
     * @return 准入判定（通过 = 已解析 descriptor；拒绝 = 外部结果码 + 审计细因）
     * @throws IllegalArgumentException request 缺失（编程错误，非业务拒绝）
     */
    GatewayOutcome admitAgentAction(AgentActionRequest request);
}
