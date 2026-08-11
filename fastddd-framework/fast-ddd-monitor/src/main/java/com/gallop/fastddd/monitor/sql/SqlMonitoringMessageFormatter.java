package com.gallop.fastddd.monitor.sql;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * SQL监控消息格式化器
 * 实现P6Spy的MessageFormattingStrategy接口，用于自定义SQL日志格式和监控
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Slf4j
public class SqlMonitoringMessageFormatter implements MessageFormattingStrategy {

    private SqlMonitoringProperties properties;
    private MeterRegistry meterRegistry;
    private Timer sqlExecutionTimer;
    
    // 编译后的排除模式
    private Pattern[] excludePatterns;
    private Pattern[] includePatterns;

    public SqlMonitoringMessageFormatter() {
        // 默认构造函数，用于P6Spy实例化
    }

    @Autowired(required = false)
    public void setProperties(SqlMonitoringProperties properties) {
        this.properties = properties;
        if (properties != null) {
            initializePatterns();
        }
    }

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            this.sqlExecutionTimer = Timer.builder("sql.execution.time")
                    .description("SQL execution time")
                    .register(meterRegistry);
        }
    }

    private void initializePatterns() {
        // 初始化排除模式
        if (properties.getExcludePatterns() != null) {
            excludePatterns = new Pattern[properties.getExcludePatterns().length];
            for (int i = 0; i < properties.getExcludePatterns().length; i++) {
                excludePatterns[i] = Pattern.compile(properties.getExcludePatterns()[i], Pattern.CASE_INSENSITIVE);
            }
        }

        // 初始化包含模式
        if (properties.getIncludePatterns() != null && properties.getIncludePatterns().length > 0) {
            includePatterns = new Pattern[properties.getIncludePatterns().length];
            for (int i = 0; i < properties.getIncludePatterns().length; i++) {
                includePatterns[i] = Pattern.compile(properties.getIncludePatterns()[i], Pattern.CASE_INSENSITIVE);
            }
        }
    }

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, 
                               String prepared, String sql, String url) {
        
        // 如果监控未启用，返回空字符串
        if (properties != null && !properties.isEnabled()) {
            return "";
        }

        try {
            // 检查是否应该排除此SQL
            if (shouldExcludeSql(sql)) {
                return "";
            }

            // 构建SQL执行信息
            SqlExecutionInfo executionInfo = buildExecutionInfo(connectionId, elapsed, category, 
                    prepared, sql, url, now);

            // 记录Prometheus指标
            recordPrometheusMetrics(executionInfo);

            // 记录ELK日志
            recordElkLog(executionInfo);

            // 返回格式化的日志消息（用于P6Spy的标准输出）
            return formatLogMessage(executionInfo);

        } catch (Exception e) {
            log.error("Error in SQL monitoring message formatter", e);
            return String.format("SQL Monitor Error: %s | Original: %s", e.getMessage(), sql);
        }
    }

    private boolean shouldExcludeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return true;
        }

        // 检查排除模式
        if (excludePatterns != null) {
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(sql).matches()) {
                    return true;
                }
            }
        }

        // 检查包含模式（如果定义了包含模式）
        if (includePatterns != null && includePatterns.length > 0) {
            boolean matches = false;
            for (Pattern pattern : includePatterns) {
                if (pattern.matcher(sql).matches()) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return true;
            }
        }

        return false;
    }

    private SqlExecutionInfo buildExecutionInfo(int connectionId, long elapsed, String category,
                                              String prepared, String sql, String url, String timestamp) {
        
        String operationType = SqlExecutionInfo.extractOperationType(sql);
        boolean isSlowQuery = properties != null && elapsed > properties.getSlowQueryThreshold();
        
        // 截断过长的SQL
        String truncatedSql = sql;
        if (properties != null && sql.length() > properties.getMaxSqlLength()) {
            truncatedSql = sql.substring(0, properties.getMaxSqlLength()) + "...";
        }

        return SqlExecutionInfo.builder()
                .sql(truncatedSql)
                .formattedSql(prepared)
                .executionTime(elapsed)
                .operationType(operationType)
                .connectionUrl(url)
                .startTime(LocalDateTime.now().minusNanos(elapsed * 1_000_000))
                .endTime(LocalDateTime.now())
                .slowQuery(isSlowQuery)
                .status("SUCCESS")
                .threadName(Thread.currentThread().getName())
                .context(Map.of(
                        "connectionId", connectionId,
                        "category", category,
                        "timestamp", timestamp
                ))
                .build();
    }

    private void recordPrometheusMetrics(SqlExecutionInfo executionInfo) {
        if (properties != null && !properties.isPrometheusEnabled()) {
            return;
        }

        if (meterRegistry != null && sqlExecutionTimer != null) {
            try {
                // 记录SQL执行时间指标，带标签
                Timer.Sample sample = Timer.start(meterRegistry);
                sample.stop(Timer.builder("sql.execution.time")
                        .description("SQL execution time")
                        .tag("operation", executionInfo.getOperationType())
                        .tag("slow_query", String.valueOf(executionInfo.isSlowQuery()))
                        .tag("thread", executionInfo.getThreadName())
                        .register(meterRegistry));

                // 记录慢查询计数器
                if (executionInfo.isSlowQuery()) {
                    meterRegistry.counter("sql.slow.queries.total",
                            "operation", executionInfo.getOperationType())
                            .increment();
                }

                // 记录SQL操作类型计数器
                meterRegistry.counter("sql.operations.total",
                        "operation", executionInfo.getOperationType())
                        .increment();

            } catch (Exception e) {
                log.warn("Failed to record Prometheus metrics for SQL execution", e);
            }
        }
    }

    private void recordElkLog(SqlExecutionInfo executionInfo) {
        if (properties != null && !properties.isElkEnabled()) {
            return;
        }

        try {
            // 构建结构化日志数据
            Map<String, Object> logData = new HashMap<>();
            logData.put("sql", executionInfo.getSql());
            logData.put("executionTime", executionInfo.getExecutionTime());
            logData.put("operationType", executionInfo.getOperationType());
            logData.put("slowQuery", executionInfo.isSlowQuery());
            logData.put("connectionUrl", executionInfo.getConnectionUrl());
            logData.put("threadName", executionInfo.getThreadName());
            logData.put("timestamp", executionInfo.getEndTime());
            logData.put("status", executionInfo.getStatus());

            if (properties != null && properties.isLogParameters() && executionInfo.getFormattedSql() != null) {
                logData.put("formattedSql", executionInfo.getFormattedSql());
            }

            // 根据是否为慢查询选择不同的日志级别
            if (executionInfo.isSlowQuery()) {
                log.warn("Slow SQL query detected: {}ms", 
                        executionInfo.getExecutionTime(),
                        StructuredArguments.entries(logData));
            } else {
                log.info("SQL execution completed: {}ms", 
                        executionInfo.getExecutionTime(),
                        StructuredArguments.entries(logData));
            }

        } catch (Exception e) {
            log.warn("Failed to record ELK log for SQL execution", e);
        }
    }

    private String formatLogMessage(SqlExecutionInfo executionInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("[SQL Monitor] ");
        sb.append("Time: ").append(executionInfo.getExecutionTime()).append("ms");
        sb.append(" | Type: ").append(executionInfo.getOperationType());
        
        if (executionInfo.isSlowQuery()) {
            sb.append(" | SLOW QUERY");
        }
        
        sb.append(" | Thread: ").append(executionInfo.getThreadName());
        sb.append(" | SQL: ").append(executionInfo.getSql());
        
        return sb.toString();
    }
}