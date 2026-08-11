package com.gallop.fastddd.security;

import com.gallop.fastddd.security.audit.AuditLogAspect;
import com.gallop.fastddd.security.audit.AuditLogService;
import com.gallop.fastddd.security.audit.AuditLogServiceImpl;
import com.gallop.fastddd.security.code.DefaultVerificationCodeService;
import com.gallop.fastddd.security.code.InMemoryVerificationCodeStore;
import com.gallop.fastddd.security.code.NoopVerificationCodeSender;
import com.gallop.fastddd.security.code.NoopVerificationCodeRateLimiter;
import com.gallop.fastddd.security.code.VerificationCodeProperties;
import com.gallop.fastddd.security.code.VerificationCodeRateLimiter;
import com.gallop.fastddd.security.code.VerificationCodeSender;
import com.gallop.fastddd.security.code.VerificationCodeService;
import com.gallop.fastddd.security.code.VerificationCodeStore;
import com.gallop.fastddd.security.code.VerificationCodeType;
import com.gallop.fastddd.security.config.AuditLogConfiguration;
import com.gallop.fastddd.security.config.SecurityAutoConfiguration;
import com.gallop.fastddd.security.controller.VerificationCodeController;
import com.gallop.fastddd.security.controller.AuthController;
import com.gallop.fastddd.security.provider.SecurityCurrentUserProvider;
import com.gallop.fastddd.security.handler.JwtAccessDeniedHandler;
import com.gallop.fastddd.security.handler.JwtAuthenticationEntryPoint;
import com.gallop.fastddd.security.jwt.HmacJwtSigner;
import com.gallop.fastddd.security.jwt.InMemoryJwtTokenRevocationStore;
import com.gallop.fastddd.security.jwt.JwtProperties;
import com.gallop.fastddd.security.jwt.JwtSigner;
import com.gallop.fastddd.security.jwt.JwtTokenRevocationStore;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.DefaultSecurityIdentityService;
import com.gallop.fastddd.security.spi.DefaultSecurityPermissionService;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityPermissionService;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import com.gallop.fastddd.security.strategy.AuthStrategy;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import com.gallop.fastddd.security.strategy.EmailCodeAuthStrategy;
import com.gallop.fastddd.security.strategy.PasswordAuthStrategy;
import com.gallop.fastddd.security.strategy.SmsCodeAuthStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.sql.DataSource;
import java.util.List;

