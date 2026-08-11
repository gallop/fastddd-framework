package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQL监控自动配置类
 *
 * <p>MON-001 修复：
 * <ul>
 *   <li>移除 {@code @Bean sqlMonitoringProperties()} —— 与
 *       {@link EnableConfigurationProperties} 重复注册导致
 *       {@code NoUniqueBeanDefinitionException}（审计 P0-4 根因）。
 *       {@link SqlMonitoringProperties} 现仅由
 *       {@link EnableConfigurationProperties} 单一方式注册。</li>
 *   <li>类级保留 {@link ConditionalOnClass}({@link MeterRegistry}) —— 仅在 Micrometer
 *       在场时装配（MeterRegistry 多候选的精细退让属 MON-002 范畴，本任务不改注入契约）。</li>
 *   <li>{@code sqlMetricsCollector} / {@code sqlMonitoringMessageFormatter} /
 *       {@code sqlMonitoringService} 三个 {@code @Bean} 追加
 *       {@link ConditionalOnMissingBean}，业务方自定义时框架退让；
 *       同时移除这三个类上的 {@code @Component}/{@code @Service} 刻板注解，
 *       消除"组件扫描 + AutoConfig @Bean"双重注册隐患（同一缺陷模式）。</li>
 *   <li>移除 {@code sqlMonitoringMessageFormatter} 上依赖固定 Bean 名的
 *       {@code @DependsOn} —— 方法签名已声明依赖，Spring 自动排序，固定名引用冗余且脆弱。</li>
 * </ul>
 *
 * @author gallop
 * @since 2025-01-10
 */
@Slf4j
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "fastddd.monitor.sql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SqlMonitoringProperties.class)
public class SqlMonitoringAutoConfiguration {

    /**
     * SQL指标收集器
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
     * SQL监控消息格式化器
     * 注意：这个Bean主要用于依赖注入，P6Spy会通过反射创建实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMonitoringMessageFormatter sqlMonitoringMessageFormatter(
            SqlMonitoringProperties properties,
            MeterRegistry meterRegistry) {

        log.info("Initializing SQL Monitoring Message Formatter");
        SqlMonitoringMessageFormatter formatter = new SqlMonitoringMessageFormatter();
        formatter.setProperties(properties);
        formatter.setMeterRegistry(meterRegistry);
        return formatter;
    }

    /**
     * SQL监控服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlMonitoringService sqlMonitoringService(SqlMetricsCollector metricsCollector,
                                                   SqlMonitoringProperties properties) {
        return new SqlMonitoringService(metricsCollector, properties);
    }
}
