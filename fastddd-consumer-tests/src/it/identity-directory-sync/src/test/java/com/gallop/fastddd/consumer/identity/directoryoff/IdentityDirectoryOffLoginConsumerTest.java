package com.gallop.fastddd.consumer.identity.directoryoff;

import com.gallop.fastddd.consumer.identity.SyncPgSupport;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.spring.directory.DirectoryDiffReporter;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncManualTrigger;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import com.gallop.fastddd.identity.spring.web.IdentityLoginCookies;
import com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W3 设计 v3 §8 矩阵 25（目录关闭登录零回归，W3-c 消费者门禁）：同宿主形态
 * （C 模式 feishu-web）下 {@code directory.enabled=false}——目录机件全不装配
 * （用例/手动触发/差异报表/调度），登录闭环 / T2 稳定身份 / T4 停用即失效与
 * W2-2 基线一致（基线见 identity-feishu-login 与 identity-session-postgres
 * 场景）。目录类在 classpath 而开关关闭：验证引入目录能力不扰动登录路径。
 *
 * <p>登录走 HTTP 浏览器流（发起→回调 code→会话 Cookie）——提供者/准入/授权 URL
 * 均为测试夹具桩（§五 桩边界），真实适配器闭环见 identity-feishu-login 场景。</p>
 */
@SpringBootTest(classes = IdentityDirectoryOffLoginConsumerTest.App.class,
        properties = "fastddd.identity.directory.enabled=false")
@AutoConfigureMockMvc
class IdentityDirectoryOffLoginConsumerTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PrincipalLifecycleUseCase principalLifecycleUseCase;

    @SpringBootApplication
    @Import({SyncPgSupport.class, IdentityDirectoryOffLoginConsumerTest.ProbeController.class})
    static class App {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/me")
        public String me() {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        }
    }

    @BeforeEach
    void cleanLoginTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE login_state");
        jdbc.execute("TRUNCATE TABLE local_session, external_binding, principal CASCADE");
    }

    @Test
    void directoryMachineryStaysCompletelyOff() {
        // 目录机件零装配（开关关闭）：用例/手动触发/差异报表/调度线程
        assertThat(context.getBeanNamesForType(DirectorySyncUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DirectorySyncManualTrigger.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DirectoryDiffReporter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DirectorySyncScheduler.class)).isEmpty();
        // 登录链形态与 W2-2 基线一致：identity 链唯一、旧 JWT 链不装配
        assertThat(context.containsBean("identitySecurityFilterChain")).isTrue();
        assertThat(context.containsBean("jwtAuthenticationFilter")).isFalse();
    }

    @Test
    void loginT2AndDeactivationMatchW22Baseline() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LoggedIn first = login("ou_regression");

        // 会话 cookie 每请求权威复验（基线形态：带 cookie 200 且名=principalId；无 cookie 401）
        mockMvc.perform(get("/api/probe/me").cookie(first.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(first.principalId()));
        mockMvc.perform(get("/api/probe/me")).andExpect(status().isUnauthorized());

        // T2 稳定身份：同外部主体再次登录 → 同一 principalId，无第二主体/绑定行
        LoggedIn second = login("ou_regression");
        assertThat(second.principalId()).isEqualTo(first.principalId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM principal", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM external_binding WHERE external_id = ?",
                Integer.class, "ou_regression")).isEqualTo(1);

        // T4 停用即失效（W2-2 基线路径）：DISABLED + epoch+1 + 旧 cookie 立即 401
        long epochBefore = jdbc.queryForObject(
                "SELECT auth_epoch FROM principal WHERE principal_id = ?",
                Long.class, first.principalId());
        principalLifecycleUseCase.deactivate(first.principalId());
        assertThat(jdbc.queryForObject("SELECT status FROM principal WHERE principal_id = ?",
                String.class, first.principalId())).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject("SELECT auth_epoch FROM principal WHERE principal_id = ?",
                Long.class, first.principalId())).isEqualTo(epochBefore + 1);
        mockMvc.perform(get("/api/probe/me").cookie(first.session()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/probe/me").cookie(second.session()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- 助手

    private record LoggedIn(String principalId, Cookie session) {
    }

    /** 登录闭环（发起→回调 code→会话 Cookie），并回查 principalId。 */
    private LoggedIn login(String code) throws Exception {
        MvcResult authorize = mockMvc
                .perform(get(IdentityLoginEndpoints.AUTHORIZE_PATH).queryParam("redirect", "/home"))
                .andExpect(status().isFound())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        assertThat(location).startsWith("https://idp-stub.example/authorize?");
        String state = Arrays.stream(location.substring(location.indexOf("state=") + 6).split("&"))
                .findFirst().orElseThrow();
        Cookie binding = cookieOf(authorize, IdentityLoginCookies.BINDING_COOKIE_NAME);
        assertThat(binding).as("发起授权应下发绑定 Cookie").isNotNull();

        MvcResult callback = mockMvc
                .perform(get(IdentityLoginEndpoints.CALLBACK_PATH)
                        .queryParam("code", code).queryParam("state", state).cookie(binding))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/home"))
                .andReturn();
        Cookie session = cookieOf(callback, "FASTDDD_SESSION");
        assertThat(session).as("登录成功应下发会话 Cookie（code=" + code + "）").isNotNull();
        String principalId = new JdbcTemplate(dataSource).queryForObject(
                "SELECT p.principal_id FROM local_session s"
                        + " JOIN principal p ON p.principal_id = s.principal_id WHERE s.session_id = ?",
                String.class, session.getValue());
        return new LoggedIn(principalId, session);
    }

    private static Cookie cookieOf(MvcResult result, String name) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        return null;
    }
}
