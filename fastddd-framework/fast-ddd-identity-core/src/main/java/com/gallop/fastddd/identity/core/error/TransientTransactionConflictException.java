package com.gallop.fastddd.identity.core.error;

/**
 * 死锁/序列化失败类瞬时冲突：同样按 §8.3a 完整回滚后新事务有限重试。
 *
 * @author gallop
 * @since 1.1.0
 */
public class TransientTransactionConflictException extends RetryableTransactionConflictException {

    public TransientTransactionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
