package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.IdentityLoginCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-2 浏览器登录闭环 HTTP 级测试（真实 PG + reference-pg + 真实 C 链 + MockMvc）：
 * 发起→回调→Cookie→受保护资源→登出的全链；state/绑定/回跳/CSRF/匿名面边界
 * （RV-03/RV-09：回调签发必须经 IdentitySessionCookies 并做链级回归）。
 * 测试桩（authorize 端口/提供者/放行策略）仅存在于本夹具。
 */
class IdentityLoginHttpFlowTest {

    /** 登录闭环共用装配（与 Boot 宿主同构的真实 MVC/Jackson 基础设施，无它则 406）。 */
    private static Class<?>[] identityAutoConfigs() {
        return new Class<?>[] {
                IdentityModeGuardAutoConfiguration.class,
                IdentityAutoConfiguration.class,
                com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                com.gallop.fastddd.identity.spring.web.IdentityLoginAutoConfiguration.class,
                FastDddIdentityPgAutoConfiguration.class,
                org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class};
    }

    private static String[] identityFlowProperties() {
        return new String[] {
                "fastddd.identity.enabled=true",
                "fastddd.identity.login.mode=feishu-web",
                // 本地 HTTP 测试显式放宽 Secure（安全缺省仍为 true，见 IdentitySessionCookieTest）
                "fastddd.identity.login.session.cookie-secure=false",
                "fastddd.identity.login.allowed-post-login-redirects=/,/home",
                "fastddd.security.jwt.enabled=false",
                "fastddd.security.local-auth.enabled=false"};
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(identityAutoConfigs()))
            .withUserConfiguration(LoginApp.class)
            .withPropertyValues(identityFlowProperties());

