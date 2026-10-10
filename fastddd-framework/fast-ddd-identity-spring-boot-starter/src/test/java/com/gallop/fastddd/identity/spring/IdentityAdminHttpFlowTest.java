package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import com.gallop.fastddd.identity.spring.security.IdentitySessionAuthenticationFilter;
import com.gallop.fastddd.identity.spring.web.IdentityAdminEndpoints;
import com.gallop.fastddd.identity.spring.web.IdentityLoginCookies;
import com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W3-c 管理端点 HTTP 级测试（W3 设计 v3 §8 矩阵 19/20/21，真实 PG + 真实 C 链 +
 * MockMvc）：非管理员 403 零状态变化（19）、管理员 200 + T4 停用语义（DISABLED +
 * epoch+1 + 全会话撤销）+ sys_audit_log 操作人行（20）、匿名 401（21）；外加
 * 404（目标不存在）与 400（principalId 协议外）。测试桩（提供者/准入/管理策略）
 * 仅存在于本夹具——策略以 {@link #ADMIN_PRINCIPAL_IDS} 白名单模拟逐操作裁决。
 * 另含控制器级认证在场防线（§9.3 双链等效第二层）：宿主链放行管理路径的形态下，
 * ActorResolver 抛 IdentityRequiredException → 401 identity_unauthorized（复评观察 1
 * 补测试，行为先经复评探针 A 验证）。
 */
class IdentityAdminHttpFlowTest {

    /** 管理员主体白名单（策略桩判据；登录后由测试回填 principalId——逐操作裁决语义）。 */
    static final Set<String> ADMIN_PRINCIPAL_IDS = ConcurrentHashMap.newKeySet();

    /** 策略桩被咨询次数（防线测试断言"策略零触碰"用；其余测试不断言其值）。 */
    static final java.util.concurrent.atomic.AtomicInteger POLICY_CONSULTATIONS =
            new java.util.concurrent.atomic.AtomicInteger();

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityLoginAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class))
            .withUserConfiguration(AdminFlowApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.identity.admin.enabled=true",
                    "fastddd.identity.login.session.cookie-secure=false",
                    "fastddd.identity.login.allowed-post-login-redirects=/,/home",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    /** 矩阵 22：同应用配置 + 宿主链复刻 + host-owned=true（结果须与 19–21 全等）。 */
    private final WebApplicationContextRunner hostOwnedRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityLoginAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class))
            .withUserConfiguration(AdminFlowApp.class, HostOwnedChainApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.identity.admin.enabled=true",
                    "fastddd.identity.security.host-owned=true",
                    "fastddd.identity.login.session.cookie-secure=false",
                    "fastddd.identity.login.allowed-post-login-redirects=/,/home",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    /**
     * 防线形态（§9.3 第二层）：宿主链把管理路径 permitAll（信任框架控制器自守）——
     * 请求过链但无认证在场，控制器级检查必须独立于链给出 401。host-owned=true 使
     * 默认链退让，本链成为唯一链。
     */
    private final WebApplicationContextRunner permissiveRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityLoginAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class))
            .withUserConfiguration(AdminFlowApp.class, PermitAllAdminChainApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.identity.admin.enabled=true",
                    "fastddd.identity.security.host-owned=true",
                    "fastddd.identity.login.session.cookie-secure=false",
                    "fastddd.identity.login.allowed-post-login-redirects=/,/home",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    /**
     * 宿主链复刻（§9.3 双链等效）：与 identity 默认链同构——cookie CSRF 仓 + plain
     * 请求处理器、STATELESS、仅两 GET 登录路径 permitAll、anyRequest authenticated、
     * 401 entryPoint、会话过滤器前置。默认链以 @CoM 退让（validator 终检互证声明）。
     */
    @Configuration(proxyBeanMethods = false)
    static class HostOwnedChainApp {

        @Bean
        SecurityFilterChain hostSecurityFilterChain(HttpSecurity http,
                                                    IdentitySessionAuthenticationFilter sessionFilter)
                throws Exception {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                                    IdentityLoginEndpoints.AUTHORIZE_PATH),
                                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                                            IdentityLoginEndpoints.CALLBACK_PATH))
                            .permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(handler -> handler.authenticationEntryPoint(
                            (request, response, authException) -> response.sendError(401)))
                    .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    /**
     * 防线形态宿主链：管理两路径 permitAll（其余 authenticated）——认证在场防线
     * 只能由控制器层承载（§9.3 双链等效第二层的可观测形态）。
     */
    @Configuration(proxyBeanMethods = false)
    static class PermitAllAdminChainApp {

        @Bean
        SecurityFilterChain permitAllAdminChain(HttpSecurity http,
                                                IdentitySessionAuthenticationFilter sessionFilter)
                throws Exception {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
                                    IdentityAdminEndpoints.DEACTIVATE_PATH),
                                    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
                                            IdentityAdminEndpoints.REACTIVATE_PATH))
                            .permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(handler -> handler.authenticationEntryPoint(
                            (request, response, authException) -> response.sendError(401)))
                    .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AdminFlowApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        /** 测试桩提供者：code→主体（admin-code=管理员、emp-code=普通员工、target-code=停用目标）。 */
        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return command -> {
                String code = command.credential("code");
                String externalId = "admin-code".equals(code) ? "ou_admin"
                        : "emp-code".equals(code) ? "ou_employee" : "ou_target";
                return new VerifiedExternalLogin(
                        new ExternalIdentity("feishu-stub", "tenant-http", "app-http", "union_id", externalId),
                        "login-" + externalId, "HTTP " + externalId);
            };
        }

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        /** 管理策略桩：仅白名单内主体放行，其余逐操作拒绝（生产宿主自带策略）。 */
        @Bean
        com.gallop.fastddd.identity.core.port.IdentityAdminPolicy identityAdminPolicy() {
            return (operation, actor, targetPrincipalId) -> {
                POLICY_CONSULTATIONS.incrementAndGet();
                if (!ADMIN_PRINCIPAL_IDS.contains(actor.principalId().value())) {
                    throw new IdentityAdminDeniedException(operation.name(),
                            targetPrincipalId == null ? null : targetPrincipalId.value(),
                            "非管理员（测试策略桩）");
                }
            };
        }

        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp-stub.example/authorize?state=" + state
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256";
        }

        /** 受保护资源探测端点（会话撤销后立即失效的判据）。 */
        @RestController
        static class MeController {
            @GetMapping("/api/me")
            String me() {
                return "authenticated";
            }
        }
    }

    @BeforeEach
    void cleanDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());
        jdbc.execute("TRUNCATE TABLE sys_audit_log");
        jdbc.execute("TRUNCATE TABLE login_state");
        jdbc.execute("TRUNCATE TABLE local_session, external_binding, principal CASCADE");
        // W5-e：T4 停用经门禁写 external_rejection（reactivate 不自动清）——
        // 跨方法残留会使后续 login 撞 T2 门禁（M-31⑦ 语义，非缺陷），必须随批清理
        jdbc.execute("TRUNCATE TABLE external_rejection");
        ADMIN_PRINCIPAL_IDS.clear();
        POLICY_CONSULTATIONS.set(0);
    }

    // ------------------------------------------------------------- 矩阵 21

    @Test
    void matrix21_anonymousPostIsUnauthorized() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 默认链 anyRequest().authenticated()：匿名（带 CSRF）→ 401，零审计
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, "00000000-0000-0000-0000-000000000000")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
            assertThat(auditCount()).isZero();
        });
    }

    // ------------------------------------------------------------- 矩阵 19

    @Test
    void matrix19_nonAdminEmployeeDeniedWithZeroStateChange() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            LoggedIn admin = login(mvc, "admin-code");
            LoggedIn employee = login(mvc, "emp-code");
            ADMIN_PRINCIPAL_IDS.add(admin.principalId());
            long epochBefore = authEpoch(jdbc, admin.principalId());

            // 普通员工对管理员发起停用 → 403 identity_admin_denied（逐操作裁决）
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, admin.principalId())
                            .cookie(employee.session()).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("identity_admin_denied"))
                    .andExpect(jsonPath("$.operation").value("DEACTIVATE"));

            // 零状态变化：目标主体/epoch/会话全不动、零审计行
            assertThat(principalStatus(jdbc, admin.principalId())).isEqualTo("ACTIVE");
            assertThat(authEpoch(jdbc, admin.principalId())).isEqualTo(epochBefore);
            assertThat(activeSessions(jdbc, admin.principalId())).isEqualTo(1);
            assertThat(auditCount()).isZero();
        });
    }

    // ------------------------------------------------------------- 矩阵 20

    @Test
    void matrix20_adminDeactivatesTargetWithAuditEpochAndSessionRevocationThenReactivates() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            LoggedIn admin = login(mvc, "admin-code");
            LoggedIn target = login(mvc, "target-code");
            ADMIN_PRINCIPAL_IDS.add(admin.principalId());
            long epoch0 = authEpoch(jdbc, target.principalId());

            // 管理员停用目标 → 200 + 响应体语义（DISABLED + epoch+1）
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, target.principalId())
                            .cookie(admin.session()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.principalId").value(target.principalId()))
                    .andExpect(jsonPath("$.status").value("DISABLED"))
                    .andExpect(jsonPath("$.authEpoch").value(String.valueOf(epoch0 + 1)));

            // T4 同事务语义：DISABLED + epoch+1 + 全会话撤销（旧 cookie 立即失效）
            assertThat(principalStatus(jdbc, target.principalId())).isEqualTo("DISABLED");
            assertThat(authEpoch(jdbc, target.principalId())).isEqualTo(epoch0 + 1);
            assertThat(activeSessions(jdbc, target.principalId())).isZero();
            mvc.perform(get("/api/me").cookie(target.session())).andExpect(status().isUnauthorized());

            // W5-e M-31⑦：T4 同事务拒绝行生效——停用后再登录被拒（T2 门禁 ⓪①②，
            // callback 401 不创建主体不签发会话；主体已 DISABLED，双重拦截面）
            assertThat(reloginCallbackStatus(mvc, "target-code")).isEqualTo(401);

            // 审计行（成功必写）：操作人=管理员 principalId、稳定操作码、目标主体入参
            java.util.List<java.util.Map<String, Object>> auditRows = jdbc.queryForList(
                    "SELECT username, operation, operation_type, method, request_params, success "
                            + "FROM sys_audit_log ORDER BY create_time");
            assertThat(auditRows).hasSize(1);
            assertThat(auditRows.get(0)).containsEntry("username", admin.principalId())
                    .containsEntry("operation", "identity-admin-deactivate")
                    .containsEntry("operation_type", "IDENTITY")
                    .containsEntry("method", "identity")
                    .containsEntry("request_params", "targetPrincipalId=" + target.principalId())
                    .containsEntry("success", true);

            // 启用（独立裁决的逆操作）→ ACTIVE + epoch 再 +1 + 第二条审计行
            mvc.perform(post(IdentityAdminEndpoints.REACTIVATE_PATH, target.principalId())
                            .cookie(admin.session()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.authEpoch").value(String.valueOf(epoch0 + 2)));
            assertThat(principalStatus(jdbc, target.principalId())).isEqualTo("ACTIVE");
            assertThat(authEpoch(jdbc, target.principalId())).isEqualTo(epoch0 + 2);
            // W5-e 裁决可观察面：reactivate 不自动清拒绝行（行可能源自权威确证，
            // 静默清除削弱可追溯性）——恢复登录须管理端 clearControlled 两显式操作
            assertThat(reloginCallbackStatus(mvc, "target-code")).isEqualTo(401);
            java.util.List<String> operations = jdbc.queryForList(
                    "SELECT operation FROM sys_audit_log ORDER BY create_time", String.class);
            assertThat(operations).containsExactly("identity-admin-deactivate", "identity-admin-reactivate");
        });
    }

    // ------------------------------------------------------ 404 / 400 负路径

    @Test
    void unknownTargetGives404AndMalformedIdGives400() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            LoggedIn admin = login(mvc, "admin-code");
            ADMIN_PRINCIPAL_IDS.add(admin.principalId());

            // 目标不存在 → 404 identity_principal_not_found（策略通过后才执行，零审计）
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, "00000000-0000-0000-0000-000000000000")
                            .cookie(admin.session()).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("identity_principal_not_found"));

            // principalId 协议外（超长 51）→ 400 identity_invalid_principal_id（先于策略）
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, "x".repeat(51))
                            .cookie(admin.session()).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("identity_invalid_principal_id"));

            assertThat(auditCount()).isZero();
        });
    }

    // ------------------------------------------------------------- 矩阵 22

    /** 宿主自定义链下复验 19–21（链无关防线；单上下文顺序执行以控制启动成本）。 */
    @Test
    void matrix22_hostOwnedChainRepeatsOutcomesOf19To21() throws Exception {
        hostOwnedRunner.run(context -> {
            assertThat(context).hasNotFailed();
            // 宿主链承接：默认链退让（@CoM），validator 终检互证 host-owned 声明
            assertThat(context).hasBean("hostSecurityFilterChain");
            assertThat(context).doesNotHaveBean("identitySecurityFilterChain");
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            LoggedIn admin = login(mvc, "admin-code");
            LoggedIn employee = login(mvc, "emp-code");
            LoggedIn target = login(mvc, "target-code");
            ADMIN_PRINCIPAL_IDS.add(admin.principalId());
            long epoch0 = authEpoch(jdbc, target.principalId());

            // 21 复验：匿名（带 CSRF）→ 401，零审计
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, target.principalId())
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
            assertThat(auditCount()).isZero();

            // 19 复验：普通员工 → 403 identity_admin_denied，目标零状态变化
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, target.principalId())
                            .cookie(employee.session()).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("identity_admin_denied"));
            assertThat(principalStatus(jdbc, target.principalId())).isEqualTo("ACTIVE");
            assertThat(authEpoch(jdbc, target.principalId())).isEqualTo(epoch0);
            assertThat(activeSessions(jdbc, target.principalId())).isEqualTo(1);

            // 20 复验：管理员 → 200 + DISABLED + epoch+1 + 全会话撤销 + 审计行
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, target.principalId())
                            .cookie(admin.session()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"))
                    .andExpect(jsonPath("$.authEpoch").value(String.valueOf(epoch0 + 1)));
            assertThat(principalStatus(jdbc, target.principalId())).isEqualTo("DISABLED");
            assertThat(activeSessions(jdbc, target.principalId())).isZero();
            mvc.perform(get("/api/me").cookie(target.session())).andExpect(status().isUnauthorized());
            java.util.List<String> operations = jdbc.queryForList(
                    "SELECT operation FROM sys_audit_log", String.class);
            assertThat(operations).containsExactly("identity-admin-deactivate");
        });
    }

    // ---------------------------------------- 控制器级认证在场防线（§9.3 第二层）

    /**
     * 宿主链放行管理路径（permitAll）+ 无认证在场：控制器级检查独立于链给出
     * 401 identity_unauthorized（响应体为准——链层 401 是 sendError 无 JSON 体，
     * 两形态以此区分）；策略/执行/审计零触碰（复评观察 1 的正式测试化）。
     */
    @Test
    void controllerLevelAuthPresenceGuardReturns401WhenChainPermits() throws Exception {
        permissiveRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("permitAllAdminChain");
            assertThat(context).doesNotHaveBean("identitySecurityFilterChain");
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 过链（permitAll）但无会话 → 控制器 ① 认证在场检查接管：401 + 错误体
            mvc.perform(post(IdentityAdminEndpoints.DEACTIVATE_PATH, "00000000-0000-0000-0000-000000000000")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("identity_unauthorized"));

            // 零触碰：策略未被咨询、零审计行；执行未达（否则 404 而非 401，上断言已锁定）
            assertThat(POLICY_CONSULTATIONS.get()).isZero();
            assertThat(auditCount()).isZero();
        });
    }

    // ------------------------------------------------------------- 助手

    private record LoggedIn(String principalId, Cookie session) {
    }

    /** 登录闭环（发起→回调 code→会话 Cookie），并回查 principalId。 */
    private static LoggedIn login(MockMvc mvc, String code) throws Exception {
        org.springframework.test.web.servlet.MvcResult authorize = mvc
                .perform(get(IdentityLoginEndpoints.AUTHORIZE_PATH))
                .andExpect(status().isFound())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        String state = Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                .findFirst().orElseThrow();
        Cookie binding = cookieOf(authorize, IdentityLoginCookies.BINDING_COOKIE_NAME);
        org.springframework.test.web.servlet.MvcResult callback = mvc
                .perform(get(IdentityLoginEndpoints.CALLBACK_PATH)
                        .queryParam("code", code).queryParam("state", state)
                        .cookie(binding))
                .andExpect(status().isFound())
                .andReturn();
        Cookie session = cookieOf(callback, "FASTDDD_SESSION");
        assertThat(session).as("登录成功应下发会话 Cookie（code=" + code + "）").isNotNull();
        String principalId = new JdbcTemplate(PgTestSupport.dataSource()).queryForObject(
                "SELECT p.principal_id FROM local_session s "
                        + "JOIN principal p ON p.principal_id = s.principal_id WHERE s.session_id = ?",
                String.class, session.getValue());
        return new LoggedIn(principalId, session);
    }

    /**
     * W5-e M-31⑦ 断言支撑：重走登录闭环（发起→回调），返回 callback HTTP 状态码
     * ——T2 门禁命中（活跃拒绝行）时 callback 401（不签发会话）。
     */
    private static int reloginCallbackStatus(MockMvc mvc, String code) throws Exception {
        org.springframework.test.web.servlet.MvcResult authorize = mvc
                .perform(get(IdentityLoginEndpoints.AUTHORIZE_PATH))
                .andExpect(status().isFound())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        String state = Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                .findFirst().orElseThrow();
        Cookie binding = cookieOf(authorize, IdentityLoginCookies.BINDING_COOKIE_NAME);
        return mvc.perform(get(IdentityLoginEndpoints.CALLBACK_PATH)
                        .queryParam("code", code).queryParam("state", state)
                        .cookie(binding))
                .andReturn().getResponse().getStatus();
    }

    private static Cookie cookieOf(org.springframework.test.web.servlet.MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        return null;
    }

    private static int auditCount() {
        return new JdbcTemplate(PgTestSupport.dataSource())
                .queryForObject("SELECT count(*) FROM sys_audit_log", Integer.class);
    }

    private static String principalStatus(JdbcTemplate jdbc, String principalId) {
        return jdbc.queryForObject("SELECT status FROM principal WHERE principal_id = ?",
                String.class, principalId);
    }

    private static long authEpoch(JdbcTemplate jdbc, String principalId) {
        return jdbc.queryForObject("SELECT auth_epoch FROM principal WHERE principal_id = ?",
                Long.class, principalId);
    }

    /** 未撤销会话数（T4 撤销 = revoked_at 置位，行保留）。 */
    private static int activeSessions(JdbcTemplate jdbc, String principalId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM local_session WHERE principal_id = ? AND revoked_at IS NULL",
                Integer.class, principalId);
    }
}
