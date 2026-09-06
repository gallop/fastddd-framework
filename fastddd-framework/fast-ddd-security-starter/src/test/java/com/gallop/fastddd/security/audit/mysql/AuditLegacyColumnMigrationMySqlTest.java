package com.gallop.fastddd.security.audit.mysql;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
 * 审计日志旧窄列（method VARCHAR(10)）复现 + 扩宽迁移演练（ECO-006C3 专项②，
 * ECO-006C4 收编入仓）。专用一次性 MySQL 8 容器 + 独立库 audit_legacy，用后回收。
 *
 * <p>@BeforeAll 按旧版框架 MySqlDialect 精确复刻 method VARCHAR(10) 的存量表并预写
 * 一行历史数据（短标识可入库）。四阶段验证（单测试方法保证时序）：</p>
 * <ol>
 *   <li><b>复现</b>：真实 @AuditLog 入口（方法标识 37 字符）在 MySQL 8 默认严格模式下
 *       INSERT 失败（Data too long for column 'method'）→ 按既有契约回退 SLF4J
 *       （WARN "审计日志入库失败"），业务响应不受影响（200），表内仍只有历史行；</li>
 *   <li><b>迁移</b>：执行 docs/security/AUDIT_LOG_TABLE_MIGRATION.md 的
 *       ALTER TABLE sys_audit_log MODIFY COLUMN method VARCHAR(200)（仅本测试库演练，
 *       不代表对任何业务库执行；真实迁移前须按该文档核对实际列属性、容量与执行影响）；</li>
 *   <li><b>历史保留</b>：迁移后历史行原样存在（非破坏性扩宽）；</li>
 *   <li><b>新记录成功</b>：再次真实入口 → 新行完整方法标识入库，表内共 2 行。</li>
 * </ol>
 *
 * <p><b>环境门禁</b>：仅当系统属性 {@code -Dfastddd.test.docker=true} 时启用——与
 * fast-ddd-event 的 {@code OutboxEventMysqlTestcontainersTest} 同一机制；默认休眠，
 * 不影响标准 {@code clean verify}。专项运行（记账时须与默认跳过分开记录）：
 * <pre>{@code
 * mvn -B test -pl fastddd-framework/fast-ddd-security-starter \
 *     -Dtest=AuditLegacyColumnMigrationMySqlTest -Dfastddd.test.docker=true
 * }</pre></p>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Testcontainers
@SpringBootTest(classes = AuditLegacyColumnMigrationMySqlTest.App.class)
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.docker", matches = "true",
        disabledReason = "需 Docker（-Dfastddd.test.docker=true 显式开启）；CI 无 runner，详见类 Javadoc")
class AuditLegacyColumnMigrationMySqlTest {

    private static final String JWT_SECRET =
            "fastddd-mysql-audit-it-jwt-secret-not-for-production-0000000000000";
    private static final String AUDIT_LOGGER_NAME =
            "com.gallop.fastddd.security.audit.AuditLogServiceImpl";

    /** 专用一次性容器凭据，容器随测试结束回收，不代表任何真实环境凭据。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("audit_legacy")
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

    private final ListAppender<ILoggingEvent> auditLogAppender = new ListAppender<>();

    @BeforeAll
    static void createLegacyNarrowTable() {
        // 旧版框架自动建表 DDL 的精确复刻（MySqlDialect + method VARCHAR(10)）
        try (var conn = MYSQL.createConnection(""); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE sys_audit_log (" +
                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                    "username VARCHAR(50), " +
                    "operation VARCHAR(200), " +
                    "operation_type VARCHAR(50), " +
                    "method VARCHAR(10), " +
                    "request_url VARCHAR(500), " +
                    "request_params TEXT, " +
                    "result TEXT, " +
                    "client_ip VARCHAR(50), " +
                    "user_agent VARCHAR(500), " +
                    "execution_time BIGINT, " +
                    "success TINYINT(1) DEFAULT 1, " +
                    "error_message TEXT, " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            // 一行"历史数据"：短方法标识（9 字符）在旧窄列下可正常入库
            stmt.execute("INSERT INTO sys_audit_log " +
                    "(username, operation, operation_type, method, request_url, execution_time, success, create_time) " +
                    "VALUES ('legacy-user', 'legacy-op', 'SELECT', 'LegacySvc', '/legacy/old', 5, 1, NOW())");
        } catch (Exception e) {
            throw new IllegalStateException("预建旧窄列表失败", e);
        }
    }

    @AfterEach
    void detachAuditAppender() {
        ((Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME)).detachAppender(auditLogAppender);
    }

    @Test
    void narrowColumnReproFailsThenMigrationWidensAndNewRowsPersist() throws Exception {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        auditLogAppender.start();
        auditLogger.addAppender(auditLogAppender);

        // ---- 阶段 1：复现——窄列 + 严格模式下超长方法标识 INSERT 失败并回退 SLF4J ----
        mockMvc.perform(get("/api/legacy/audited")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk()); // 业务响应不受审计失败影响

        Integer countAfterFailedInsert = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log", Integer.class);
        assertThat(countAfterFailedInsert).isEqualTo(1); // 仍只有历史行，未写入新行
        assertThat(auditLogAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getFormattedMessage()).contains("审计日志入库失败");
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                })
                .anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("Audit Log: User[consumer] performed [audit-legacy-repro-probe]"));

        // ---- 阶段 2：迁移——按 docs/security/AUDIT_LOG_TABLE_MIGRATION.md 扩宽（仅测试库演练） ----
        jdbcTemplate.execute("ALTER TABLE sys_audit_log MODIFY COLUMN method VARCHAR(200)");
        Long width = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'method'",
                Long.class);
        assertThat(width).isEqualTo(200L);

        // ---- 阶段 3：历史数据保留（非破坏性扩宽） ----
        Map<String, Object> history = jdbcTemplate.queryForMap(
                "SELECT username, operation, method FROM sys_audit_log WHERE operation = 'legacy-op'");
        assertThat(history.get("username")).isEqualTo("legacy-user");
        assertThat(history.get("method")).isEqualTo("LegacySvc");

        // ---- 阶段 4：迁移后新审计记录以完整方法标识成功入库 ----
        mockMvc.perform(get("/api/legacy/audited-after-migration")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());

        Integer countAfterMigration = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log", Integer.class);
        assertThat(countAfterMigration).isEqualTo(2); // 历史行 + 新行

        Map<String, Object> newRow = jdbcTemplate.queryForMap(
                "SELECT method, operation, username FROM sys_audit_log WHERE operation = ?",
                "audit-legacy-after-migration");
        // "ProbeController.auditedAfterMigration" = 37 字符，完整入库不截断
        assertThat(newRow.get("method")).isEqualTo("ProbeController.auditedAfterMigration");
        assertThat(newRow.get("username")).isEqualTo("consumer");
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

        /** 同步审计（async=false）保证断言时 INSERT/回退已发生。 */
        @AuditLog(value = "audit-legacy-repro-probe", type = AuditLog.OperationType.SELECT, async = false)
        @GetMapping("/api/legacy/audited")
        public ProbeResponse audited() {
            return new ProbeResponse("legacy repro ok");
        }

        @AuditLog(value = "audit-legacy-after-migration", type = AuditLog.OperationType.SELECT, async = false)
        @GetMapping("/api/legacy/audited-after-migration")
        public ProbeResponse auditedAfterMigration() {
            return new ProbeResponse("after migration ok");
        }
    }
}
