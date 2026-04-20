package com.gallop.fastddd.monitor.sql;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SQL监控控制器
 * 提供SQL监控数据的REST API接口
 * 
 * @author gallop
 * @since 2025-01-10
 */
@RestController
@RequestMapping("/actuator/sql-monitoring")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fastddd.monitor.sql", name = "enabled", havingValue = "true")
public class SqlMonitoringController {

    private final SqlMonitoringService sqlMonitoringService;

    /**
     * 获取SQL监控统计信息
     */
    @GetMapping("/statistics")
    public SqlMetricsCollector.SqlStatistics getStatistics() {
        return sqlMonitoringService.getStatistics();
    }

    /**
     * 获取监控配置信息
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return sqlMonitoringService.getMonitoringConfig();
    }

    /**
     * 获取监控健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        return sqlMonitoringService.getHealthStatus();
    }

    /**
     * 重置统计信息
     */
    @PostMapping("/reset")
    public Map<String, String> resetStatistics() {
        sqlMonitoringService.resetStatistics();
        return Map.of("message", "Statistics reset successfully");
    }

    /**
     * 更新慢查询阈值
     */
    @PutMapping("/slow-query-threshold")
    public Map<String, String> updateSlowQueryThreshold(@RequestParam long threshold) {
        if (threshold <= 0) {
            return Map.of("error", "Threshold must be greater than 0");
        }
        sqlMonitoringService.updateSlowQueryThreshold(threshold);
        return Map.of("message", "Slow query threshold updated to " + threshold + "ms");
    }

    /**
     * 启用/禁用SQL监控
     */
    @PutMapping("/enabled")
    public Map<String, String> setMonitoringEnabled(@RequestParam boolean enabled) {
        sqlMonitoringService.setMonitoringEnabled(enabled);
        return Map.of("message", "SQL monitoring " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 启用/禁用Prometheus指标收集
     */
    @PutMapping("/prometheus")
    public Map<String, String> setPrometheusEnabled(@RequestParam boolean enabled) {
        sqlMonitoringService.setPrometheusEnabled(enabled);
        return Map.of("message", "Prometheus metrics " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * 启用/禁用ELK日志记录
     */
    @PutMapping("/elk")
    public Map<String, String> setElkEnabled(@RequestParam boolean enabled) {
        sqlMonitoringService.setElkEnabled(enabled);
        return Map.of("message", "ELK logging " + (enabled ? "enabled" : "disabled"));
    }
}