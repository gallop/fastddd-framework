package com.gallop.fastddd.identity.spring.security;

import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * C 模式安全装配（v0.2 §4.3 条 1，W1-b）：{@code enabled=true} + servlet web 下，
 * {@code login.mode=feishu-web} 时装配 {@code @EnableWebSecurity/@EnableMethodSecurity}
 * + identity 会话过滤链 + CSRF + IdentityCurrentUserProvider（provider 见
 * IdentityAutoConfiguration）。双开关/组合冲突由 IdentityModeGuard 前置拦截；
 * security-starter 默认链因 jwt=false（C 要求显式）自然退让，方法安全能力由本类承接不降级。
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, OncePerRequestFilter.class})
@EnableConfigurationProperties(FastDddIdentityProperties.class)
public class IdentitySecurityAutoConfiguration {

    /**
     * C 模式内核：会话过滤链（@CoM 语义——宿主自定义链时退让，
     * 但须显式声明 host-owned=true，由 IdentityModeValidator 终检）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
    @EnableWebSecurity
    @EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
    static class IdentityWebSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentitySessionAuthenticationFilter identitySessionAuthenticationFilter(
                LocalSessionPort localSessionPort, FastDddIdentityProperties properties) {
            return new IdentitySessionAuthenticationFilter(
                    localSessionPort, properties.getLogin().getSession().getCookieName());
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain identitySecurityFilterChain(HttpSecurity http,
                                                        IdentitySessionAuthenticationFilter sessionFilter)
                throws Exception {
            // CSRF 启用（cookie 会话架构 → 双提交 Cookie 仓）；formLogin/httpBasic 显式关闭，
            // 拒绝旧登录/刷新/验证码路径旁路；飞书端点独立路径（W2-2 交付浏览器回调）
            http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(handler -> handler.authenticationEntryPoint(
                            (request, response, authException) -> response.sendError(401)))
                    .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
