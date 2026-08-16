package com.gallop.fastddd.agentaccess.contract;

/**
 * 许可效果类型（Contract §5.2 effect 枚举语义，AA-02 冻结）。
 *
 * <ul>
 *   <li>{@link #WRITE}：同步业务提交——业务事务内完成聚合修改、持久去重与业务
 *       Outbox，Receipt 表达本次提交结果；</li>
 *   <li>{@link #TASK}：异步任务受理——业务效果是「受理一个异步业务任务」这一
 *       同步事实；受理记录按与 WRITE 相同的幂等与重放键规则
 *       （{@code (tenantId, businessOperationId)} 持久唯一、同 {@code effectDigest}
 *       幂等）；Receipt 表达受理结果，异步完成态经 Status/对账收敛；
 *       TASK 不引入第三种重放规则，Phase 1A 不要求实现 TASK 样板；</li>
 *   <li>{@link #READ}：受约束读取——usageLimit、时间窗、dataBudget、速率
 *       四约束按 Descriptor/Permit 执行。</li>
 * </ul>
 */
public enum Effect {

    WRITE,

    TASK,

    READ
}
