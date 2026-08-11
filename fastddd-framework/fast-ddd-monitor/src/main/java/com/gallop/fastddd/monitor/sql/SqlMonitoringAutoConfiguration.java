package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * SQL 监控自动配置（MON-001 + MON-002 演进）。
 *
 * <p>MON-001 已修复：
 * <ul>
 *   <li>移除 {@code @Bean sqlMonitoringProperties()}（P0-4 双注册根因），属性类仅由
 *       {@link EnableConfigurationProperties} 单一方式注册。</li>
 *   <li>组件类移除刻板注解，{@code @Bean} 补 {@link ConditionalOnMissingBean}。</li>
 * </ul>
 *
 * <p>MON-002 真实链路闭环：
 * <ul>
 *   <li>类升级为 {@link AutoConfiguration}（原为普通 {@link Configuration}），
 *       并 {@link AutoConfigureAfter} {@link DataSourceAutoConfiguration}，使
 *       {@code @ConditionalOnSingleCandidate(DataSource.class)} 求值时 DataSource 已注册。</li>
 *   <li>移除原 {@code SqlMonitoringMessageFormatter}（P6Spy 反射实例化绕过 Spring DI 的"双实例"缺陷）
 *       及其 {@code @Bean}；删除 P6Spy 依赖与 {@code spy.properties}。</li>
 *   <li>新增 {@link SqlMonitoringExecutionRecorder}（普通 Spring bean，经
 *       {@link SqlMonitoringJdbcProxy} 接入真实 SQL 执行链路 → {@link SqlMetricsCollector}）。</li>
 *   <li>新增 {@link SqlMonitoringDataSourcePostProcessor}（static {@code @Bean}），仅单候选
 *       {@link DataSource} 场景包装主数据源——多数据源消费者不装配（留后续任务）。</li>
 *   <li>新增 {@link SqlMonitoringEndpoint}（真正 Actuator {@code @Endpoint}），替代原伪
 *       {@code @RestController}；由 Actuator exposure 体系治理（默认不暴露）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass({MeterRegistry.class, DataSource.class})
@ConditionalOnProperty(prefix = "fastddd.monitor.sql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SqlMonitoringProperties.class)
public class SqlMonitoringAutoConfiguration {

    /**
     * SQL 指标收集器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMetricsCollector sqlMetricsCollector(MeterRegistry meterRegistry,
                                                  SqlMonitoringProperties properties) {
        log.info("Initializing SQL Metrics Collector with slow query threshold: {}ms",
                properties.getSlowQueryThreshold());
        return new SqlMetricsCollector(meterRegistry, properties);
    }

    /**
     * SQL 执行记录器（MON-002）：经 JDBC 代理接收真实执行事件，驱动指标 + 脱敏日志。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMonitoringExecutionRecorder sqlMonitoringExecutionRecorder(SqlMetricsCollector metricsCollector,
                                                                        SqlMonitoringProperties properties) {
        return new SqlMonitoringExecutionRecorder(metricsCollector, properties);
    }

    /**
     * SQL 监控服务（聚合查询/动态配置门面）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMonitoringService sqlMonitoringService(SqlMetricsCollector metricsCollector,
                                                   SqlMonitoringProperties properties) {
        return new SqlMonitoringService(metricsCollector, properties);
    }

    /**
     * SQL 监控 Actuator Endpoint（MON-002）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMonitoringEndpoint sqlMonitoringEndpoint(SqlMonitoringService sqlMonitoringService,
                                                       SqlMonitoringProperties properties) {
        return new SqlMonitoringEndpoint(sqlMonitoringService, properties);
    }

    /**
     * DataSource 后置处理器（MON-002）：仅单候选 DataSource 消费者装配。
     *
     * <p>声明为 {@code static}：BeanPostProcessor 必须极早注册，构造期只接收
     * {@link ConfigurableListableBeanFactory}（早可用），业务 bean 由处理器懒解析，避免提前实例化。
     */
    @Bean
    @ConditionalOnSingleCandidate(DataSource.class)
    @ConditionalOnBean(SqlMonitoringExecutionRecorder.class)
    public static SqlMonitoringDataSourcePostProcessor sqlMonitoringDataSourcePostProcessor(
            ConfigurableListableBeanFactory beanFactory) {
        return new SqlMonitoringDataSourcePostProcessor(beanFactory);
    }
}
