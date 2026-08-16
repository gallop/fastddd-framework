package com.gallop.fastddd.agentaccess.spi;

/**
 * 宿主持久去重 Provider SPI（Contract §8 幂等与事务契约的目标侧承载）。
 *
 * <p><b>宿主实现契约（TM-14 冻结）</b>：聚合修改、成功去重记录与业务
 * Outbox 必须使用<b>同一 DataSource/TransactionManager</b> 提交（§8.4）；
 * {@code recordCompletion} 在业务事务内执行，与聚合变更原子可见。本端口
 * 不做 XA/JTA——FastAgent LP/dispatch、FastDDD 业务事务、FastAgent 结果
 * 收敛是三个独立事务（§8.5）。内存参考实现
 * {@link InMemoryDedupStore} 无法表达该约束，由 kit conformance fixture
 * （FDD-AA-14）对宿主真实现断言。</p>
 *
 * <p><b>键语义（§8 权威口径）</b>：{@code (tenantId, businessOperationId)}
 * 二元键建立持久唯一约束（§8.1）；{@code effectDigest} 是约束下的一致性
 * 比对字段——同键同摘要返回既有稳定结果（§8.2），同键异摘要
 * {@code IDEMPOTENCY_CONFLICT} 并产生完整性事件（§8.3，事件登记归调用方
 * 审计通道，冲突分支只报告既有摘要）。</p>
 *
 * <p><b>TM-26</b>：本端口签名不携带 FastDDD {@code commandId}——commandId
 * 仅调查关联，不作业务幂等键（见 {@link DedupKey} 结构级表达）。</p>
 */
public interface PersistentDedupPort {

    /**
     * 幂等检查（§7 步骤 8）：按二元键查询并与 {@code effectDigest} 比对。
     * 只读操作，不产生写副作用。
     */
    DedupCheck check(DedupKey key, String effectDigest);

    /**
     * 写入稳定去重结果（§7 步骤 11）：业务事务内执行（TM-14 同事务）。
     *
     * <p>同键同摘要重复写入为幂等 no-op（首次稳定结果保持权威）；
     * 同键异摘要抛 {@link IdempotencyConflictException}（并发竞态下的
     * §8.3 收敛——唯一约束被他人先占）。完整性与成功态约束见
     * {@link DedupCompletion} 紧致校验。</p>
     */
    void recordCompletion(DedupCompletion completion);
}
