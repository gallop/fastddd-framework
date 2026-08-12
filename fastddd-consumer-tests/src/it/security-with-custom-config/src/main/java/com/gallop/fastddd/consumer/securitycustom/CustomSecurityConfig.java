package com.gallop.fastddd.consumer.securitycustom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 业务自定义安全配置（SEC-003 退让验证）。
 *
 * <p>定义了自定义 SecurityFilterChain 与 PasswordEncoder，
 * 框架 {@code SecurityAutoConfiguration} 的对应 Bean 应因
 * {@code @ConditionalOnMissingBean} 退让，不覆盖本配置。</p>
 */
@Configuration
public class CustomSecurityConfig {

    static final String CUSTOM_ENCODER_PREFIX = "CUSTOM-BCRYPT-";

    /**
     * 业务自定义 PasswordEncoder（封装 BCryptPasswordEncoder 加前缀）。
     */
    @Bean
    public PasswordEncoder customPasswordEncoder() {
        return new PasswordEncoder() {
            private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

            @Override
            public String encode(CharSequence rawPassword) {
                return CUSTOM_ENCODER_PREFIX + delegate.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (encodedPassword != null && encodedPassword.startsWith(CUSTOM_ENCODER_PREFIX)) {
                    return delegate.matches(rawPassword,
                            encodedPassword.substring(CUSTOM_ENCODER_PREFIX.length()));
                }
                return false;
            }
        };
    }

    /**
     * 业务自定义 SecurityFilterChain：permit-all（最小策略，仅验证退让而非安全语义）。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain customFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }
}
