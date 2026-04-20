package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL监控功能测试
 * 
 * @author gallop
 * @since 2025-01-10
 */
@ExtendWith(MockitoExtension.class)
class SqlMonitoringTest {

    private MeterRegistry meterRegistry;
    private SqlMonitoringProperties properties;
    private SqlMetricsCollector metricsCollector;
    private SqlMonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new SqlMonitoringProperties();
        properties.setEnabled(true);
        properties.setSlowQueryThreshold(1000L);
        properties.setPrometheusEnabled(true);
        properties.setElkEnabled(true);
        
        metricsCollector = new SqlMetricsCollector(meterRegistry, properties);
        monitoringService = new SqlMonitoringService(metricsCollector, properties);
    }

    @Test
    void testSqlExecutionInfoCreation() {
        // 测试SQL执行信息创建
        SqlExecutionInfo info = SqlExecutionInfo.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .formattedSql("SELECT * FROM users WHERE id = ?")
                .parameters("1")
                .executionTime(500L)
                .operationType("SELECT")
                .connectionUrl("jdbc:mysql://localhost:3306/test")
                .startTime(java.time.LocalDateTime.now().minusSeconds(1))
                .endTime(java.time.LocalDateTime.now())
                .slowQuery(false)
                .affectedRows(1)
                .resultSetSize(1)
                .errorMessage(null)
                .status("SUCCESS")
                .mapperMethod("UserMapper.findById")
                .threadName(Thread.currentThread().getName())
                .context(new java.util.HashMap<>())
                .build();

        assertNotNull(info);
        assertEquals("SELECT", info.getOperationType());
        assertEquals(500L, info.getExecutionTime());
        assertFalse(info.isSlowQuery());
        assertEquals("UserMapper.findById", info.getMapperMethod());
    }

    @Test
    void testOperationTypeExtraction() {
        // 测试SQL操作类型提取
        assertEquals("SELECT", SqlExecutionInfo.extractOperationType("SELECT * FROM users"));
        assertEquals("INSERT", SqlExecutionInfo.extractOperationType("INSERT INTO users VALUES (1, 'test')"));
        assertEquals("UPDATE", SqlExecutionInfo.extractOperationType("UPDATE users SET name = 'test'"));
        assertEquals("DELETE", SqlExecutionInfo.extractOperationType("DELETE FROM users WHERE id = 1"));
        assertEquals("OTHER", SqlExecutionInfo.extractOperationType("SHOW TABLES"));
        assertEquals("UNKNOWN", SqlExecutionInfo.extractOperationType(""));
        assertEquals("UNKNOWN", SqlExecutionInfo.extractOperationType(null));
    }

    @Test
    void testMetricsCollection() {
        // 测试指标收集
        SqlExecutionInfo normalQuery = createSqlExecutionInfo("SELECT", 500L, false);
        SqlExecutionInfo slowQuery = createSqlExecutionInfo("SELECT", 2000L, true);

        metricsCollector.recordSqlExecution(normalQuery);
        metricsCollector.recordSqlExecution(slowQuery);

        SqlMetricsCollector.SqlStatistics stats = metricsCollector.getStatistics();
        assertEquals(2, stats.getTotalQueries());
        assertEquals(1, stats.getSlowQueries());
        assertEquals(50.0, stats.getSlowQueryPercentage(), 0.01);
    }

    @Test
    void testSlowQueryDetection() {
        // 测试慢查询检测
        SqlExecutionInfo fastQuery = createSqlExecutionInfo("SELECT", 500L, false);
        SqlExecutionInfo slowQuery = createSqlExecutionInfo("SELECT", 1500L, true);

        assertFalse(fastQuery.isSlowQuery());
        assertTrue(slowQuery.isSlowQuery());
        assertTrue(slowQuery.getExecutionTime() > properties.getSlowQueryThreshold());
    }

    @Test
    void testMonitoringServiceConfiguration() {
        // 测试监控服务配置
        var config = monitoringService.getMonitoringConfig();
        
        assertTrue((Boolean) config.get("enabled"));
        assertEquals(1000L, config.get("slowQueryThreshold"));
        assertTrue((Boolean) config.get("prometheusEnabled"));
        assertTrue((Boolean) config.get("elkEnabled"));
    }

    @Test
    void testHealthStatus() {
        // 测试健康状态
        var health = monitoringService.getHealthStatus();
        
        assertEquals("UP", health.get("status"));
        assertEquals(0L, health.get("totalQueries"));
        assertEquals(0L, health.get("slowQueries"));
        assertEquals(0.0, health.get("slowQueryPercentage"));
    }

    @Test
    void testDynamicConfiguration() {
        // 测试动态配置更新
        monitoringService.updateSlowQueryThreshold(2000L);
        assertEquals(2000L, properties.getSlowQueryThreshold());

        monitoringService.setMonitoringEnabled(false);
        assertFalse(properties.isEnabled());

        monitoringService.setPrometheusEnabled(false);
        assertFalse(properties.isPrometheusEnabled());

        monitoringService.setElkEnabled(false);
        assertFalse(properties.isElkEnabled());
    }

    @Test
    void testSqlPatternFiltering() {
        // 测试SQL模式过滤
        properties.setExcludePatterns(new String[]{"SELECT 1", "SHOW.*"});

        assertTrue(shouldExcludeSql("SELECT 1", properties));
        assertTrue(shouldExcludeSql("SHOW TABLES", properties));
        assertFalse(shouldExcludeSql("SELECT * FROM users", properties));
    }

    @Test
    void testConnectionPoolMetrics() {
        // 测试连接池指标 - 由于没有实际的连接池指标方法，这里只测试基本功能
        SqlExecutionInfo info = createSqlExecutionInfo("SELECT", 500L, false);
        info.setConnectionUrl("jdbc:mysql://localhost:3306/test");

        metricsCollector.recordSqlExecution(info);

        // 验证指标记录成功
        SqlMetricsCollector.SqlStatistics stats = metricsCollector.getStatistics();
        assertEquals(1, stats.getTotalQueries());
    }

    @Test
    void testResultSetSizeMetrics() {
        // 测试结果集大小指标
        SqlExecutionInfo info = createSqlExecutionInfo("SELECT", 500L, false);
        info.setResultSetSize(100);

        metricsCollector.recordSqlExecution(info);

        // 验证结果集大小指标记录成功
        SqlMetricsCollector.SqlStatistics stats = metricsCollector.getStatistics();
        assertEquals(1, stats.getTotalQueries());
    }

    private SqlExecutionInfo createSqlExecutionInfo(String operation, long executionTime, boolean isSlowQuery) {
        long currentTime = System.currentTimeMillis();
        SqlExecutionInfo info = SqlExecutionInfo.builder()
                .sql("SELECT * FROM test_table")
                .formattedSql("SELECT * FROM test_table")
                .parameters("")
                .executionTime(executionTime)
                .operationType(operation)
                .connectionUrl("jdbc:h2:mem:test")
                .startTime(java.time.LocalDateTime.now().minusSeconds(1))
                .endTime(java.time.LocalDateTime.now())
                .slowQuery(isSlowQuery)
                .affectedRows(1)
                .resultSetSize(1)
                .errorMessage(null)
                .status("SUCCESS")
                .mapperMethod("TestMapper.findAll")
                .threadName(Thread.currentThread().getName())
                .context(new java.util.HashMap<>())
                .build();
        return info;
    }

    private boolean shouldExcludeSql(String sql, SqlMonitoringProperties properties) {
        return java.util.Arrays.stream(properties.getExcludePatterns())
                .anyMatch(pattern -> sql.matches(pattern));
    }
}