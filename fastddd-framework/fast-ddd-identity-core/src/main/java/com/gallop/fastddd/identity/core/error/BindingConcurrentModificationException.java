package com.gallop.fastddd.identity.core.error;

/**
 * 绑定唯一键竞争（并发首次登录）：存储适配器将绑定插入的唯一约束冲突翻译为本异常；
 * 用例层完整回滚后在新事务回读赢家。
 *
 * @author gallop
 * @since 1.1.0
 */
public class BindingConcurrentModificationException extends RetryableTransactionConflictException {

    public BindingConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
