package com.gallop.fastddd.agentaccess.contract;

/**
 * 对账授权范围（Contract §5.7 冻结：首版仅 {@code STATUS_QUERY}）。
 *
 * <p>单值枚举是显式冻结而非占位：对账授权不授予任何执行权——
 * {@code STATUS_QUERY} 不可当 {@code WRITE}/{@code READ} 使用，也不得
 * 复用原执行 Permit 的 effect 语义。新增范围必须经契约版本化（§11）。</p>
 */
public enum ReconciliationScope {
    STATUS_QUERY
}
