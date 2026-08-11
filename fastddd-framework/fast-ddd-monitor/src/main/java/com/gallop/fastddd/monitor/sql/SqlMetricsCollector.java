package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL指标收集器
 * 提供细粒度的SQL执行指标收集功能
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Slf4j
public class SqlMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final SqlMonitoringProperties properties;
    
    // 缓存Timer实例以提高性能
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);

    public SqlMetricsCollector(MeterRegistry meterRegistry, SqlMonitoringProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        initializeBaseMetrics();
    }

    private void initializeBaseMetrics() {
        // 注册基础指标
        Gauge.builder("sql.queries.total", totalQueries, AtomicLong::doubleValue)
                .description("Total number of SQL queries executed")
                .register(meterRegistry);

        Gauge.builder("sql.queries.slow.total", slowQueries, AtomicLong::doubleValue)
                .description("Total number of slow SQL queries")
                .register(meterRegistry);
    }

    /**
     * 记录SQL执行指标
     */
    public void recordSqlExecution(SqlExecutionInfo executionInfo) {
        if (!properties.isPrometheusEnabled()) {
            return;
        }

        try {
            // 更新总查询计数
            totalQueries.incrementAndGet();
            
            // 更新慢查询计数
            if (executionInfo.isSlowQuery()) {
                slowQueries.incrementAndGet();
            }

            // 记录执行时间
            recordExecutionTime(executionInfo);
            
            // 记录操作计数
            recordOperationCount(executionInfo);
            
            // 记录连接池指标
            recordConnectionMetrics(executionInfo);
            
            // 记录结果集大小
            recordResultSetSize(executionInfo);

        } catch (Exception e) {
            log.warn("Failed to record SQL metrics", e);
        }
    }

    private void recordExecutionTime(SqlExecutionInfo executionInfo) {
        String timerKey = buildTimerKey(executionInfo);
        Timer timer = timerCache.computeIfAbsent(timerKey, key -> 
            Timer.builder("sql.execution.time")
                    .description("SQL execution time in milliseconds")
                    .tag("operation", executionInfo.getOperationType())
                    .tag("slow_query", String.valueOf(executionInfo.isSlowQuery()))
                    .tag("status", executionInfo.getStatus())
                    .register(meterRegistry)
        );
        
        timer.record(executionInfo.getExecutionTime(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void recordOperationCount(SqlExecutionInfo executionInfo) {
        String counterKey = buildCounterKey(executionInfo);
        Counter counter = counterCache.computeIfAbsent(counterKey, key ->
            Counter.builder("sql.operations.total")
                    .description("Total number of SQL operations by type")
                    .tag("operation", executionInfo.getOperationType())
                    .tag("status", executionInfo.getStatus())
                    .register(meterRegistry)
        );
        
        counter.increment();

        // 慢查询专用计数器
        if (executionInfo.isSlowQuery()) {
            String slowCounterKey = "slow_" + counterKey;
            Counter slowCounter = counterCache.computeIfAbsent(slowCounterKey, key ->
                Counter.builder("sql.slow.operations.total")
                        .description("Total number of slow SQL operations by type")
                        .tag("operation", executionInfo.getOperationType())
                        .register(meterRegistry)
            );
            slowCounter.increment();
        }
    }

    private void recordConnectionMetrics(SqlExecutionInfo executionInfo) {
        if (executionInfo.getConnectionUrl() != null) {
            // 提取数据库类型
            String dbType = extractDatabaseType(executionInfo.getConnectionUrl());
            
            Counter dbCounter = counterCache.computeIfAbsent("db_" + dbType, key ->
                Counter.builder("sql.database.operations.total")
                        .description("Total number of operations by database type")
                        .tag("database", dbType)
                        .register(meterRegistry)
            );
            dbCounter.increment();
        }
    }

    private void recordResultSetSize(SqlExecutionInfo executionInfo) {
        if (executionInfo.getResultSetSize() != null && executionInfo.getResultSetSize() > 0) {
            String summaryKey = "resultset_" + executionInfo.getOperationType();
            DistributionSummary summary = summaryCache.computeIfAbsent(summaryKey, key ->
                DistributionSummary.builder("sql.resultset.size")
                        .description("Size of SQL result sets")
                        .tag("operation", executionInfo.getOperationType())
                        .register(meterRegistry)
            );
            summary.record(executionInfo.getResultSetSize());
        }
    }

    private String buildTimerKey(SqlExecutionInfo executionInfo) {
        return String.format("timer_%s_%s_%s", 
                executionInfo.getOperationType(),
                executionInfo.isSlowQuery() ? "slow" : "normal",
                executionInfo.getStatus());
    }

    private String buildCounterKey(SqlExecutionInfo executionInfo) {
        return String.format("counter_%s_%s", 
                executionInfo.getOperationType(),
                executionInfo.getStatus());
    }

    private String extractDatabaseType(String connectionUrl) {
        if (connectionUrl == null) {
            return "unknown";
        }
        
        String lowerUrl = connectionUrl.toLowerCase();
        if (lowerUrl.contains("mysql")) {
            return "mysql";
        } else if (lowerUrl.contains("postgresql")) {
            return "postgresql";
        } else if (lowerUrl.contains("oracle")) {
            return "oracle";
        } else if (lowerUrl.contains("sqlserver")) {
            return "sqlserver";
        } else if (lowerUrl.contains("h2")) {
            return "h2";
        } else {
            return "other";
        }
    }

    /**
     * 获取当前统计信息
     */
    public SqlStatistics getStatistics() {
        return SqlStatistics.builder()
                .totalQueries(totalQueries.get())
                .slowQueries(slowQueries.get())
                .build();
    }

    /**
     * 重置查询/慢查询计数（MON-002）。
     *
     * <p>仅清零本收集器维护的 {@link AtomicLong} 计数器；已注册到 {@link MeterRegistry} 的
     * Gauge 会即时反映新值。Micrometer 的 Timer/Counter/DistributionSummary 为累计型，
     * 无法按实例重置——这是 Micrometer 的既有语义，非本框架缺陷。
     */
    public void reset() {
        totalQueries.set(0);
        slowQueries.set(0);
    }

    /**
     * SQL统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class SqlStatistics {
        private long totalQueries;
        private long slowQueries;

        public double getSlowQueryPercentage() {
            return totalQueries > 0 ? (double) slowQueries / totalQueries * 100 : 0.0;
        }
    }
}