package com.gallop.fastddd.agentaccess.contract;

/**
 * 十二外部稳定结果码（Contract §9 逐字冻结；SCREAMING_SNAKE_CASE 是 wire 层
 * 唯一稳定契约，experimental 制品线内一经发布不得改义——AA-02 冻结）。
 *
 * <p>{@code UNKNOWN} 不是错误分类而是可证明性状态：不得把 {@code UNKNOWN}
 * 映射为任何具体失败，也不得把具体失败伪装为 {@code UNKNOWN}。</p>
 */
public enum ActionOutcomeCode {
    TARGET_AUTHENTICATION_FAILED,
    TARGET_AUTHORIZATION_DENIED,
    ACTION_NOT_EXPOSED,
    PERMIT_INVALID_OR_EXPIRED,
    PERMIT_BINDING_MISMATCH,
    PRECONDITION_CHANGED,
    RESOURCE_VERSION_CONFLICT,
    BUSINESS_RULE_VIOLATION,
    IDEMPOTENCY_CONFLICT,
    ALREADY_COMPLETED,
    TARGET_TEMPORARILY_UNAVAILABLE,
    UNKNOWN
}
