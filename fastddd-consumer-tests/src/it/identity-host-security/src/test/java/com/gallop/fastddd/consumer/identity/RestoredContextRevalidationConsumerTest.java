package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.LogoutUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.spring.actor.IdentityAuthenticationToken;
import com.gallop.fastddd.identity.spring.security.IdentitySessionAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-1 修复轮 W21-02：完整宿主链（host-owned）中的恢复上下文复验——宿主组件
 * （如持久化 SecurityContext 恢复过滤器）在 identity 会话过滤器<b>之前</b>恢复了
 * 旧的会话型 {@code IdentityAuthenticationToken} 时，不得以"已有认证"豁免权威
 * 复验。覆盖：有效、主体停用、会话撤销、epoch 变化、未知格式、缺 Cookie。
 *
 * <p>链形态：宿主链 = [恢复过滤器（测试模拟）] → [identity 会话过滤器] → 授权；
 * 复验失败请求继续（不中断），授权按未认证 401。</p>
 */
@SpringBootTest(classes = RestoredContextRevalidationConsumerTest.App.class,
        properties = "fastddd.identity.security.host-owned=true")
@AutoConfigureMockMvc
class RestoredContextRevalidationConsumerTest {

    /** 测试钩子：模拟宿主在请求线程恢复的旧 Authentication（null=不恢复）。 */
    static volatile Authentication restored;

    // 不用 @SpringBootApplication 的包扫描：包内其他顶层 @Configuration/嵌套控制器
    // 会进入本测试上下文（hostChain 双 anyRequest 链、/api/probe/me 重复映射）——
    // 显式装配 + 自动配置；探针复用 HostOwnedSuccess 的现成 ProbeController
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @Import({HostSecuritySupport.class, HostOwnedSuccessConsumerTest.ProbeController.class})
    static class App {

        /** 宿主链（带恢复语义的测试形态）：恢复过滤器先于 identity 会话过滤器。 */
        @Bean
        SecurityFilterChain restoringHostChain(HttpSecurity http,
                                               IdentitySessionAuthenticationFilter sessionFilter)
                throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(handler -> handler.authenticationEntryPoint(
                            (request, response, authException) -> response.sendError(401)))
                    // 先注册 identity 会话过滤器（取得顺序），再把恢复过滤器插到它前面
                    .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(new RestoredContextFilter(), IdentitySessionAuthenticationFilter.class);
            return http.build();
        }
    }

    /** 模拟宿主的持久化上下文恢复组件：在会话过滤器前恢复旧 token，链尾统一清理。 */
    static class RestoredContextFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        jakarta.servlet.FilterChain filterChain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            try {
                if (restored != null) {
                    SecurityContextHolder.getContext().setAuthentication(restored);
                }
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalLoginUseCase localLoginUseCase;

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private PrincipalLifecycleUseCase principalLifecycleUseCase;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void tearDown() {
        restored = null;
        SecurityContextHolder.clearContext();
    }

    /** 登录并把返回的 token 设为"宿主恢复的旧上下文"。 */
    private LoginResult loginAndRestore(String externalId) {
        LoginResult result = localLoginUseCase.login(HostSecuritySupport.command(externalId));
        restored = IdentityAuthenticationToken.forSession(result.principal());
        return result;
    }

    @Test
    void validRestoredContextWithCookieStillPasses() throws Exception {
        LoginResult result = loginAndRestore("ou_restore_valid");
        mockMvc.perform(get("/api/probe/me")
                        .cookie(new Cookie("FASTDDD_SESSION", result.session().sessionId().value())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").value(result.principal().principalId().value()));
    }

    @Test
    void deactivatedPrincipalRejectsRestoredContext() throws Exception {
        // 登录 → 保存上下文 → 另一入口停用 → 恢复旧上下文的新请求被拒（W21-02 验收路径）
        LoginResult result = loginAndRestore("ou_restore_deactivated");
        principalLifecycleUseCase.deactivate(result.principal().principalId().value());

        mockMvc.perform(get("/api/probe/me")
                        .cookie(new Cookie("FASTDDD_SESSION", result.session().sessionId().value())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedSessionRejectsRestoredContext() throws Exception {
        LoginResult result = loginAndRestore("ou_restore_revoked");
        logoutUseCase.logout(result.session().sessionId());

        mockMvc.perform(get("/api/probe/me")
                        .cookie(new Cookie("FASTDDD_SESSION", result.session().sessionId().value())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void epochMismatchRejectsRestoredContextEvenIfRowUnrevoked() throws Exception {
        LoginResult result = loginAndRestore("ou_restore_epoch");
        String principalId = result.principal().principalId().value();
        // 停用+重新启用：epoch +2 且撤销全部会话
        principalLifecycleUseCase.deactivate(principalId);
        principalLifecycleUseCase.reactivate(principalId);
        // 人为抹掉撤销标记：隔离"epoch 快照不一致"判据（不靠 revoked_at 拒绝）
        new JdbcTemplate(dataSource).update(
                "UPDATE local_session SET revoked_at = NULL WHERE session_id = ?",
                result.session().sessionId().value());

        mockMvc.perform(get("/api/probe/me")
                        .cookie(new Cookie("FASTDDD_SESSION", result.session().sessionId().value())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownSessionFormatRejectsRestoredContext() throws Exception {
        LoginResult result = loginAndRestore("ou_restore_format");
        new JdbcTemplate(dataSource).update(
                "UPDATE local_session SET session_format_version = 99 WHERE session_id = ?",
                result.session().sessionId().value());

        mockMvc.perform(get("/api/probe/me")
                        .cookie(new Cookie("FASTDDD_SESSION", result.session().sessionId().value())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restoredContextWithoutCookieIsRejected() throws Exception {
        loginAndRestore("ou_restore_no_cookie");
        // 有恢复的旧 token、无会话 Cookie：清理身份，按未认证拒绝
        mockMvc.perform(get("/api/probe/me"))
                .andExpect(status().isUnauthorized());
    }
}
