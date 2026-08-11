package com.gallop.fastddd.monitor.sql;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL监控配置属性
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Data
@ConfigurationProperties(prefix = "fastddd.monitor.sql")
public class SqlMonitoringProperties {

    /**
     * 是否启用SQL监控
     */
    private boolean enabled = true;

    /**
     * 慢查询阈值（毫秒）
     * 超过此阈值的查询将被记录为慢查询
     */
    private long slowQueryThreshold = 1000L;

    /**
     * 是否在结构化日志中输出 SQL 文本（已脱敏）。
     * <p>MON-002：默认关闭——即便开启，SQL 文本亦经 {@code SqlMaskingUtils} 敏感参数脱敏，
     * 满足"默认日志不包含敏感参数"。指标记录不依赖此开关（指标仅按操作类型聚合，不含 SQL 文本）。
     */
    private boolean logParameters = false;

    /**
     * 是否记录查询结果集大小
     */
    private boolean logResultSetSize = false;

    /**
     * 是否启用Prometheus指标收集
     */
    private boolean prometheusEnabled = true;

    /**
     * 是否启用ELK日志记录
     */
    private boolean elkEnabled = true;

    /**
     * 最大SQL长度（超过此长度将被截断）
     */
    private int maxSqlLength = 2000;

    /**
     * 是否记录堆栈跟踪信息
     */
    private boolean includeStackTrace = false;

    /**
     * 排除的SQL模式（正则表达式）
     */
    private String[] excludePatterns = {
        ".*INFORMATION_SCHEMA.*",
        ".*performance_schema.*",
        ".*mysql.*"
    };

    /**
     * 包含的SQL模式（正则表达式，为空表示包含所有）
     */
    private String[] includePatterns = {};
}