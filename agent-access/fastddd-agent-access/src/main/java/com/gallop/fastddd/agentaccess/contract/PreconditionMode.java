package com.gallop.fastddd.agentaccess.contract;

/**
 * 前置条件模式（Contract §5.3：preconditionMode 必选）。
 */
public enum PreconditionMode {
    /** 必须携带资源前置条件并做事务内重读校验。 */
    REQUIRED,
    /** 可选携带。 */
    OPTIONAL,
    /** 不适用。 */
    NOT_APPLICABLE
}
