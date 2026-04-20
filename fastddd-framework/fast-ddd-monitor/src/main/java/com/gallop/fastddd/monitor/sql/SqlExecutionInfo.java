package com.gallop.fastddd.monitor.sql;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SQL执行信息
 * 
 * @author gallop
 * @since 2025-01-10
 */
@Data
@Builder
public class SqlExecutionInfo {

    /**
     * SQL语句
     */
    private String sql;

    /**
     * 格式化后的SQL语句（带参数）
     */
    private String formattedSql;

    /**
     * SQL参数
     */
    private String parameters;

    /**
     * 执行时间（毫秒）
     */
    private long executionTime;

    /**
     * SQL操作类型（SELECT, INSERT, UPDATE, DELETE等）
     */
    private String operationType;

    /**
     * 数据库连接URL
     */
    private String connectionUrl;

    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;

    /**
     * 是否为慢查询
     */
    private boolean slowQuery;

    /**
     * 影响的行数
     */
    private Integer affectedRows;

    /**
     * 结果集大小
     */
    private Integer resultSetSize;

    /**
     * 异常信息（如果有）
     */
    private String errorMessage;

    /**
     * 执行状态（SUCCESS, ERROR）
     */
    private String status;

    /**
     * MyBatis Mapper方法信息
     */
    private String mapperMethod;

    /**
     * 线程名称
     */
    private String threadName;

    /**
     * 额外的上下文信息
     */
    private Map<String, Object> context;

    /**
     * 获取SQL操作类型
     */
    public static String extractOperationType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) {
            return "SELECT";
        } else if (upperSql.startsWith("INSERT")) {
            return "INSERT";
        } else if (upperSql.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (upperSql.startsWith("DELETE")) {
            return "DELETE";
        } else if (upperSql.startsWith("CREATE")) {
            return "CREATE";
        } else if (upperSql.startsWith("DROP")) {
            return "DROP";
        } else if (upperSql.startsWith("ALTER")) {
            return "ALTER";
        } else if (upperSql.startsWith("TRUNCATE")) {
            return "TRUNCATE";
        } else {
            return "OTHER";
        }
    }
}