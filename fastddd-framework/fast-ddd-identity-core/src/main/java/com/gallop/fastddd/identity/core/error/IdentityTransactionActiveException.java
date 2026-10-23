package com.gallop.fastddd.identity.core.error;

/**
 * 事务 runner 入口已有活动事务（§8.3a）：runner 是唯一顶层事务所有者，加入既有事务被拒绝。
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityTransactionActiveException extends RuntimeException {

    public IdentityTransactionActiveException(String message) {
        super(message);
    }
}