/**
 * FastDDD安全模块自动配置
 *
 * <p>使用 @AutoConfiguration 确保作为 deferred import 处理，
 * 保证 @ConditionalOnMissingBean 能检测到用户通过 @Service 注册的 SPI 实现。
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@AutoConfiguration
@EnableConfigurationProperties(VerificationCodeProperties.class)
@Import({
    SecurityAutoConfiguration.class,
    AuditLogConfiguration.class
})
public class FastDddSecurityAutoConfiguration {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FastDddSecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JwtSigner.class)
    public JwtSigner jwtSigner(JwtProperties jwtProperties) {
        log.info("FastDDD security: using default HmacJwtSigner (HS512)");
        return new HmacJwtSigner(jwtProperties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenRevocationStore jwtTokenRevocationStore() {
        return new InMemoryJwtTokenRevocationStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties,
                                             JwtSigner jwtSigner,
                                             JwtTokenRevocationStore tokenRevocationStore) {
        log.info("FastDDD security: JwtTokenProvider initialized with JwtSigner={}", jwtSigner.getClass().getSimpleName());
        return new JwtTokenProvider(jwtProperties, jwtSigner, tokenRevocationStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAccessDeniedHandler jwtAccessDeniedHandler() {
        return new JwtAccessDeniedHandler();
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogService.class)
    public AuditLogServiceImpl auditLogServiceImpl(@Autowired(required = false) JdbcTemplate jdbcTemplate,
                                                   @Autowired(required = false) DataSource dataSource) {
        AuditLogServiceImpl impl = new AuditLogServiceImpl();
        impl.setJdbcTemplate(jdbcTemplate);
        impl.setDataSource(dataSource);
        return impl;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(AuditLogService auditLogService) {
        return new AuditLogAspect(auditLogService);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityIdentityService.class)
    public SecurityIdentityService securityIdentityService() {
        return new DefaultSecurityIdentityService();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityPermissionService.class)
    public SecurityPermissionService securityPermissionService() {
        return new DefaultSecurityPermissionService();
    }

    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService userDetailsService(SecurityIdentityService identityService) {
        return username -> {
            SecurityUserDetails details = identityService.loadUserByUsername(username);
            if (details == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            return details;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthStrategyRegistry authStrategyRegistry(@Autowired(required = false) List<AuthStrategy> strategies) {
        return new AuthStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnBean(AuthenticationManager.class)
    public PasswordAuthStrategy passwordAuthStrategy(AuthenticationManager authenticationManager) {
        return new PasswordAuthStrategy(authenticationManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public VerificationCodeStore verificationCodeStore() {
        return new InMemoryVerificationCodeStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public VerificationCodeRateLimiter verificationCodeRateLimiter() {
        return new NoopVerificationCodeRateLimiter();
    }

    @Bean
    @ConditionalOnBean(PasswordEncoder.class)
    @ConditionalOnMissingBean
    public VerificationCodeService verificationCodeService(VerificationCodeProperties properties,
                                                           VerificationCodeStore store,
                                                           PasswordEncoder passwordEncoder,
                                                           VerificationCodeRateLimiter rateLimiter,
                                                           @Autowired(required = false) List<VerificationCodeSender> senders) {
        return new DefaultVerificationCodeService(properties, store, passwordEncoder, rateLimiter, senders);
    }

    @Bean
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean
    public SmsCodeAuthStrategy smsCodeAuthStrategy(VerificationCodeService verificationCodeService,
                                                   SecurityIdentityService identityService) {
        return new SmsCodeAuthStrategy(verificationCodeService, identityService);
    }

    @Bean
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean
    public EmailCodeAuthStrategy emailCodeAuthStrategy(VerificationCodeService verificationCodeService,
                                                       SecurityIdentityService identityService) {
        return new EmailCodeAuthStrategy(verificationCodeService, identityService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fastddd.security.code", name = "dev-mode", havingValue = "true")
    @ConditionalOnMissingBean(name = "noopSmsVerificationCodeSender")
    public VerificationCodeSender noopSmsVerificationCodeSender() {
        return new NoopVerificationCodeSender(VerificationCodeType.SMS);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fastddd.security.code", name = "dev-mode", havingValue = "true")
    @ConditionalOnMissingBean(name = "noopEmailVerificationCodeSender")
    public VerificationCodeSender noopEmailVerificationCodeSender() {
        return new NoopVerificationCodeSender(VerificationCodeType.EMAIL);
    }

    @Configuration
    @ConditionalOnClass(name = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
    static class CrudBridgeConfiguration {
        @Bean
        @Primary
        public SecurityCurrentUserProvider securityCurrentUserProvider() {
            return new SecurityCurrentUserProvider();
        }
    }

    @Bean
    @ConditionalOnClass(name = "com.gallop.fastddd.web.response.ApiResponse")
    @ConditionalOnMissingBean(name = "authController")
    public AuthController frameworkAuthController(JwtTokenProvider jwtTokenProvider,
                                                   JwtProperties jwtProperties,
                                                   SecurityIdentityService identityService,
                                                   SecurityPermissionService permissionService,
                                                   AuthStrategyRegistry strategyRegistry) {
        return new AuthController(jwtTokenProvider, jwtProperties, identityService, permissionService, strategyRegistry);
    }

    @Bean
    @ConditionalOnClass(name = "com.gallop.fastddd.web.response.ApiResponse")
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean(name = "verificationCodeController")
    public VerificationCodeController verificationCodeController(VerificationCodeService verificationCodeService) {
        return new VerificationCodeController(verificationCodeService);
    }
}
