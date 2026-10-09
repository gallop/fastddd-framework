package com.gallop.fastddd.identity.spring.security;

import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsEndpoints;
import com.gallop.fastddd.identity.spring.web.IdentityLoginEndpoints;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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
                                                        IdentitySessionAuthenticationFilter sessionFilter,
                                                        FastDddIdentityProperties properties)
                throws Exception {
            // CSRF 启用（cookie 会话架构 → 双提交 Cookie 仓）；formLogin/httpBasic 显式关闭，
            // 拒绝旧登录/刷新/验证码路径旁路。
            // 请求处理器必须显式用 plain CsrfTokenRequestAttributeHandler：SS6 默认的
            // XorCsrfTokenRequestAttributeHandler 期望客户端回传编码值，会拒绝浏览器
            // 「裸 XSRF-TOKEN cookie 值 → X-XSRF-TOKEN header」双提交（2026-09-27 L3
            // 真实联调实证：Base64 解码长度不符即拒，服务端 403/401，无客户端侧可行
            // 写法）。官方 SPA 方案：裸值常量时间比较。
            // W5-d：增量事件 webhook（仅 increment.enabled 时入安全面）——服务间
            // 调用无浏览器会话，CSRF 不适用；安全性由接收链验签/解密承载（§5.1，M-1）。
            // 豁免面 = 固定常量路径（不随配置扩张——差异登记在案）。
            boolean incrementEvents = properties.getDirectory().getIncrement().isEnabled();
            PathPatternRequestMatcher eventsMatcher = PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, IdentityDirectoryEventsEndpoints.EVENTS_PATH);
            http.csrf(csrf -> {
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
                if (incrementEvents) {
                    csrf.ignoringRequestMatchers(eventsMatcher);
                }
            })
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> {
                        // W5-d：增量事件 webhook 端点（仅 increment.enabled 时）——
                        // 入口自证语义（验签/解密承载安全性，M-1），非匿名业务面
                        //（与登录两 GET 路径同款放行理由）。
                        if (incrementEvents) {
                            auth.requestMatchers(eventsMatcher).permitAll();
                        }
                        // W2-2 浏览器登录闭环的最小放行面：仅两个 GET 登录路径
                        // （发起授权是无认证跳转；回调自行消费一次性 state + 浏览器绑定
                        //  完成信任裁决，非匿名业务面）。其余路径维持 authenticated。
                        // 显式 PathPatternRequestMatcher：不依赖容器是否注册 MVC
                        // introspector（字符串+HttpMethod 重载会走 MvcRequestMatcher，
                        // 无 MVC 装配的宿主会启动失败）。
                        auth.requestMatchers(
                                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                                                IdentityLoginEndpoints.AUTHORIZE_PATH),
                                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                                                IdentityLoginEndpoints.CALLBACK_PATH))
                                .permitAll()
                                .anyRequest().authenticated();
                    })
                    .exceptionHandling(handler -> handler.authenticationEntryPoint(
                            (request, response, authException) -> response.sendError(401)))
                    .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
                    // Spring Security 6 延迟 CSRF token：cookie 只有在 token 被读取时才落盘。
                    // 此过滤器对每个响应前触碰一次 token，保证任意 GET（含登录回调/受保护页）
                    // 都种下 XSRF-TOKEN cookie，浏览器后续 POST（如登出）可携带双提交头。
                    // （Spring Security 官方 SPA 模式；不改变校验语义。）
                    .addFilterAfter((request, response, chain) -> {
                        org.springframework.security.web.csrf.CsrfToken token =
                                (org.springframework.security.web.csrf.CsrfToken) request
                                        .getAttribute(org.springframework.security.web.csrf.CsrfToken.class
                                                .getName());
                        if (token != null) {
                            token.getToken();
                        }
                        chain.doFilter(request, response);
                    }, org.springframework.security.web.csrf.CsrfFilter.class);
            return http.build();
        }
    }
}
