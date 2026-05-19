package com.gallop.fastddd.security.config;

import com.gallop.fastddd.security.handler.JwtAccessDeniedHandler;
import com.gallop.fastddd.security.handler.JwtAuthenticationEntryPoint;
import com.gallop.fastddd.security.jwt.JwtAuthenticationFilter;
import com.gallop.fastddd.security.jwt.JwtProperties;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security自动配置
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, SecurityHttpProperties.class})
@ConditionalOnProperty(prefix = "fastddd.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsProperties corsProperties;
    private final SecurityHttpProperties httpProperties;

    public SecurityAutoConfiguration(JwtTokenProvider jwtTokenProvider,
                                   JwtProperties jwtProperties,
                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                   JwtAccessDeniedHandler jwtAccessDeniedHandler,
                                   CorsProperties corsProperties,
                                   SecurityHttpProperties httpProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.corsProperties = corsProperties;
        this.httpProperties = httpProperties;
        log.info("SecurityAutoConfiguration initialized with CORS properties: enabled={}, allowedOrigins={}",
                corsProperties.isEnabled(), corsProperties.getAllowedOrigins());
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     * 用于处理用户认证
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * JWT认证过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, jwtProperties, httpProperties);
    }

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF
            .csrf(csrf -> csrf.disable())

            // 配置CORS（开发环境可禁用）
            .cors(cors -> {
                if (corsProperties.shouldDisableCors()) {
                    log.info("Disabling CORS filter in development mode");
                    cors.disable();  // 完全禁用CORS检查
                } else {
                    cors.configurationSource(corsConfigurationSource());
                }
            })

            // 配置会话管理为无状态
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Security 6.x 默认不自动保存 SecurityContext，导致 DeferredResult 等
            // 异步分派丢失认证信息。启用隐式保存以支持异步请求。
            .securityContext(context -> context
                .requireExplicitSave(false)
            )

            // 配置异常处理
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )

            // 配置请求授权
            .authorizeHttpRequests(authz -> {
                // 公开接口（包括 OPTIONS 预检请求）
                authz.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                // 配置化的 permit-all 路径
                for (String path : httpProperties.getPermitAllPaths()) {
                    authz.requestMatchers(path).permitAll();
                }
                // 其他请求需要认证
                authz.anyRequest().authenticated();
            })

            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

            // 配置安全头
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentTypeOptions -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
            );

        return http.build();
    }

    /**
     * CORS配置源（最佳实践）
     * <p>
     * 使用配置化的CORS策略，支持：
     * <ul>
     *   <li>配置文件：application.yml, application-prod.yml</li>
     *   <li>环境变量：FASTDDD_SECURITY_CORS_*</li>
     *   <li>命令行参数：--fastddd.security.cors.*</li>
     * </ul>
     * <p>
     * 启动时会自动验证配置并打印详细日志，便于问题排查
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (!corsProperties.isEnabled()) {
            log.info("CORS is disabled by configuration");
            return new UrlBasedCorsConfigurationSource();
        }

        // 使用CorsProperties的转换方法获取Spring的CorsConfiguration
        org.springframework.web.cors.CorsConfiguration configuration =
                corsProperties.toSpringCorsConfiguration();

        // 注册URL路径映射
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!CollectionUtils.isEmpty(corsProperties.getPathPatterns())) {
            for (String pathPattern : corsProperties.getPathPatterns()) {
                source.registerCorsConfiguration(pathPattern, configuration);
            }
        } else {
            source.registerCorsConfiguration("/**", configuration);
        }

        return source;
    }
}