package com.gallop.fastddd.consumer.identity.feishu;

import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-2 飞书浏览器登录闭环消费者（§六 D）：<b>真实</b> fast-ddd-identity-feishu 适配器
 * （authorizeUrl 构造 + token v3 JSON 兑换 + user_info Bearer 拉取）+ reference-pg
 * login_state 存储 + C 链会话签发，经 BOM 解析本地安装制品装配。
 *
 * <p>飞书侧为本地 HTTP 桩（FeishuStubSupport，§五 桩边界）；PG 为 zonky 一次性真实库。
 * 覆盖：全链浏览器流（发起→回调→会话 Cookie→受保护资源→登出）、并发重复回调恰一次
 * 兑换、上游确定拒绝（20008→401）与不可用（传输中断→503 不重试）分流、宿主准入拒绝
 * 零残留、回跳白名单。</p>
 */
@SpringBootTest(classes = FeishuLoginConsumerTest.App.class)
@AutoConfigureMockMvc
class FeishuLoginConsumerTest {

    private static final String AUTHORIZE_PATH = "/api/identity/login/authorize";

    private static final String CALLBACK_PATH = "/api/identity/login/callback";

    private static final String LOGOUT_PATH = "/api/identity/logout";

    static final FeishuStubSupport STUB = FeishuStubSupport.start();

    @DynamicPropertySource
    static void feishuProperties(DynamicPropertyRegistry registry) {
        registry.add("fastddd.identity.feishu.app-id", () -> "cli_consumer_app");
        registry.add("fastddd.identity.feishu.app-secret", () -> "consumer-secret-not-real");
        registry.add("fastddd.identity.feishu.redirect-uri",
                () -> "https://consumer.example.com/api/identity/login/callback");
        registry.add("fastddd.identity.feishu.expected-tenant-key", () -> "consumer-tenant");
        registry.add("fastddd.identity.feishu.authorize-endpoint",
                () -> "https://accounts.feishu.cn/open-apis/authen/v1/authorize");
        registry.add("fastddd.identity.feishu.token-endpoint", STUB::tokenUrl);
        registry.add("fastddd.identity.feishu.user-info-endpoint", STUB::userInfoUrl);
    }

    @SpringBootApplication
    @Import({FeishuLoginConsumerTest.FeishuSupport.class, FeishuLoginConsumerTest.ProbeController.class})
    static class App {
    }

