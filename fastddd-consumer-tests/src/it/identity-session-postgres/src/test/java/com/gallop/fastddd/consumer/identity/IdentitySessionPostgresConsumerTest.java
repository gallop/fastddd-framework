package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-1 C 模式消费者闭环（§4.3 条 1/§七 装配与消费者验证）：
 *
 * <ol>
 *   <li><b>装配</b>：identity 会话链唯一；旧 JWT 链/本地认证族/Boot 默认用户名认证全部退让；
 *       员工身份 provider 是唯一 CurrentUserProvider。</li>
 *   <li><b>会话闭环</b>：桩登录签发不透明会话 → cookie 每请求权威复验（200 且
 *       Authentication 名 = principalId）→ 主体停用（同事务撤会话）后同一 cookie 立即 401。</li>
 *   <li><b>防御不降级</b>：CSRF 保留（POST 无令牌 403）、方法安全保留（@Secured 403）、
 *       员工身份缺失拒绝不降 system。</li>
 * </ol>
 * 会话/绑定/主体写入走真实 PG（zonky 一次性测试库）；外部登录桩仅测试夹具（§五）。
 */
@SpringBootTest(classes = IdentitySessionPostgresConsumerTest.App.class)
@AutoConfigureMockMvc
class IdentitySessionPostgresConsumerTest {

    @SpringBootApplication
    @Import({CModeSupport.class, ProbeController.class})
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalLoginUseCase localLoginUseCase;

    @Autowired
    private PrincipalLifecycleUseCase principalLifecycleUseCase;

    @Test
    void identityChainIsSoleAuthorityAndLegacyAuthBacksOff() {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.containsBean("identitySecurityFilterChain")).isTrue();
        assertThat(context.containsBean("jwtAuthenticationFilter")).isFalse();
        // 旧本地认证族与 Boot 默认用户名认证均无旁路
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(
                com.gallop.fastddd.security.controller.AuthController.class)).isEmpty();
        Set<String> patterns = new HashSet<>();
        context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet()
                .forEach(info -> patterns.addAll(info.getPatternValues()));
        assertThat(patterns).isNotEmpty().noneMatch(p -> p.startsWith("/api/auth"));
        // 员工身份 provider 唯一
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).hasSize(1);
        assertThat(context.containsBean("identityCurrentUserProvider")).isTrue();
    }

    @Test
    void sessionCookieRoundTripAndDeactivationKillsItImmediately() throws Exception {
        LoginResult result = localLoginUseCase.login(CModeSupport.command("ou_consumer_1"));
        String sessionId = result.session().sessionId().value();
        String principalId = result.principal().principalId().value();

        // 无 cookie：401（每请求权威复验拒绝未认证）
        mockMvc.perform(get("/api/probe/me")).andExpect(status().isUnauthorized());

        // 有效会话 cookie：通过认证，且 Authentication 名 = principalId（§5 装配结果；
        // String 返回经 Jackson 序列化为 JSON 字符串，按 jsonPath 解析断言）
        mockMvc.perform(get("/api/probe/me").cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").value(principalId));

        // 停用（T4 同事务撤销全部会话）→ 同一 cookie 立即 401
        principalLifecycleUseCase.deactivate(principalId);
        mockMvc.perform(get("/api/probe/me").cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfAndMethodSecurityRemainEnforced() throws Exception {
        LoginResult result = localLoginUseCase.login(CModeSupport.command("ou_consumer_2"));
        String sessionId = result.session().sessionId().value();

        // CSRF 保留：携带有效会话的 POST 缺 X-XSRF-TOKEN → 403
        mockMvc.perform(post("/api/probe/echo")
                        .cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isForbidden());

        // 方法安全保留：会话通过链路认证，但无 ROLE_ADMIN → @Secured 403
        mockMvc.perform(get("/api/probe/admin")
                        .cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSessionFormatIsRejectedAtHttpLayerNot500() throws Exception {
        LoginResult result = localLoginUseCase.login(CModeSupport.command("ou_consumer_3"));
        String sessionId = result.session().sessionId().value();

        // 把会话行写成未知格式（模拟 §13.8 升级矩阵：99 版本行）——HTTP 层必须
        // 401（过滤器拒绝认证，复评 RV-08）而非 500
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        jdbc.update("UPDATE local_session SET session_format_version = 99 WHERE session_id = ?", sessionId);

        mockMvc.perform(get("/api/probe/me").cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingEmployeeIdentityIsRejectedNotDegradedToSystem() {
        CurrentUserProvider provider = context.getBean(CurrentUserProvider.class);
        // 无 SYSTEM_JOB 栈、无会话 Authentication：拒绝而非降级 system
        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(IdentityRequiredException.class);
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/me")
        public String me() {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        }

        @PostMapping("/api/probe/echo")
        public String echo() {
            return "echo";
        }

        @GetMapping("/api/probe/admin")
        @Secured("ROLE_ADMIN")
        public String admin() {
            return "admin";
        }
    }
}
