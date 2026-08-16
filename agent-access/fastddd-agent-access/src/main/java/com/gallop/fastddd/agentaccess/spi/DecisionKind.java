package com.gallop.fastddd.agentaccess.spi;

/**
 * 授权决定类型（deny-overrides；INDETERMINATE 按拒绝处理）。
 *
 * <ul>
 *   <li>{@link #PERMIT}：允许（合成时允许集合求交、义务按类型合成）；</li>
 *   <li>{@link #DENY}：明确拒绝——双端 PEP 任一端 DENY 即最终 DENY；</li>
 *   <li>{@link #INDETERMINATE}：无法判定（PDP 超时/故障/证据不足）——
 *       <b>必须按拒绝处理</b>，不得重试降级为放行。</li>
 * </ul>
 */
public enum DecisionKind {

    PERMIT,

    DENY,

    INDETERMINATE
}
