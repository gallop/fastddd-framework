package com.gallop.fastddd.identity.pg.support;

import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;
import com.gallop.fastddd.identity.core.error.TransientTransactionConflictException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PG 适配器公共支撑：MANDATORY 事务门禁 + 核心异常翻译。
 *
 * <p>写端口只在外层事务内执行（不各自提交）；绑定唯一键竞争与死锁/序列化失败
 * 翻译为 core 的可重试冲突异常，由用例层完整回滚后新事务重试（§8.3a）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PgTransactionGate {

    private PgTransactionGate() {
    }

    /** MANDATORY 门禁：无活动事务即拒绝（本适配器不自行开事务提交）。 */
    public static void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）必须在身份事务 runner 的外层事务内执行（MANDATORY）");
        }
    }

    /** 唯一约束冲突（绑定五元组）→ 绑定并发修改。 */
    public static RuntimeException translateBindingConflict(String operation, RuntimeException e) {
        if (e instanceof DuplicateKeyException) {
            return new BindingConcurrentModificationException(operation + "：绑定唯一键竞争", e);
        }
        return e;
    }

    /** 死锁/序列化失败 → 瞬时冲突（可重试）。 */
    public static RuntimeException translateTransient(String operation, RuntimeException e) {
        if (e instanceof DeadlockLoserDataAccessException || e instanceof CannotSerializeTransactionException) {
            return new TransientTransactionConflictException(operation + "：死锁/序列化冲突", e);
        }
        return e;
    }
}