    private final WebApplicationContextRunner anomalyRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(identityAutoConfigs()))
            .withUserConfiguration(AnomalyLoginApp.class)
            .withPropertyValues(identityFlowProperties());

    @Configuration(proxyBeanMethods = false)
    static class LoginApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        /** 测试桩提供者：code==deny-code → 准入策略拒绝的主体（其余成功）。 */
        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return command -> {
                String code = command.credential("code");
                String externalId = "deny-code".equals(code) ? "ou_denied" : "ou_http_1";
                return new VerifiedExternalLogin(
                        new ExternalIdentity("feishu-stub", "tenant-http", "app-http", "union_id", externalId),
                        "login-" + externalId, "HTTP User");
            };
        }

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
                if ("login-ou_denied".equals(principal.loginName())) {
                    throw new com.gallop.fastddd.identity.core.error.AdmissionDeniedException("测试拒绝");
                }
            };
        }

        /** 测试桩授权端口：回显 state/challenge（真实 URL 构造在 feishu 模块测试）。 */
        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp-stub.example/authorize?state=" + state
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256";
        }

        /** 受保护资源探测端点（配置类嵌套 @RestController 由成员类处理自动注册）。 */
        @RestController
        static class MeController {
            @GetMapping("/api/me")
            String me() {
                return "authenticated";
            }
        }
    }

    /**
     * W22-C1 专用夹具：提供者端口恒抛"上游协议异常"形态的
     * {@link com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException}。
     * starter 不依赖 feishu 模块（v0.2 附件边界），HTTP 形态 → 异常的映射保真度由
     * feishu 模块契约测试证明；此处按端口契约注入，验证编排层后果。
     */
    @Configuration(proxyBeanMethods = false)
    static class AnomalyLoginApp {

        static final java.util.concurrent.atomic.AtomicInteger verifyCalls =
                new java.util.concurrent.atomic.AtomicInteger();

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return command -> {
                verifyCalls.incrementAndGet();
                throw new com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException(
                        "http-business-conflict",
                        "飞书 token 响应业务码为 0 但 HTTP 状态非成功（httpStatus=503），矛盾结果不确定");
            };
        }

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp-stub.example/authorize?state=" + state
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256";
        }
    }

    @BeforeEach
    void cleanDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());
        jdbc.execute("TRUNCATE TABLE login_state");
        jdbc.execute("TRUNCATE TABLE local_session, external_binding, principal CASCADE");
    }

    /** 从响应提取指定 cookie。 */
    private static Cookie cookieOf(org.springframework.test.web.servlet.MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        return null;
    }

    /** 发起授权并返回（state, 绑定 Cookie）。 */
    private record Begun(String state, Cookie binding) {
    }

    private static Begun begin(MockMvc mvc) throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mvc
                .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "https://idp-stub.example/authorize?state=")))
                // W22-RV-09：携带绑定 Cookie 值的 302 禁止中间缓存存储
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        String state = java.util.Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                .findFirst().orElseThrow();
        Cookie binding = cookieOf(result, IdentityLoginCookies.BINDING_COOKIE_NAME);
        assertThat(binding).isNotNull();
        assertThat(binding.isHttpOnly()).isTrue();
        return new Begun(state, binding);
    }

    @Test
    void fullBrowserFlowIssuesSessionCookieAndReachesProtectedResource() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            Begun begun = begin(mvc);
            // 发起响应不包含 verifier（challenge 是派生值，verifier 只在服务端存储）
            String savedVerifier = new JdbcTemplate(PgTestSupport.dataSource()).queryForObject(
                    "SELECT code_verifier FROM login_state WHERE state = ?", String.class, begun.state);
            assertThat(savedVerifier).hasSize(86);

            org.springframework.test.web.servlet.MvcResult callback = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", begun.state)
                            .cookie(begun.binding()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/"))
                    // W22-RV-09：携带会话 Cookie 值的成功 302 同样禁缓存
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();

            Cookie session = cookieOf(callback, "FASTDDD_SESSION");
            assertThat(session).isNotNull();
            assertThat(session.isHttpOnly()).isTrue();
            assertThat(session.getPath()).isEqualTo("/");
            // 绑定 Cookie 随回调结束清除
            Cookie bindingCleared = cookieOf(callback, IdentityLoginCookies.BINDING_COOKIE_NAME);
            assertThat(bindingCleared).isNotNull();
            assertThat(bindingCleared.getMaxAge()).isZero();

            // 会话 Cookie 访问受保护资源 → 200；无 Cookie → 401
            mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
            mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

            // 数据库事实：1 主体 / 1 绑定 / 1 会话
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM principal", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isEqualTo(1);

            // 登出（已认证 + CSRF）→ 撤销 + 清除 Cookie；旧会话立即失效
            org.springframework.test.web.servlet.MvcResult logout = mvc
                    .perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.LOGOUT_PATH)
                            .cookie(session).with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie cleared = cookieOf(logout, "FASTDDD_SESSION");
            assertThat(cleared).isNotNull();
            assertThat(cleared.getMaxAge()).isZero();
            assertThat(cleared.getPath()).isEqualTo("/");
            mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        });
    }

    /**
     * 真实浏览器双提交契约（2026-09-27 L3 联调缺陷修复回归）：浏览器只能把
     * XSRF-TOKEN cookie 的<b>裸值</b>原样回传到 X-XSRF-TOKEN header（无法获得
     * SS6 Xor 处理器期望的编码值）。本用例不经 {@code with(csrf())}（服务端取
     * token，恰好绕开真实路径），按裸值回传断言 logout 放行。
     */
    @Test
    void logoutAcceptsRawCookieHeaderDoubleSubmit() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 发起授权：state + 绑定 Cookie + XSRF-TOKEN 种下（浏览器双提交取值的唯一来源）
            org.springframework.test.web.servlet.MvcResult authorize = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH))
                    .andExpect(status().isFound())
                    .andReturn();
            Cookie xsrf = cookieOf(authorize, "XSRF-TOKEN");
            assertThat(xsrf).as("authorize GET 应种下 XSRF-TOKEN cookie").isNotNull();
            String location = authorize.getResponse().getHeader("Location");
            String state = java.util.Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                    .findFirst().orElseThrow();
            Cookie binding = cookieOf(authorize, IdentityLoginCookies.BINDING_COOKIE_NAME);
            assertThat(binding).isNotNull();

            org.springframework.test.web.servlet.MvcResult callback = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", state)
                            .cookie(binding))
                    .andExpect(status().isFound())
                    .andReturn();
            Cookie session = cookieOf(callback, "FASTDDD_SESSION");
            assertThat(session).isNotNull();

            // 裸 cookie 值 → header 原样回传（真实 SPA 路径）→ 放行并撤销会话
            org.springframework.test.web.servlet.MvcResult logout = mvc
                    .perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.LOGOUT_PATH)
                            .cookie(session)
                            .cookie(new Cookie("XSRF-TOKEN", xsrf.getValue()))
                            .header("X-XSRF-TOKEN", xsrf.getValue()))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie cleared = cookieOf(logout, "FASTDDD_SESSION");
            assertThat(cleared).isNotNull();
            assertThat(cleared.getMaxAge()).isZero();
            assertThat(cleared.getPath()).isEqualTo("/");
            mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        });
    }

    @Test
    void stateReplayAndBindingMismatchAreRejectedWithoutSession() throws Exception {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            // 绑定不匹配：401 且零会话（state 已烧毁）
            Begun mismatch = begin(mvc);
            Cookie forged = new Cookie(IdentityLoginCookies.BINDING_COOKIE_NAME, "forged-binding-value");
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", mismatch.state()).cookie(forged))
                    .andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isZero();

            // 重放：成功回调一次后，同 state/绑定再回调 → 401，会话不增
            Begun begun = begin(mvc);
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", begun.state())
                            .cookie(begun.binding()))
                    .andExpect(status().isFound());
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", begun.state())
                            .cookie(begun.binding()))
                    .andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isEqualTo(1);
        });
    }

    @Test
    void admissionDeniedLeavesZeroResidueAndErrorCallbackRejected() throws Exception {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            // 宿主准入拒绝（T2 内回滚）：401，主体/绑定/会话零残留
            Begun denied = begin(mvc);
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "deny-code").queryParam("state", denied.state())
                            .cookie(denied.binding()))
                    .andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM principal", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM external_binding", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isZero();

            // 错误回调（用户拒绝）同样要走 state/绑定校验 → 401
            Begun errorCb = begin(mvc);
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("error", "access_denied").queryParam("state", errorCb.state())
                            .cookie(errorCb.binding()))
                    .andExpect(status().isUnauthorized());
        });
    }

    /**
     * W22-C1 编排/PG 负向验证（2026-09-27 独立校审）：上游协议异常（成功形状矛盾/
     * 非法信封）在提供者端口表现为"结果不确定"异常——编排层必须 503 + reason 透传、
     * <b>不重试</b>提供者（verify 恰 1 次）、零主体/绑定/会话持久化残留、
     * <b>不下发任何会话 Cookie</b>。state 在异常前已被消费，不复活。
     */
    @Test
    void upstreamProtocolAnomalyIssuesNoSessionAndLeavesZeroPersistence() {
        AnomalyLoginApp.verifyCalls.set(0);
        anomalyRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            JdbcTemplate jdbc = new JdbcTemplate(PgTestSupport.dataSource());

            Begun begun = begin(mvc);
            org.springframework.test.web.servlet.MvcResult callback = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", begun.state())
                            .cookie(begun.binding()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("identity_login_unavailable"))
                    .andExpect(jsonPath("$.reason").value("http-business-conflict"))
                    .andReturn();

            // 不下发会话 Cookie（也不复用绑定 Cookie 延续登录）
            assertThat(cookieOf(callback, "FASTDDD_SESSION")).isNull();

            // 提供者不重试：verify 恰 1 次（结果不确定类重试无意义，code 一次性）
            assertThat(AnomalyLoginApp.verifyCalls.get()).isEqualTo(1);

            // 持久化零残留：无主体/绑定/会话
            assertThat(jdbc.queryForObject("SELECT count(*) FROM principal", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM external_binding", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isZero();

            // state 已消费：同 state 重放 → 401（不复活）
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", begun.state())
                            .cookie(begun.binding()))
                    .andExpect(status().isUnauthorized());
        });
    }

    @Test
    void postLoginRedirectWhitelistBlocksOpenRedirect() throws Exception {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 白名单外绝对地址：回跳回退 "/"（Location 是授权页；裁决结果在回跳）
            org.springframework.test.web.servlet.MvcResult evil = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH)
                            .queryParam("redirect", "https://evil.example/phish"))
                    .andExpect(status().isFound()).andReturn();
            Begun evilBegun = new Begun(stateOf(evil), cookieOf(evil, IdentityLoginCookies.BINDING_COOKIE_NAME));
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", evilBegun.state())
                            .cookie(evilBegun.binding()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/"));

            // 白名单内站内路径：原样回跳
            org.springframework.test.web.servlet.MvcResult home = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH)
                            .queryParam("redirect", "/home"))
                    .andExpect(status().isFound()).andReturn();
            Begun homeBegun = new Begun(stateOf(home), cookieOf(home, IdentityLoginCookies.BINDING_COOKIE_NAME));
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH)
                            .queryParam("code", "ok-code").queryParam("state", homeBegun.state())
                            .cookie(homeBegun.binding()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/home"));
        });
    }

    @Test
    void csrfBootstrapCookieAndLogoutPostProtection() throws Exception {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 任意 GET（登录路径）都会种 XSRF-TOKEN（延迟 token bootstrap 过滤器）
            org.springframework.test.web.servlet.MvcResult bootstrap = mvc
                    .perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH))
                    .andReturn();
            Cookie xsrf = cookieOf(bootstrap, "XSRF-TOKEN");
            assertThat(xsrf).as("GET 响应应种下 XSRF-TOKEN cookie").isNotNull();

            // POST 登出无 CSRF → 403（未认证为 401 之前由 CSRF 先拦）
            mvc.perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.LOGOUT_PATH))
                    .andExpect(status().isForbidden());

            // 有 CSRF 但未认证 → 401
            mvc.perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.LOGOUT_PATH)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        });
    }

    @Test
    void anonymousAccessIsDeniedBeyondPermittedLoginPaths() throws Exception {
        runner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 两个 GET 登录路径 permitAll（authorize 302；callback 无参走 401 业务拒绝）
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH))
                    .andExpect(status().isFound());
            mvc.perform(get(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.CALLBACK_PATH))
                    .andExpect(status().isUnauthorized());
            // 其余路径匿名拒绝；登录路径的 POST 也不放行
            mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
            // 弱断言加固（复评第六节）：无 token 的 POST 无论如何 403（CSRF 先裁），不能
            // 独立证明 POST 不在 permitAll——携带有效 CSRF 后匿名 POST 应 401（若 POST
            // 被 permitAll 会到达 @GetMapping 而得 405；401 = 方法绑定生效的独立证据）
            mvc.perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints.AUTHORIZE_PATH))
                    .andExpect(status().isForbidden());
        });
    }

    private static String stateOf(org.springframework.test.web.servlet.MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return java.util.Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                .findFirst().orElseThrow();
    }
}
