package com.gallop.fastddd.monitor.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL 执行记录器（MON-002）。
 *
 * <p>取代原 {@code SqlMonitoringMessageFormatter}（P6Spy 反射实例化绕过 Spring DI 的"双实例"缺陷）：
 * 本类是普通 Spring bean，由 {@link SqlMonitoringJdbcProxy} 注入并调用，
 * 拥有完整的 {@link SqlMetricsCollector} / {@link SqlMonitoringProperties} 依赖注入。
 *
 * <p>职责：
 * <ol>
 *   <li>将一次 SQL 执行转为 {@link SqlExecutionInfo} 并经 {@link SqlMetricsCollector} 记录 Micrometer 指标
 *       （指标仅按操作类型/状态聚合，<b>不含 SQL 文本</b>）。</li>
 *   <li>按 {@link SqlMonitoringProperties#isElkEnabled()} 决定是否输出结构化日志；
 *       日志中的 SQL 文本一律经 {@link SqlMaskingUtils} 脱敏；SQL 文本仅在
 *       {@link SqlMonitoringProperties#isLogParameters()} 开启时才输出。</li>
 * </ol>
 *
 * <p>排除规则（{@link SqlMonitoringProperties#getExcludePatterns()}）在记录前生效——
 * 命中排除模式的 SQL 既不计入指标也不输出日志。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Slf4j
@RequiredArgsConstructor
public class SqlMonitoringExecutionRecorder {

    private final SqlMetricsCollector metricsCollector;
    private final SqlMonitoringProperties properties;

    private volatile Pattern[] compiledExcludes;

    /**
     * 记录一次 SQL 执行（指标 + 可选脱敏日志）。
     *
     * @param sql           SQL 文本（PreparedStatement 为预编译 SQL，Statement 为完整 SQL）
     * @param elapsedMs     执行耗时（毫秒）
     * @param connectionUrl 数据库连接 URL（用于 db 类型 tag；可能为 null）
     * @param success       是否成功
     * @param errorMessage  失败时的错误信息（成功为 null）
     * @param affectedRows  DML 影响行数（不可得时为 null）
     */
    public void record(String sql, long elapsedMs, String connectionUrl,
                       boolean success, String errorMessage, Integer affectedRows) {
        if (!properties.isEnabled()) {
            return;
        }
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        if (isExcluded(sql)) {
            return;
        }

        String operationType = SqlExecutionInfo.extractOperationType(sql);
        boolean slow = elapsedMs > properties.getSlowQueryThreshold();
        String truncated = truncate(sql, properties.getMaxSqlLength());

        SqlExecutionInfo info = SqlExecutionInfo.builder()
                .sql(truncated)
                .executionTime(elapsedMs)
                .operationType(operationType)
                .connectionUrl(connectionUrl)
                .slowQuery(slow)
                .status(success ? "SUCCESS" : "ERROR")
                .errorMessage(errorMessage)
                .affectedRows(affectedRows)
                .threadName(Thread.currentThread().getName())
                .endTime(LocalDateTime.now())
                .build();

        // 指标（不含 SQL 文本）
        metricsCollector.recordSqlExecution(info);

        // 结构化日志（SQL 文本仅在 logParameters 开启时输出，且经脱敏）
        if (properties.isElkEnabled()) {
            logExecution(info);
        }
    }

    private void logExecution(SqlExecutionInfo info) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("executionTime", info.getExecutionTime());
        logData.put("operationType", info.getOperationType());
        logData.put("slowQuery", info.isSlowQuery());
        logData.put("status", info.getStatus());
        logData.put("connectionUrl", info.getConnectionUrl());
        logData.put("threadName", info.getThreadName());
        if (info.getAffectedRows() != null) {
            logData.put("affectedRows", info.getAffectedRows());
        }
        if (info.getErrorMessage() != null) {
            logData.put("errorMessage", info.getErrorMessage());
        }
        // 关键：默认 logParameters=false → 不输出 SQL 文本；开启时输出经脱敏的文本
        if (properties.isLogParameters()) {
            logData.put("sql", SqlMaskingUtils.mask(info.getSql()));
        }

        if (info.isSlowQuery()) {
            log.warn("Slow SQL detected: {}ms", info.getExecutionTime(),
                    StructuredArguments.entries(logData));
        } else {
            log.info("SQL executed: {}ms", info.getExecutionTime(),
                    StructuredArguments.entries(logData));
        }
    }

    private boolean isExcluded(String sql) {
        Pattern[] patterns = compiledExcludes;
        if (patterns == null) {
            patterns = compileExcludes(properties.getExcludePatterns());
            compiledExcludes = patterns;
        }
        if (patterns.length == 0) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(sql).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern[] compileExcludes(String[] rawPatterns) {
        if (rawPatterns == null || rawPatterns.length == 0) {
            return new Pattern[0];
        }
        Pattern[] compiled = new Pattern[rawPatterns.length];
        for (int i = 0; i < rawPatterns.length; i++) {
            compiled[i] = Pattern.compile(rawPatterns[i], Pattern.CASE_INSENSITIVE);
        }
        return compiled;
    }

    private static String truncate(String sql, int maxLen) {
        if (sql.length() <= maxLen) {
            return sql;
        }
        return sql.substring(0, maxLen) + "...";
    }
}
