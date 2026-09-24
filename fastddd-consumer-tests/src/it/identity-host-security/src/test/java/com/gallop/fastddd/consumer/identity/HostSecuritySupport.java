package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.spring.security.IdentitySessionAuthenticationFilter;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.sql.DataSource;
import java.util.Map;

/**
 * C+host-owned 消费者测试夹具（§五 桩边界）：zonky PG、测试登录桩与宿主自建链。
 * 宿主链复用 identity 的 {@link IdentitySessionAuthenticationFilter}（每请求权威复验），
 * CSRF/授权策略由宿主链自决——会话防护责任随 host-owned 声明显式移交宿主。
 */
@Configuration(proxyBeanMethods = false)
class HostSecuritySupport {

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
    ExternalLoginProvider externalLoginProvider() {
        return command -> {
            String externalId = command.credential("externalId") == null ? "ou_default"
                    : command.credential("externalId");
            return new VerifiedExternalLogin(
                    new ExternalIdentity("feishu-stub", "consumer-tenant", "consumer-app",
                            "union_id", externalId),
                    "login-" + externalId, "Stub " + externalId);
        };
    }

    static ExternalLoginCommand command(String externalId) {
        return new ExternalLoginCommand(Map.of("externalId", externalId));
    }

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
