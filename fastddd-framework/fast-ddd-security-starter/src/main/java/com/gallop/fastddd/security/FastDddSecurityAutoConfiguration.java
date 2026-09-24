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
import com.gallop.fastddd.security.config.LocalAuthConfigurationGuard;
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
import org.springframework.core.env.Environment;
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

    /**
     * SEC-002：JWT 相关 Bean 的统一启用条件。
     *
     * <p>仅当 {@code fastddd.security.jwt.enabled=true}（默认缺省视为 true）时才装配
     * {@link JwtSigner}、{@link JwtTokenProvider}、{@link JwtTokenRevocationStore}、
     * 认证异常处理器以及 {@link AuthController} 认证端点。{@code enabled=false} 时这些 Bean
     * 一律不创建，因此最小消费者在禁用 JWT 时无需配置 {@code secret} 即可启动，
     * 也不会暴露框架认证端点。{@link SecurityAutoConfiguration}（SecurityFilterChain/
     * PasswordEncoder/AuthenticationManager）已使用同一条件，二者保持一致。
     */
    private static final String JWT_ENABLED_CONDITION_PREFIX = "fastddd.security.jwt";

    /**
     * W1（评审 R1 / 身份集成方案 §4.5）：本地认证族统一启用条件。
     *
     * <p>仅当 {@code fastddd.security.local-auth.enabled=true}（默认缺省视为 true）时才装配
     * 框架本地认证族：{@link AuthController} 登录端点、{@link VerificationCodeController} 验证码端点、
     * {@link PasswordAuthStrategy}/{@link SmsCodeAuthStrategy}/{@link EmailCodeAuthStrategy}、
     * 默认 {@link AuthStrategyRegistry}、默认 {@link SecurityIdentityService}/UserDetailsService
     * （用户名认证回退）与默认验证码 Store/RateLimiter/Service 及开发用 Noop sender。每个受控 Bean
     * 保留原条件并与本开关取 AND：缺省不配置时与旧版行为完全一致；显式 false 时整族不装配，
     * 宿主自定义链或基础 Bean（PasswordEncoder/VerificationCodeService/AuthenticationManager）
     * 不会重新触发本族。JWT 验证基础、PasswordEncoder、权限 SPI、审计与 CurrentUserProvider
     * 桥接不受本开关影响。</p>
     */
    private static final String LOCAL_AUTH_CONDITION_PREFIX = "fastddd.security.local-auth";

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(JwtSigner.class)
    public JwtSigner jwtSigner(JwtProperties jwtProperties) {
        log.info("FastDDD security: using default HmacJwtSigner (HS512)");
        return new HmacJwtSigner(jwtProperties.getSecret());
    }

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JwtTokenRevocationStore jwtTokenRevocationStore() {
        return new InMemoryJwtTokenRevocationStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties,
                                             JwtSigner jwtSigner,
                                             JwtTokenRevocationStore tokenRevocationStore) {
        log.info("FastDDD security: JwtTokenProvider initialized with JwtSigner={}", jwtSigner.getClass().getSimpleName());
        return new JwtTokenProvider(jwtProperties, jwtSigner, tokenRevocationStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AuthStrategyRegistry authStrategyRegistry(@Autowired(required = false) List<AuthStrategy> strategies) {
        return new AuthStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(AuthenticationManager.class)
    public PasswordAuthStrategy passwordAuthStrategy(AuthenticationManager authenticationManager) {
        return new PasswordAuthStrategy(authenticationManager);
    }

    @Bean
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public VerificationCodeStore verificationCodeStore() {
        return new InMemoryVerificationCodeStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public VerificationCodeRateLimiter verificationCodeRateLimiter() {
        return new NoopVerificationCodeRateLimiter();
    }

    @Bean
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean
    public SmsCodeAuthStrategy smsCodeAuthStrategy(VerificationCodeService verificationCodeService,
                                                   SecurityIdentityService identityService) {
        return new SmsCodeAuthStrategy(verificationCodeService, identityService);
    }

    @Bean
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean
    public EmailCodeAuthStrategy emailCodeAuthStrategy(VerificationCodeService verificationCodeService,
                                                       SecurityIdentityService identityService) {
        return new EmailCodeAuthStrategy(verificationCodeService, identityService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fastddd.security.code", name = "dev-mode", havingValue = "true")
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "noopSmsVerificationCodeSender")
    public VerificationCodeSender noopSmsVerificationCodeSender() {
        return new NoopVerificationCodeSender(VerificationCodeType.SMS);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fastddd.security.code", name = "dev-mode", havingValue = "true")
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "noopEmailVerificationCodeSender")
    public VerificationCodeSender noopEmailVerificationCodeSender() {
        return new NoopVerificationCodeSender(VerificationCodeType.EMAIL);
    }

    /**
     * SEC-003：CRUD CurrentUserProvider 桥接配置。
     *
     * <p>仅当业务未定义自己的 {@code CurrentUserProvider} 时才注册
     * {@link SecurityCurrentUserProvider}（从 SecurityContext 提取当前用户）。
     * 业务自定义实现优先——按接口类型退让，避免两个 Provider 并存导致
     * {@code FastDddCrudAutoConfiguration} 注入歧义。</p>
     */
    @Configuration
    @ConditionalOnClass(name = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
    static class CrudBridgeConfiguration {
        @Bean
        @ConditionalOnMissingBean(type = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
        public SecurityCurrentUserProvider securityCurrentUserProvider() {
            return new SecurityCurrentUserProvider();
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = JWT_ENABLED_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = LOCAL_AUTH_CONDITION_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "com.gallop.fastddd.web.response.ApiResponse")
    @ConditionalOnBean(VerificationCodeService.class)
    @ConditionalOnMissingBean(name = "verificationCodeController")
    public VerificationCodeController verificationCodeController(VerificationCodeService verificationCodeService) {
        return new VerificationCodeController(verificationCodeService);
    }

    /**
     * W1（评审 R1）：本地认证族配置冲突 fail-fast 守卫。
     *
     * <p>local-auth 显式关闭但 {@code fastddd.security.code.dev-mode} 显式开启时启动失败
     * 并指名两个属性（见 {@link LocalAuthConfigurationGuard}）。identity.* 组合校验
     * 随 W2 identity-starter 落地，本守卫只覆盖 security-starter 自身可判定的矛盾。</p>
     */
    @Bean
    public LocalAuthConfigurationGuard localAuthConfigurationGuard(Environment environment) {
        return new LocalAuthConfigurationGuard(environment);
    }
}
