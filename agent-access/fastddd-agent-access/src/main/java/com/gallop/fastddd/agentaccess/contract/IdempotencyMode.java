package com.gallop.fastddd.agentaccess.contract;

/**
 * 幂等模式（Contract §5.3：idempotencyMode 在 WRITE/TASK 必选——
 * {@code REQUIRED} 或明确不适用）。
 */
public enum IdempotencyMode {
    /** 必须以 (tenantId, businessOperationId) 持久幂等。 */
    REQUIRED,
    /** 明确声明不适用（仅允许在语义上可证明无需幂等的动作使用）。 */
    NOT_APPLICABLE
}
