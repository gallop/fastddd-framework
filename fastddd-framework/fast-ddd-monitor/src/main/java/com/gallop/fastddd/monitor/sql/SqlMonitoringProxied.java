package com.gallop.fastddd.monitor.sql;

/**
 * 标记接口：标识一个 JDBC 对象已被 SQL 监控代理包装（MON-002）。
 *
 * <p>由 {@link SqlMonitoringJdbcProxy} 创建的 JDK 动态代理实现此接口，
 * 供 {@code SqlMonitoringDataSourcePostProcessor} 判定避免重复包装。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public interface SqlMonitoringProxied {
}
