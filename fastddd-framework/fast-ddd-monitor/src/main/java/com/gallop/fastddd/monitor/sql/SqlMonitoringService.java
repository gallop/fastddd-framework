package com.gallop.fastddd.monitor.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL监控服务
 * 提供SQL监控数据的查询和管理功能
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Slf4j
@RequiredArgsConstructor
public class SqlMonitoringService {

    private final SqlMetricsCollector metricsCollector;
    private final SqlMonitoringProperties properties;

    /**
     * 获取SQL监控统计信息
     */
    public SqlMetricsCollector.SqlStatistics getStatistics() {
        return metricsCollector.getStatistics();
    }

    /**
     * 获取监控配置信息
     */
    public Map<String, Object> getMonitoringConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("slowQueryThreshold", properties.getSlowQueryThreshold());
        config.put("logParameters", properties.isLogParameters());
        config.put("logResultSetSize", properties.isLogResultSetSize());
        config.put("prometheusEnabled", properties.isPrometheusEnabled());
        config.put("elkEnabled", properties.isElkEnabled());
        config.put("maxSqlLength", properties.getMaxSqlLength());
        config.put("includeStackTrace", properties.isIncludeStackTrace());
        config.put("excludePatterns", properties.getExcludePatterns());
        config.put("includePatterns", properties.getIncludePatterns());
        return config;
    }

    /**
     * 获取监控健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        SqlMetricsCollector.SqlStatistics stats = getStatistics();
        
        health.put("status", properties.isEnabled() ? "UP" : "DOWN");
        health.put("totalQueries", stats.getTotalQueries());
        health.put("slowQueries", stats.getSlowQueries());
        health.put("slowQueryPercentage", stats.getSlowQueryPercentage());
        
        // 健康状态判断
        double slowQueryPercentage = stats.getSlowQueryPercentage();
        if (slowQueryPercentage > 20) {
            health.put("status", "WARN");
            health.put("message", "High percentage of slow queries detected");
        } else if (slowQueryPercentage > 50) {
            health.put("status", "DOWN");
            health.put("message", "Critical: Very high percentage of slow queries");
        }
        
        return health;
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        log.info("Resetting SQL monitoring statistics");
        metricsCollector.reset();
    }

    /**
     * 动态更新慢查询阈值
     */
    public void updateSlowQueryThreshold(long threshold) {
        log.info("Updating slow query threshold from {}ms to {}ms", 
                properties.getSlowQueryThreshold(), threshold);
        properties.setSlowQueryThreshold(threshold);
    }

    /**
     * 启用/禁用SQL监控
     */
    public void setMonitoringEnabled(boolean enabled) {
        log.info("Setting SQL monitoring enabled: {}", enabled);
        properties.setEnabled(enabled);
    }

    /**
     * 启用/禁用Prometheus指标收集
     */
    public void setPrometheusEnabled(boolean enabled) {
        log.info("Setting Prometheus metrics enabled: {}", enabled);
        properties.setPrometheusEnabled(enabled);
    }

    /**
     * 启用/禁用ELK日志记录
     */
    public void setElkEnabled(boolean enabled) {
        log.info("Setting ELK logging enabled: {}", enabled);
        properties.setElkEnabled(enabled);
    }
}