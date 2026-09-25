package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W2-1/W1-b C 模式全链装配与行为（真实 PG + reference-pg + 测试桩提供者）：
 * identity 链注册且旧默认链退让、Boot 默认用户名认证无旁路、登录→cookie→每请求
 * 权威复验→停用即失效的闭环（§4.2/§7）。测试桩仅存在于测试夹具（§五）。
 */
class IdentityCModeAssemblyTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    // Boot 安全侧：验证默认用户名认证在 C 模式下不旁路
                    UserDetailsServiceAutoConfiguration.class,
                    // 宿主侧既有 starter：验证 jwt=false 退让与 CRUD provider 优先序
                    com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class,
                    com.gallop.fastddd.crud.FastDddCrudAutoConfiguration.class))
            .withUserConfiguration(CModeApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    @Configuration(proxyBeanMethods = false)
    static class CModeApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProvider();
        }

        /**
         * 测试夹具显式放行策略（W21-01：框架不再提供默认放行策略；放行决策
         * 只允许存在于测试/隔离夹具——生产宿主必须自带 AdmissionPolicy）。
         */
        @Bean
        com.gallop.fastddd.identity.core.port.AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }
    }

    @Test
    void cModeAssemblyWinsOverLegacyDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            // identity 会话链注册；旧默认链（jwt=false）与 Boot 默认用户名认证均不旁路
            assertThat(context).hasBean("identitySecurityFilterChain");
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
            assertThat(context).doesNotHaveBean(
                    org.springframework.security.core.userdetails.UserDetailsService.class);
            assertThat(context).doesNotHaveBean("authController");

            // provider 优先序：identity > security 桥接 > crud 默认
            assertThat(context).hasBean("identityCurrentUserProvider");
            assertThat(context).doesNotHaveBean("securityCurrentUserProvider");
            assertThat(context).doesNotHaveBean("defaultCurrentUserProvider");

            // 用例与桥接齐备（真实 PG 端口）
            assertThat(context).hasSingleBean(LocalLoginUseCase.class);
            assertThat(context).hasSingleBean(PrincipalLifecycleUseCase.class);
            assertThat(context).hasBean("identityActorResolver");
            assertThat(context).hasBean("springIdentityTransactionRunner");
        });
    }

    @Test
    void sessionCookieRoundTripThroughRealFilterChain() {
        runner.run(context -> {
            LocalLoginUseCase login = context.getBean(LocalLoginUseCase.class);
            LoginResult result = login.login(PgTestSupport.command("ou_roundtrip"));
            String sessionId = result.session().sessionId().value();
            SecurityFilterChain chain = context.getBean("identitySecurityFilterChain", SecurityFilterChain.class);
            FilterChainProxy proxy = new FilterChainProxy(chain);

            // 无 cookie：401（每请求复验拒绝未认证）
            assertThat(execute(proxy, null).getStatus()).isEqualTo(401);

            // 有效会话 cookie：通过认证（terminal filter 捕获到 name=principalId 的 Authentication）
            CapturingChain capture = new CapturingChain();
            MockHttpServletResponse ok = execute(proxy, sessionId, capture);
            assertThat(ok.getStatus()).isEqualTo(200);
            assertThat(capture.authenticationName).isEqualTo(result.principal().principalId().value());

            // 停用（T4 同事务撤会话）→ 同一 cookie 立即失效：401
            context.getBean(PrincipalLifecycleUseCase.class)
                    .deactivate(result.principal().principalId().value());
            assertThat(execute(proxy, sessionId).getStatus()).isEqualTo(401);
        });
    }

    @Test
    void missingRealProviderFailsStartupInCMode() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(NoProviderApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("ExternalLoginProvider"));
    }

    /**
     * W21-01：外部凭证验证成功≠宿主准入——漏配 AdmissionPolicy 时启动失败，
     * 不能由框架默认放行悄悄签发会话（放行策略只允许存在于测试夹具）。
     */
    @Test
    void missingAdmissionPolicyFailsStartupInCMode() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(ProviderWithoutPolicyApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("AdmissionPolicy"));
    }

    @Configuration(proxyBeanMethods = false)
    static class NoProviderApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }
    }

    /** 有真实（桩）提供者但宿主未提供准入策略——漏配即失败。 */
    @Configuration(proxyBeanMethods = false)
    static class ProviderWithoutPolicyApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProvider();
        }
    }

    private static MockHttpServletResponse execute(FilterChainProxy proxy, String sessionId)
            throws ServletException, IOException {
        return execute(proxy, sessionId, new MockFilterChain());
    }

    private static MockHttpServletResponse execute(FilterChainProxy proxy, String sessionId,
                                                   MockFilterChain terminal)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        if (sessionId != null) {
            request.setCookies(new jakarta.servlet.http.Cookie("FASTDDD_SESSION", sessionId));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        proxy.doFilter(request, response, terminal);
        return response;
    }

    /** terminal filter：捕获安全链放行时刻的 SecurityContext 认证名。 */
    static final class CapturingChain extends MockFilterChain {

        volatile String authenticationName;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            this.authenticationName = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication() == null ? null
                    : org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
            super.doFilter(request, response);
        }
    }
}
