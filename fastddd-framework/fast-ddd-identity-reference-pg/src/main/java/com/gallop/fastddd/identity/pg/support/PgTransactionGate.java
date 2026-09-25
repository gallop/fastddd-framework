package com.gallop.fastddd.identity.pg.support;

import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;
import com.gallop.fastddd.identity.core.error.TransientTransactionConflictException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * PG 适配器公共支撑：MANDATORY 事务门禁 + 核心异常翻译。
 *
 * <p>写端口只在外层事务内执行（不各自提交）；绑定唯一键竞争与死锁/序列化失败
 * 翻译为 core 的可重试冲突异常，由用例层完整回滚后新事务重试（§8.3a）。</p>
 *
 * <p><b>门禁绑定适配器数据源（W2-1 修复轮 W21-03）</b>：每个适配器以自己的
 * {@link DataSource} 实例构造门禁；除全局"有实际事务"
 * （{@code TransactionSynchronizationManager.isActualTransactionActive()}）外，
 * 还要求该数据源上绑定了线程级连接资源（{@code ConnectionHolder}，即
 * {@code JdbcTemplate} 参与外层事务的同一判据）。由此检出三类错误装配并在
 * 首次写入前拒绝：</p>
 * <ul>
 *   <li>无任何实际事务（仅有事务同步、无实际事务不算）；</li>
 *   <li>事务开在另一个 DataSource 上（即使两个 DataSource 指向同一数据库，
 *       适配器所在连接不在外层事务内，写入会自动提交、外层回滚无法带走）；</li>
 *   <li>数据源上无线程绑定连接资源。</li>
 * </ul>
 *
 * <p>判据与 Spring 自身的事务参与语义一致且更严（其上再叠加全局实际事务要求）；
 * {@code ConnectionHolder#isTransactionActive()} 为受保护 API 不可外部读取，
 * 故不再单独区分"同步绑定的非事务连接"——该形态在"无实际事务"分支已被拒绝，
 * 其余形态（如 JTA 同步绑定）写入与事务共用同一连接，视为参与。</p>
 *
 * <p><b>自定义 runner 契约</b>：宿主替换 {@code IdentityTransactionRunner} 时，
 * 必须在 {@code fastddd.identity.transaction.data-source-bean} 指定的同一
 * {@link DataSource} <b>Bean 实例</b>上开启事务（与默认
 * {@code SpringIdentityTransactionRunner} 一致）；否则适配器首次写入前即失败。
 * 判定基于 DataSource 实例同一性——宿主若包装/代理了该 Bean，须保证事务管理器
 * 与适配器解析到同一实例。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PgTransactionGate {

    private final DataSource dataSource;

    public PgTransactionGate(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * MANDATORY 门禁：无实际事务、或适配器数据源上无线程绑定的连接资源
     * （{@code ConnectionHolder}），均在首次写入前拒绝（本适配器不自行开事务提交）。
     */
    public void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）必须在身份事务 runner 的外层事务内执行（MANDATORY）："
                            + "当前线程无实际事务");
        }
        Object resource = TransactionSynchronizationManager.getResource(dataSource);
        if (!(resource instanceof ConnectionHolder)) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）要求事务绑定在本适配器数据源上（MANDATORY）："
                            + "当前线程事务未绑定到适配器 DataSource（可能开在另一个数据源上）。自定义"
                            + " IdentityTransactionRunner 必须与 fastddd.identity.transaction.data-source-bean"
                            + " 指定的同一 DataSource 实例开事务");
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
