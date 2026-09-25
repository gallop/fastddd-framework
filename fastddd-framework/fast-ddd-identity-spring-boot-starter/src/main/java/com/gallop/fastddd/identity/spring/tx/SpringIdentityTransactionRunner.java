package com.gallop.fastddd.identity.spring.tx;

import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 身份统一签发/停用事务 T2/T3/T4 的 Spring 桥接（v0.2 §8.3）。
 *
 * <p>与 reference-pg 测试支撑同契约：入口已有活动事务即拒绝（杜绝静默加入外层事务）；
 * READ_COMMITTED + 10s 超时；私有 {@link DataSourceTransactionManager} 绑定按名解析的
 * 业务 DataSource（不注册为 Bean、不猜 @Primary），写端口的 MANDATORY 门禁依赖
 * 本 runner 开启的线程绑定事务。</p>
 *
 * <p><b>自定义 runner 契约（W2-1 修复轮 W21-03）</b>：宿主替换
 * {@code IdentityTransactionRunner} 时，必须在
 * {@code fastddd.identity.transaction.data-source-bean} 指定的同一 {@link DataSource}
 * <b>Bean 实例</b>上开启并提交事务——PG 写端口门禁除校验"有实际事务"外，还校验
 * 事务性连接绑定在该实例上；事务开在别的 DataSource（即使指向同一数据库）会在
 * 首次写入前被拒绝（见 {@code PgTransactionGate}）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class SpringIdentityTransactionRunner implements IdentityTransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringIdentityTransactionRunner(DataSource dataSource) {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        this.transactionTemplate = new TransactionTemplate(manager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setTimeout(10);
    }

    @Override
    public <T> T inTransaction(IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) {
            throw new IdentityTransactionActiveException("入口已有活动事务，identity runner 拒绝加入（T2 须独立事务）");
        }
        return transactionTemplate.execute(status -> callback.doInTransaction());
    }
}
