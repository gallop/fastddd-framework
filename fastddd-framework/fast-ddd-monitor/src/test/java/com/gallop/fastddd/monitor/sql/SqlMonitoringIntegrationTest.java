package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SQL 监控真实链路闭环集成测试（MON-002 验收①②）。
 *
 * <p>验收①（真实 SQL 产生 Micrometer 指标）：经代理 DataSource 执行真实 H2 SQL，
 * 断言 {@code sql.queries.total} 等 gauge 增长、{@code sql.execution.time} timer 记录。
 *
 * <p>验收②（未暴露 Endpoint 不可访问）：默认 Actuator exposure 仅 health，故
 * {@code GET /actuator/sqlmonitoring} 返回 404。
 *
 * <p>对照场景（暴露后可访问）见 {@link SqlMonitoringExposureEnabledTest}。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SqlMonitoringIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    MockMvc mockMvc;

    /**
     * 验收①：真实 SQL 经代理 → Micrometer 指标增长。
     */
    @Test
    void realSql_producesMicrometerMetrics() throws Exception {
        double before = gaugeValue("sql.queries.total");

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS mon002_t (id INT, name VARCHAR(64))");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mon002_t(id, name) VALUES(?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "alice");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM mon002_t WHERE name = ?")) {
                ps.setString(1, "alice");
                ps.executeQuery();
            }
        }

        double after = gaugeValue("sql.queries.total");
        assertThat(after)
                .as("真实 SQL 执行后 sql.queries.total 应增长（CREATE + INSERT + SELECT 至少 3）")
                .isGreaterThanOrEqualTo(before + 3);
        assertThat(meterRegistry.find("sql.execution.time").timer())
                .as("sql.execution.time Timer 应被记录")
                .isNotNull();
        assertThat(meterRegistry.find("sql.operations.total").counter())
                .as("sql.operations.total Counter 应被记录")
                .isNotNull();
    }

    /**
     * 验收②：默认未暴露 → 404。
     */
    @Test
    void endpointNotAccessible_whenNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/sqlmonitoring"))
                .andExpect(status().isNotFound());
    }

    private double gaugeValue(String name) {
        return meterRegistry.find(name).gauge() != null
                ? meterRegistry.find(name).gauge().value()
                : 0.0;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}

/**
 * 对照场景：显式 expose=sqlmonitoring → 200 可访问。
 *
 * <p>独立类以承载不同的 exposure 属性（{@code management.endpoints.web.exposure.include}），
 * 与 {@link SqlMonitoringIntegrationTest} 的默认 exposure 上下文隔离。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=sqlmonitoring"
})
class SqlMonitoringExposureEnabledTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DataSource dataSource;

    @Test
    void endpointAccessible_whenExposed() throws Exception {
        // 先触发一次真实 SQL，使 statistics 非空
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
        }
        mockMvc.perform(get("/actuator/sqlmonitoring"))
                .andExpect(status().isOk());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ExposureApp {
    }
}
