package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * SQL监控自动配置类
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Slf4j
@Configuration
@ConditionalOnClass({MeterRegistry.class})
@ConditionalOnProperty(prefix = "fastddd.monitor.sql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SqlMonitoringProperties.class)
public class SqlMonitoringAutoConfiguration {

    /**
     * SQL监控属性配置
     */
    @Bean
    public SqlMonitoringProperties sqlMonitoringProperties() {
        return new SqlMonitoringProperties();
    }

    /**
     * SQL指标收集器
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
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
    @DependsOn({"sqlMonitoringProperties", "sqlMetricsCollector"})
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
    public SqlMonitoringService sqlMonitoringService(SqlMetricsCollector metricsCollector,
                                                   SqlMonitoringProperties properties) {
        return new SqlMonitoringService(metricsCollector, properties);
    }
}