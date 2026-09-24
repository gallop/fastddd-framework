package com.gallop.fastddd.identity.core.error;

/**
 * 可重试的事务冲突标记（§8.3a）：仅绑定唯一键竞争与死锁/序列化失败归入此类；
 * 重试 = 完整回滚后**新开事务**回读赢家，默认 ≤3 次。
 *
 * @author gallop
 * @since 1.1.0
 */
public abstract class RetryableTransactionConflictException extends RuntimeException {

    protected RetryableTransactionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
