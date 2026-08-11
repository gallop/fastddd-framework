package com.gallop.fastddd.monitor.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

import javax.sql.DataSource;

/**
 * SQL 监控 DataSource 后置处理器（MON-002）。
 *
 * <p>将 Spring 容器中（典型为 Spring Boot 自动配置的）主 {@link DataSource} 包装为
 * 监控代理，使经由该 DataSource 的真实 SQL 执行被 {@link SqlMonitoringJdbcProxy} 拦截，
 * 进而驱动 {@link SqlMonitoringExecutionRecorder} → {@link SqlMetricsCollector} 的指标闭环。
 *
 * <p>实现细节（规避 BeanPostProcessor 经典陷阱）：
 * <ul>
 *   <li>作为 <b>static</b> {@code @Bean} 注册，构造只接收 {@link ConfigurableListableBeanFactory}，
 *       不在构造期解析业务 bean，避免触发其他 bean 的提前实例化。</li>
 *   <li>{@link SqlMonitoringExecutionRecorder} 在首次包装时懒解析并缓存。</li>
 *   <li>只包装尚未被代理（非 {@link SqlMonitoringProxied}）的 {@link DataSource}，避免重复包装。</li>
 *   <li>低优先级（{@link Ordered#LOWEST_PRECEDENCE}）：让其他可能产出/校验 DataSource 的后置处理器先跑；
 *       因 postProcessAfterInitialization 在 bean 交付给依赖方前生效，事务管理器等会拿到代理实例。</li>
 * </ul>
 *
 * <p>仅作用于单候选 DataSource 消费者（由装配侧 {@code @ConditionalOnSingleCandidate(DataSource.class)}
 * 限定）；多 DataSource 场景本处理器不装配。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Slf4j
public class SqlMonitoringDataSourcePostProcessor implements BeanPostProcessor, Ordered {

    private final ConfigurableListableBeanFactory beanFactory;
    private volatile SqlMonitoringExecutionRecorder cachedRecorder;

    public SqlMonitoringDataSourcePostProcessor(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof SqlMonitoringProxied)) {
            log.debug("Wrapping DataSource [{}] with SQL monitoring proxy", beanName);
            return SqlMonitoringJdbcProxy.wrapDataSource((DataSource) bean, resolveRecorder());
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private SqlMonitoringExecutionRecorder resolveRecorder() {
        SqlMonitoringExecutionRecorder recorder = cachedRecorder;
        if (recorder == null) {
            synchronized (this) {
                recorder = cachedRecorder;
                if (recorder == null) {
                    recorder = beanFactory.getBean(SqlMonitoringExecutionRecorder.class);
                    cachedRecorder = recorder;
                }
            }
        }
        return recorder;
    }
}
