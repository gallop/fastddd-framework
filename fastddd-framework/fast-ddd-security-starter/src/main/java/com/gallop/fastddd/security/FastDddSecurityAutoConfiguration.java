package com.gallop.fastddd.security;

import com.gallop.fastddd.security.audit.AuditLogAspect;
import com.gallop.fastddd.security.audit.AuditLogService;
import com.gallop.fastddd.security.audit.AuditLogServiceImpl;
import com.gallop.fastddd.security.config.AuditLogConfiguration;
import com.gallop.fastddd.security.config.SecurityAutoConfiguration;
import com.gallop.fastddd.security.controller.AuthController;
import com.gallop.fastddd.security.handler.JwtAccessDeniedHandler;
import com.gallop.fastddd.security.handler.JwtAuthenticationEntryPoint;
import com.gallop.fastddd.security.jwt.JwtProperties;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.DefaultSecurityIdentityService;
import com.gallop.fastddd.security.spi.DefaultSecurityPermissionService;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityPermissionService;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * FastDDD安全模块自动配置
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Configuration
@Import({
    SecurityAutoConfiguration.class,
    AuditLogConfiguration.class
})
public class FastDddSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
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
    @ConditionalOnMissingBean
    public SecurityIdentityService securityIdentityService() {
        return new DefaultSecurityIdentityService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityPermissionService securityPermissionService() {
        return new DefaultSecurityPermissionService();
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
}
