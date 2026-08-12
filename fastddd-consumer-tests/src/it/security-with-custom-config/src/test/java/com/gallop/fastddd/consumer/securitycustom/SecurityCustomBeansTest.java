package com.gallop.fastddd.consumer.securitycustom;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-003 验收：业务自定义安全 Bean 未被框架覆盖。
 *
 * <p>业务定义了 CustomSecurityConfig（SecurityFilterChain + PasswordEncoder）和
 * CustomCurrentUserProvider（CurrentUserProvider）。启动后验证：
 * (1) PasswordEncoder 是业务自定义实例（CUSTOM-BCRYPT 前缀）；(2) SecurityFilterChain
 * 来自业务配置（permit-all）；(3) CurrentUserProvider 返回业务用户 ID。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityCustomBeansTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Test
    void customPasswordEncoderIsUsed() {
        String encoded = passwordEncoder.encode("test");
        assertThat(encoded).startsWith(CustomSecurityConfig.CUSTOM_ENCODER_PREFIX);
        assertThat(passwordEncoder.matches("test", encoded)).isTrue();
    }

    @Test
    void customCurrentUserProviderIsUsed() {
        assertThat(currentUserProvider.getCurrentUserId())
                .isEqualTo(CustomCurrentUserProvider.CUSTOM_USER_ID);
    }

    @Test
    void frameworkSecurityFilterChainIsNotRegistered() {
        // 框架 SecurityFilterChain 因 business 已定义而退让——
        // 不应有第二个 SecurityFilterChain bean。
        String[] chains = context.getBeanNamesForType(SecurityFilterChain.class);
        assertThat(chains).hasSize(1);
    }

    @Test
    void contextLoadsWithoutConflict() {
        // 上下文启动 = 无 Bean 冲突（NoSuchBeanDefinitionException / 循环依赖等）
        assertThat(context.containsBean("customFilterChain")).isTrue();
    }
}
