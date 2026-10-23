package com.gallop.fastddd.identity.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * identity 组合守卫自动配置：**无条件注册**（B 零副作用承诺下的唯一 unconditional
 * Bean——纯校验对象，不装任何功能件），属性级 fail-fast 见 {@link IdentityModeGuard}。
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(FastDddIdentityProperties.class)
public class IdentityModeGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IdentityModeGuard identityModeGuard(org.springframework.core.env.Environment environment) {
        return new IdentityModeGuard(environment);
    }
}
