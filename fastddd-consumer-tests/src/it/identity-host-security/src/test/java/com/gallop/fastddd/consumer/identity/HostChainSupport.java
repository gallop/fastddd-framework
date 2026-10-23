package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.spring.security.IdentitySessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 宿主自建链（无恢复语义的标准形态，W2-1 修复轮自 {@link HostSecuritySupport}
 * 拆出）：公共路径放行 + 其余 authenticated + 复用 identity 会话过滤器；
 * CSRF/授权策略由宿主链自决——会话防护责任随 host-owned 声明显式移交宿主。
 */
@Configuration(proxyBeanMethods = false)
class HostChainSupport {

    /** 宿主自建链：公共路径放行 + 其余 authenticated + 复用 identity 会话过滤器。 */
    @Bean
    SecurityFilterChain hostChain(HttpSecurity http, IdentitySessionAuthenticationFilter sessionFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(401)))
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