    /** 宿主侧夹具：真实 PG 数据源 + 宿主准入策略（登录闭环中宿主拥有准入裁决）。 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class FeishuSupport {

        private static final EmbeddedPostgres PG;

        static {
            try {
                PG = EmbeddedPostgres.builder().start();
            } catch (Exception e) {
                throw new IllegalStateException("embedded PostgreSQL 启动失败", e);
            }
        }

        @Bean
        DataSource dataSource() {
            return PG.getDatabase("postgres", "postgres");
        }

        /** 宿主准入：拒绝桩标记的 ou_denied 主体（其余放行）。 */
        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
                if ("feishu:ou_denied".equals(principal.loginName())) {
                    throw new com.gallop.fastddd.identity.core.error.AdmissionDeniedException("宿主测试拒绝");
                }
            };
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/me")
        public String me() {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanState() {
        STUB.setMode(FeishuStubSupport.Mode.OK);
        STUB.resetCounters();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE login_state");
        jdbc.execute("TRUNCATE TABLE local_session, external_binding, principal CASCADE");
    }

    private record Begun(String state, Cookie binding) {
    }

    /** 发起授权：断言 302 至飞书授权页（真实适配器构造）并取回 state + 绑定 Cookie。 */
    private Begun begin(String redirect) throws Exception {
        MvcResult result = mockMvc.perform(get(AUTHORIZE_PATH).queryParam("redirect", redirect))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "https://accounts.feishu.cn/open-apis/authen/v1/authorize?")))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        // 真实适配器契约：client_id/response_type=code/redirect_uri 编码/state/challenge/S256
        assertThat(location).contains("client_id=cli_consumer_app");
        assertThat(location).contains("response_type=code");
        assertThat(location).contains("redirect_uri=" + urlEncode(
                "https://consumer.example.com/api/identity/login/callback"));
        assertThat(location).contains("code_challenge_method=S256");
        assertThat(queryParam(location, "state")).isNotBlank();
        assertThat(queryParam(location, "code_challenge")).isNotBlank();

        Cookie binding = cookieOf(result, "FASTDDD_LOGIN_STATE");
        assertThat(binding).isNotNull();
        assertThat(binding.isHttpOnly()).isTrue();
        return new Begun(queryParam(location, "state"), binding);
    }

    @Test
    void fullBrowserFlowIssuesSessionAndLogsOut() throws Exception {
        Begun begun = begin("/console");

        // 服务端与 state 同存的 verifier（不落日志/响应）
        String verifier = new JdbcTemplate(dataSource).queryForObject(
                "SELECT code_verifier FROM login_state WHERE state = ?", String.class, begun.state());

        MvcResult callback = mockMvc
                .perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                        .queryParam("state", begun.state()).cookie(begun.binding()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/console"))
                .andReturn();

        Cookie session = cookieOf(callback, "FASTDDD_SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue(); // 消费者侧保持安全缺省
        Cookie bindingCleared = cookieOf(callback, "FASTDDD_LOGIN_STATE");
        assertThat(bindingCleared).isNotNull();
        assertThat(bindingCleared.getMaxAge()).isZero();

        // 真实适配器 HTTP 契约：token v3 JSON 请求体携带 DB 中的 verifier
        assertThat(STUB.lastTokenContentType()).contains("application/json");
        assertThat(STUB.lastTokenRequestBody())
                .contains("\"code\":\"stub-code\"")
                .contains("\"code_verifier\":\"" + verifier + "\"")
                .contains("\"client_id\":\"cli_consumer_app\"")
                .contains("\"redirect_uri\":\"https://consumer.example.com"
                        + "/api/identity/login/callback\"");
        assertThat(STUB.lastUserInfoAuthorization()).isEqualTo("Bearer u-access-token");

        // 受保护资源：会话 Cookie → 200（Authentication 名 = principalId）
        String principalId = new JdbcTemplate(dataSource).queryForObject(
                "SELECT principal_id FROM principal", String.class);
        mockMvc.perform(get("/api/probe/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(principalId));
        mockMvc.perform(get("/api/probe/me")).andExpect(status().isUnauthorized());

        // 登出（已认证 + CSRF）→ 撤销 + 清 Cookie；旧会话立即失效
        MvcResult logout = mockMvc.perform(post(LOGOUT_PATH).cookie(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cleared = cookieOf(logout, "FASTDDD_SESSION");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        mockMvc.perform(get("/api/probe/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentDuplicateCallbackExchangesExactlyOnce() throws Exception {
        Begun begun = begin("/");

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger found = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            CountDownLatch go = new CountDownLatch(1);
            // 两个线程携带同 state/同绑定并发回调（模拟飞书重定向重放/双击）
            java.util.List<Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    MvcResult result = mockMvc
                            .perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                                    .queryParam("state", begun.state()).cookie(begun.binding()))
                            .andReturn();
                    return result.getResponse().getStatus();
                }));
            }
            go.countDown();
            for (Future<Integer> future : futures) {
                int status = future.get();
                if (status == 302) {
                    found.incrementAndGet();
                } else if (status == 401) {
                    rejected.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 恰一个 302 签发、其余 401；token 兑换恰好一次；会话恰好一条
        assertThat(found.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(STUB.tokenHits.get()).isEqualTo(1);
        assertThat(new JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM local_session", Integer.class)).isEqualTo(1);
    }

    @Test
    void upstreamRejectionAndUnavailableAreDistinctAtHttpLayer() throws Exception {
        // 确定拒绝（token 端点 20008）：401，零会话，不重试
        STUB.setMode(FeishuStubSupport.Mode.REJECTED);
        Begun rejected = begin("/");
        mockMvc.perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                        .queryParam("state", rejected.state()).cookie(rejected.binding()))
                .andExpect(status().isUnauthorized());
        assertThat(STUB.tokenHits.get()).isEqualTo(1);

        // 传输中断（结果不确定）：503，零会话，兑换不重试（code 一次性）
        STUB.setMode(FeishuStubSupport.Mode.TRANSPORT);
        Begun unavailable = begin("/");
        mockMvc.perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                        .queryParam("state", unavailable.state()).cookie(unavailable.binding()))
                .andExpect(status().isServiceUnavailable());
        assertThat(STUB.tokenHits.get()).isEqualTo(2);

        assertThat(new JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM local_session", Integer.class)).isZero();
    }

    @Test
    void hostAdmissionDeniedLeavesZeroResidue() throws Exception {
        Begun denied = begin("/");

        mockMvc.perform(get(CALLBACK_PATH).queryParam("code", "deny-code")
                        .queryParam("state", denied.state()).cookie(denied.binding()))
                .andExpect(status().isUnauthorized());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM principal", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_binding", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_session", Integer.class)).isZero();
    }

    @Test
    void postLoginRedirectWhitelistFromHostConfiguration() throws Exception {
        // 白名单内（application.yml: /,/console）→ 原样回跳
        Begun allowed = begin("/console");
        mockMvc.perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                        .queryParam("state", allowed.state()).cookie(allowed.binding()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/console"));

        // 白名单外绝对地址 → 回退 "/"
        Begun evil = begin("https://evil.example/phish");
        mockMvc.perform(get(CALLBACK_PATH).queryParam("code", "stub-code")
                        .queryParam("state", evil.state()).cookie(evil.binding()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
    }

    // ==== 辅助 ====

    private static Cookie cookieOf(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        return null;
    }

    private static String queryParam(String url, String name) {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
