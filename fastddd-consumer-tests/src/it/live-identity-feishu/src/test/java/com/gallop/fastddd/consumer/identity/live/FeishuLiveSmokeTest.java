package com.gallop.fastddd.consumer.identity.live;

import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实联调冒烟（opt-in；运行手册 L1/L2 层；完整分层见维护者内部运行手册）。
 *
 * <p><b>门槛</b>：类级 {@code @EnabledIfEnvironmentVariable(FEISHU_LIVE_APP_ID)}——
 * 未设置时全部测试禁用（构建恒绿，<b>不算联调通过</b>）。凭据只经环境变量进入：
 * FEISHU_LIVE_APP_ID / FEISHU_LIVE_APP_SECRET / FEISHU_LIVE_REDIRECT_URI /
 * FEISHU_LIVE_TENANT_KEY（设了 APP_ID 而缺其余必填项时，上下文按装配期契约 fail-fast
 * 并列出缺失属性路径——这本身就是 L1 断言之一）。</p>
 *
 * <p><b>L1（默认，无外呼）</b>：真实飞书坐标下的装配与 authorizeUrl 构造——上下文
 * 启动、官方缺省 authorize 端点、client_id/redirect_uri/PKCE 全参、服务端 verifier
 * 落库。MockMvc 断言不产生任何对外网络调用。</p>
 *
 * <p><b>L2（附加，FEISHU_LIVE_NETWORK=true 才启用）</b>：对<b>公开授权页</b>发一次
 * 真实 GET（JDK HttpClient，10s 超时）验证域名/TLS/可达性。无凭据交换、不调
 * token/user_info、不消费任何 code——真实 token 兑换与 user_info 只在测试企业
 * 回调登记完成后按运行手册 L3 人工浏览器流程验证。</p>
 */
@SpringBootTest(classes = FeishuLiveSmokeTest.App.class)
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "FEISHU_LIVE_APP_ID", matches = ".+")
class FeishuLiveSmokeTest {

    private static final String AUTHORIZE_PATH = "/api/identity/login/authorize";

    /** 官方缺省 authorize 端点（不 override——L1 即验证缺省坐标正确）。 */
    private static final String OFFICIAL_AUTHORIZE =
            "https://accounts.feishu.cn/open-apis/authen/v1/authorize";

    @DynamicPropertySource
    static void feishuLiveProperties(DynamicPropertyRegistry registry) {
        registry.add("fastddd.identity.feishu.app-id", () -> System.getenv("FEISHU_LIVE_APP_ID"));
        registry.add("fastddd.identity.feishu.app-secret", () -> System.getenv("FEISHU_LIVE_APP_SECRET"));
        registry.add("fastddd.identity.feishu.redirect-uri", () -> System.getenv("FEISHU_LIVE_REDIRECT_URI"));
        registry.add("fastddd.identity.feishu.expected-tenant-key", () -> System.getenv("FEISHU_LIVE_TENANT_KEY"));
    }

    @SpringBootApplication
    @Import({FeishuLiveSmokeTest.LiveSupport.class, FeishuLiveSmokeTest.ProbeController.class})
    static class App {
    }

    /** 宿主侧夹具：真实 PG 数据源 + 放行准入（冒烟层不裁准入语义）。 */
    @Configuration(proxyBeanMethods = false)
    static class LiveSupport {

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

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
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

    @Autowired
    private ExternalLoginProvider loginProvider;

    @Autowired
    private ExternalAuthorizePort authorizePort;

    /** L1-1：真实坐标装配——两端口来自同一真实 feishu 适配器（非桩）。 */
    @Test
    void l1ContextAssemblesRealFeishuAdapterWithOfficialDefaults() {
        assertThat(loginProvider).isSameAs(authorizePort);
        String authorizeUrl = authorizePort.authorizeUrl("live-state-probe",
                "live-challenge-probe");
        assertThat(authorizeUrl)
                .startsWith(OFFICIAL_AUTHORIZE + "?")
                .contains("client_id=" + System.getenv("FEISHU_LIVE_APP_ID"))
                .contains("code_challenge_method=S256");
    }

    /**
     * L1-2：发起授权（无外呼）——302 至官方授权页，client_id/redirect_uri(编码)/state/
     * S256 challenge 全参；Cache-Control: no-store（W22-RV-09）；verifier 只落服务端库。
     */
    @Test
    void l1AuthorizeRedirectTargetsRealFeishuPageWithServerSidePkce() throws Exception {
        MvcResult result = mockMvc.perform(get(AUTHORIZE_PATH).queryParam("redirect", "/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith(OFFICIAL_AUTHORIZE + "?")))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("client_id=" + System.getenv("FEISHU_LIVE_APP_ID"));
        assertThat(location).contains("response_type=code");
        assertThat(location).contains("redirect_uri=" + urlEncode(System.getenv("FEISHU_LIVE_REDIRECT_URI")));
        assertThat(queryParam(location, "state")).isNotBlank();
        assertThat(queryParam(location, "code_challenge")).isNotBlank();

        Cookie binding = cookieOf(result, "FASTDDD_LOGIN_STATE");
        assertThat(binding).isNotNull();
        assertThat(binding.isHttpOnly()).isTrue();

        // 服务端 PKCE：verifier 与 state 同存库中（不入响应/日志）——真实坐标下同语义
        String state = queryParam(location, "state");
        String verifier = new JdbcTemplate(dataSource).queryForObject(
                "SELECT code_verifier FROM login_state WHERE state = ?", String.class, state);
        assertThat(verifier).isNotBlank();
    }

    /**
     * L2（附加门槛 FEISHU_LIVE_NETWORK=true）：公开授权页真实可达性——域名解析/TLS/端点
     * 存在。只 GET 授权页本体：无凭据交换、不携带 secret、不消费 code；2xx–4xx 均视为
     * 可达（未登录时官方授权页可能 302 到登录域，同属可达），仅 IOException/5xx 失败。
     */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
            named = "FEISHU_LIVE_NETWORK", matches = "true")
    void l2RealAuthorizePageIsReachableWithoutCredentialExchange() throws Exception {
        String authorizeUrl = authorizePort.authorizeUrl("live-reach-probe", "live-reach-probe");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(authorizeUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isLessThan(500);
    }

    // ==== 辅助（与 identity-feishu-login 场景同构） ====

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
