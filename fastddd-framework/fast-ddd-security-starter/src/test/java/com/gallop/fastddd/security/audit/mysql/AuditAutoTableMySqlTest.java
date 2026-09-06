package com.gallop.fastddd.security.audit.mysql;

import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志真实 MySQL 8 空库自动建表验证（ECO-006C3 专项①，ECO-006C4 收编入仓）。
 *
 * <p>专用一次性 Testcontainers 容器（mysql:8.0，随机名/随机端口，ryuk 自动回收），
 * 数据库为空、无 schema.sql、无 spring.sql.init——sys_audit_log 只能由框架
 * AuditLogServiceImpl#ensureTableExists 自动创建。</p>
 *
 * <p>验证：真实 @AuditLog HTTP 入口后，表存在、method 列宽为修正后的 200、
 * 完整方法标识（&gt;10 字符，远超旧列宽）入库不截断。</p>
 *
 * <p><b>环境门禁</b>：仅当系统属性 {@code -Dfastddd.test.docker=true} 时启用——与
 * fast-ddd-event 的 {@code OutboxEventMysqlTestcontainersTest} 同一机制；本仓库 CI
 * 无 Docker runner，默认休眠，不影响标准 {@code clean verify}。专项运行（记账时须与
 * 默认跳过分开记录）：
 * <pre>{@code
 * mvn -B test -pl fastddd-framework/fast-ddd-security-starter \
 *     -Dtest=AuditAutoTableMySqlTest -Dfastddd.test.docker=true
 * }</pre></p>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Testcontainers
@SpringBootTest(classes = AuditAutoTableMySqlTest.App.class)
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.docker", matches = "true",
        disabledReason = "需 Docker（-Dfastddd.test.docker=true 显式开启）；CI 无 runner，详见类 Javadoc")
class AuditAutoTableMySqlTest {

    private static final String JWT_SECRET =
            "fastddd-mysql-audit-it-jwt-secret-not-for-production-0000000000000";

    /** 专用一次性容器凭据，容器随测试结束回收，不代表任何真实环境凭据。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("audit_auto")
            .withUsername("fastddd_it")
            .withPassword("fastddd-it-one-time");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("fastddd.security.jwt.secret", () -> JWT_SECRET);
    }

    @SpringBootApplication
    @Import(ProbeController.class)
    static class App {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void emptyDatabaseAutoCreatesTableAndPersistsFullMethodIdentifier() throws Exception {
        // 前置：空库无 sys_audit_log（框架建表前）
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log'",
                Integer.class);
        assertThat(before).isZero();

        // 真实 @AuditLog 入口（同步 async=false）：切面 → AuditLogService → ensureTableExists → INSERT
        mockMvc.perform(get("/api/auto/audited")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());

        // ① 表已由框架自动创建，method 列宽 = 修正后的 200
        Long width = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'method'",
                Long.class);
        assertThat(width).isEqualTo(200L);

        // ② 完整方法标识入库不截断（"ProbeController.auditedAuto" = 26 字符 > 旧列宽 10）
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT method, operation, username FROM sys_audit_log WHERE operation = ?",
                "audit-auto-create-probe");
        assertThat(row.get("method")).isEqualTo("ProbeController.auditedAuto");
        assertThat(row.get("username")).isEqualTo("consumer");
    }

    private String userToken() {
        return jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    record ProbeResponse(String message) {
    }

    @RestController
    static class ProbeController {

        /** 同步审计：保证 MockMvc 返回时 INSERT 已完成，断言无需等待。 */
        @AuditLog(value = "audit-auto-create-probe", type = AuditLog.OperationType.SELECT, async = false)
        @GetMapping("/api/auto/audited")
        public ProbeResponse auditedAuto() {
            return new ProbeResponse("audited auto ok");
        }
    }
}
