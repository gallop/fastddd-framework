package com.gallop.fastddd.consumer.monitor;

import com.gallop.fastddd.monitor.sql.SqlMonitoringDataSourcePostProcessor;
import com.gallop.fastddd.monitor.sql.SqlMonitoringEndpoint;
import com.gallop.fastddd.monitor.sql.SqlMonitoringProperties;
import com.gallop.fastddd.monitor.sql.SqlMonitoringProxied;
import com.gallop.fastddd.monitor.sql.SqlMonitoringService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fast-ddd-monitor 消费者验收（MON-001 回归 + MON-002 真实链路闭环）。
 *
 * <p>MON-001 回归（P0-4）：无 NoUnique 启动 + SqlMonitoringProperties 单一注册。
 *
 * <p>MON-002 真实链路：
 * <ul>
 *   <li>主 {@link DataSource} 被 {@link SqlMonitoringDataSourcePostProcessor} 包装为
 *       {@link SqlMonitoringProxied} 代理；</li>
 *   <li>经该 DataSource 执行真实 H2 SQL 后，{@code sql.queries.total} Micrometer 指标增长
 *       ——证明"真实 SQL 能产生 Micrometer 指标"；</li>
 *   <li>{@link SqlMonitoringEndpoint}（真正 Actuator Endpoint）bean 装配，由 exposure 体系治理。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = MonitorConsumerTest.App.class)
class MonitorConsumerTest {

    @Autowired
    SqlMonitoringService sqlMonitoringService;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    DataSource dataSource;

    @Autowired
    MeterRegistry meterRegistry;

    @SpringBootApplication
    static class App {
    }

    /**
     * ① P0-4 回归：无 NoUnique，上下文启动成功。
     */
    @Test
    void contextLoadsWithoutNoUniqueException() {
        assertNotNull(sqlMonitoringService, "SqlMonitoringService 应装配可用");
    }

    /**
     * ② SqlMonitoringProperties 仅注册一次。
     */
    @Test
    void singleSqlMonitoringPropertiesBean() {
        String[] names = applicationContext.getBeanNamesForType(SqlMonitoringProperties.class);
        assertEquals(1, names.length,
                "SqlMonitoringProperties 应只注册一个 bean，实际: " + java.util.Arrays.toString(names));
    }

    /**
     * ③ 主 DataSource 已被监控代理包装。
     */
    @Test
    void primaryDataSourceIsProxied() {
        assertTrue(dataSource instanceof SqlMonitoringProxied,
                "主 DataSource 应被 SqlMonitoringDataSourcePostProcessor 包装为代理");
        assertNotNull(applicationContext.getBean(SqlMonitoringDataSourcePostProcessor.class));
    }

    /**
     * ④ MON-002 验收①：真实 SQL 产生 Micrometer 指标。
     */
    @Test
    void realSql_producesMicrometerMetrics() throws Exception {
        double before = gaugeValue("sql.queries.total");
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS c_t (id INT, name VARCHAR(32))");
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO c_t(id,name) VALUES(?,?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "n");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM c_t WHERE name=?")) {
                ps.setString(1, "n");
                ps.executeQuery();
            }
        }
        double after = gaugeValue("sql.queries.total");
        assertTrue(after >= before + 3,
                "真实 SQL 后 sql.queries.total 应至少 +3，before=" + before + " after=" + after);
    }

    /**
     * ⑤ 真正 Actuator Endpoint bean 装配（取代伪 Controller）。
     */
    @Test
    void sqlMonitoringEndpointBeanPresent() {
        assertNotNull(applicationContext.getBean(SqlMonitoringEndpoint.class),
                "SqlMonitoringEndpoint（真正 Actuator @Endpoint）应装配");
    }

    private double gaugeValue(String name) {
        return meterRegistry.find(name).gauge() != null
                ? meterRegistry.find(name).gauge().value()
                : 0.0;
    }
}
