package com.gallop.fastddd.consumer.security.withdb;

import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.audit.AuditLogService;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ECO-006C2：Security 有数据库消费者（H2 兼容回归，不是新的真实 MySQL 证据）。
 *
 * <p>security-starter 依赖整改（MP starter → mybatis-plus-annotation + spring-jdbc）后，
 * 有库消费者显式提供 {@code spring-boot-starter-jdbc}（池实现）+ 数据库驱动。验证：</p>
 * <ol>
 *   <li><b>装配</b>：DataSource 与 JdbcTemplate Bean 存在（spring-jdbc 现由 security-starter
 *       直接传递，Boot JdbcTemplateAutoConfiguration 因唯一 DataSource 候选而激活），
 *       审计服务为框架默认实现。</li>
 *   <li><b>落库</b>：经真实审计入口——带 token 的 HTTP 请求 → @AuditLog 切面 →
 *       AuditLogService → JdbcTemplate INSERT——后，sys_audit_log 表中确实出现该条记录
 *       （按 operation/username/success/request_url 断言，不只验证 Bean 或启动）。</li>
 * </ol>
 */
@SpringBootTest(classes = SecurityWithDatabaseConsumerTest.App.class)
@AutoConfigureMockMvc
class SecurityWithDatabaseConsumerTest {

    @SpringBootApplication
    @Import(AuditProbeController.class)
    static class App {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void dataSourceBackedAuditWiringIsAssembled() {
        assertThat(context.getBeansOfType(DataSource.class)).isNotEmpty();
        // JdbcTemplate Bean 由 Boot 自动配置基于 DataSource 创建——有库落库路径的前提
        assertThat(context.getBeansOfType(JdbcTemplate.class)).isNotEmpty();
        // @Async 使 Bean 以 AOP 代理暴露——按目标类断言默认实现未被覆盖
        assertThat(AopUtils.getTargetClass(auditLogService).getSimpleName())
                .isEqualTo("AuditLogServiceImpl");
    }

    @Test
    void auditEntryThroughRealEndpointPersistsRow() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/probe/audited")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 真实审计入口产生的记录确实入表（消费者侧直查 sys_audit_log）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, operation, success, request_url FROM sys_audit_log WHERE operation = ?",
                "db-audit-probe");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("username")).isEqualTo("consumer");
        assertThat(rows.get(0).get("operation")).isEqualTo("db-audit-probe");
        assertThat(rows.get(0).get("success")).isEqualTo(Boolean.TRUE);
        assertThat(rows.get(0).get("request_url")).isEqualTo("/api/probe/audited");
    }

    record ProbeResponse(String message) {
    }

    @RestController
    static class AuditProbeController {

        /** 同步审计（async=false）：请求返回时 INSERT 已执行，断言无需等待异步线程。 */
        @AuditLog(value = "db-audit-probe", type = AuditLog.OperationType.SELECT, async = false)
        @GetMapping("/api/probe/audited")
        public ProbeResponse audited() {
            return new ProbeResponse("audited ok");
        }
    }
}
