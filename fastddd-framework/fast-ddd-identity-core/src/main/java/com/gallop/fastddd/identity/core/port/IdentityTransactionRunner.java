package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;

/**
 * 身份事务 runner 端口（v0.2 §8.3a）：core 事务回调端口，唯一顶层事务所有者。
 *
 * <p>契约：REQUIRED 语义但入口已有活动事务即拒绝（不静默加入）；
 * READ_COMMITTED 短事务（仅本地 SQL，远程调用不进入）；Spring 桥接显式绑定
 * 具名业务 DataSource/TransactionManager，不靠 @Primary 猜资源。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityTransactionRunner {

    /**
     * 在新顶层事务中执行回调。
     *
     * @throws IdentityTransactionActiveException 入口已有活动事务
     */
    <T> T inTransaction(IdentityTransactionCallback<T> callback);

    /** 事务回调（函数式）。 */
    @FunctionalInterface
    interface IdentityTransactionCallback<T> {
        T doInTransaction();
    }
}
