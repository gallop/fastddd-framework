package com.gallop.fastddd.consumer.security.nodb;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.gallop.fastddd.security.FastDddSecurityAutoConfiguration;
import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.audit.AuditLogService;
import com.gallop.fastddd.security.jwt.JwtAuthenticationFilter;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ECO-006C2：Security 无数据库消费者。
 *
 * <p>本场景即"仅引入 fast-ddd-security-starter、提供有效安全配置但无数据库"的验收目标：
 * 无 H2、无 {@code spring.datasource.*}、无任何 {@code spring.autoconfigure.exclude}。
 * security-starter 不再传递 mybatis-plus-spring-boot3-starter（其 spring-boot-starter-jdbc/
 * HikariCP 链曾使无库消费者被迫建池、启动失败），改为 mybatis-plus-annotation + spring-jdbc。</p>
 *
 * <p>验证四件事：</p>
 * <ol>
 *   <li><b>无库装配</b>：上下文零 DataSource/JdbcTemplate Bean（Boot 原生 DataSource
 *       自动配置在无池实现且无内嵌库时条件不满足，不再急切建池）。</li>
 *   <li><b>真实默认链</b>：框架默认 SecurityFilterChain 唯一，JwtAuthenticationFilter/
 *       JwtTokenProvider 为候选制品装配的真实 Bean。</li>
 *   <li><b>链行为</b>：MockMvc 走真实默认链——未认证 401、候选 TokenProvider 签发的
 *       token 200、权限不足 403（方法安全由框架 SecurityAutoConfiguration 的
 *       @EnableMethodSecurity 启用）。不使用 @WithMockUser、不自定义安全链、不关闭 filters。</li>
 *   <li><b>无库审计契约</b>：真实 @AuditLog 入口经切面→AuditLogService 后按既有契约回退
 *       SLF4J（AuditLogServiceImpl#logToSlf4j "Audit Log: User[...] performed [...]"）——
 *       这是日志回退行为验证，不是数据库持久化。</li>
 * </ol>
 */
@SpringBootTest(classes = SecurityNoDatabaseConsumerTest.App.class)
@AutoConfigureMockMvc
class SecurityNoDatabaseConsumerTest {

    private static final String AUDIT_LOGGER_NAME = "com.gallop.fastddd.security.audit.AuditLogServiceImpl";

    private final ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> auditLogAppender =
            new ListAppender<>();

    @SpringBootApplication
    @Import(ProbeController.class)
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    /** 候选制品装配出的真实 provider——签发与过滤器验签同一 Bean。 */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditLogService auditLogService;

    @AfterEach
    void detachAuditAppender() {
        ((Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME)).detachAppender(auditLogAppender);
    }

    @Test
    void noDataSourceOrJdbcTemplateBeansExist() {
        // 无库契约核心：不靠排除配置，DataSource 自动配置条件自然不满足
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(JdbcTemplate.class)).isEmpty();
    }

    @Test
    void frameworkDefaultJwtChainIsAssembled() {
        // 消费者未自定义安全链 → 框架默认链必须是唯一链，且 JWT Filter/Provider 为真实 Bean
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtAuthenticationFilter.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtTokenProvider.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FastDddSecurityAutoConfiguration.class)).hasSize(1);
    }

    @Test
    void unauthenticatedRequestReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void validTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/probe/secured")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("secured ok"));
    }

    @Test
    void insufficientPermissionReturns403Envelope() throws Exception {
        // ROLE_USER token 访问 ADMIN 限定端点 → 真实链上方法安全拒绝（JwtAccessDeniedHandler 信封）
        mockMvc.perform(get("/api/probe/admin")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void auditWithoutDatabaseFallsBackToSlf4j() throws Exception {
        // 无库审计既有契约：切面真实入口 → AuditLogService → SLF4J 回退输出（非数据库持久化）
        assertThat(context.getBeansOfType(AuditLogService.class)).hasSize(1);

        Logger auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        auditLogAppender.start();
        auditLogger.addAppender(auditLogAppender);

        mockMvc.perform(get("/api/probe/audited")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());

        List<ch.qos.logback.classic.spi.ILoggingEvent> events = auditLogAppender.list;
        assertThat(events)
                .anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("Audit Log: User[consumer] performed [consumer-audit-probe]"));
    }

    private String userToken() {
        return jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    record ProbeResponse(String message) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/secured")
        public ProbeResponse secured() {
            return new ProbeResponse("secured ok");
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/probe/admin")
        public ProbeResponse admin() {
            return new ProbeResponse("admin ok");
        }

        /** 同步审计（async=false）保证断言时日志事件已产生。 */
        @AuditLog(value = "consumer-audit-probe", type = AuditLog.OperationType.SELECT, async = false)
        @GetMapping("/api/probe/audited")
        public ProbeResponse audited() {
            return new ProbeResponse("audited ok");
        }
    }
}
