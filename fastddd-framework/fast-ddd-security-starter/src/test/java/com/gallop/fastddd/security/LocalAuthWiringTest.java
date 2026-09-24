package com.gallop.fastddd.security;

import com.gallop.fastddd.security.audit.AuditLogAspect;
import com.gallop.fastddd.security.audit.AuditLogService;
import com.gallop.fastddd.security.code.VerificationCodeRateLimiter;
import com.gallop.fastddd.security.code.VerificationCodeService;
import com.gallop.fastddd.security.code.VerificationCodeStore;
import com.gallop.fastddd.security.jwt.JwtSigner;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityPermissionService;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import com.gallop.fastddd.security.strategy.EmailCodeAuthStrategy;
import com.gallop.fastddd.security.strategy.PasswordAuthStrategy;
import com.gallop.fastddd.security.strategy.SmsCodeAuthStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * W1 本地认证族开关装配契约（评审 R1 / 身份集成方案 §4.5）。
 *
 * <p>用 {@link ApplicationContextRunner}/{@link WebApplicationContextRunner} 验证
 * {@code fastddd.security.local-auth.enabled}（默认 true）与受控族 13 个 Bean 的关系：
 * <ol>
 *   <li><b>缺省零变化</b>：不配置该属性时本地认证族、JWT 基础与默认链全部装配（A 模式基线）。</li>
 *   <li><b>显式关闭整族消失</b>：登录/验证码控制器、认证策略、默认用户名认证、验证码设施
 *       一律不装配；JWT 验证基础、PasswordEncoder、权限 SPI、审计与桥接不受影响；
 *       上下文正常启动——包括宿主无任何认证 Bean 时 AuthenticationManager 为 NullBean
 *       的组合（唯一消费方 PasswordAuthStrategy 已被门控，无注入点）。</li>
 *   <li><b>宿主基础 Bean 不复活族</b>：宿主自带 PasswordEncoder/VerificationCodeService/
 *       AuthenticationManager 时框架族仍不装配。</li>
 *   <li><b>宿主自定义链 ≠ 关闭框架认证入口</b>：链退让但本地认证族照常装配。</li>
 *   <li><b>冲突 fail-fast</b>：local-auth 显式 false 且 code.dev-mode 显式 true 时
 *       启动失败并指名两个属性。</li>
 * </ol>
 * 控制器级（ApiResponse 在 classpath 时的 AuthController/VerificationCodeController）
 * 由消费者场景 security-local-auth-off 覆盖；本模块测试 classpath 无 fast-ddd-web。
 *
 * @author gallop
 * @since 1.1.0（W1 安全装配解耦）
 */
class LocalAuthWiringTest {

    /** 合法测试 secret：≥64 UTF-8 字节、不含占位符片段（沿用 JwtEnableConditionWiringTest 口径）。 */
    private static final String VALID_TEST_SECRET =
            "test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm";

    /** jwt 缺省启用的场景：需要 Web 运行器 + MvcRequestMatcher 支撑 + 合法 secret（见 JwtEnableConditionWiringTest）。 */
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddSecurityAutoConfiguration.class))
            .withUserConfiguration(MvcMatcherSupport.class)
            .withPropertyValues(
                    "fastddd.security.http.permit-all-paths=",
                    "fastddd.security.jwt.secret=" + VALID_TEST_SECRET);

    /** jwt 显式关闭的场景：SecurityAutoConfiguration 整类退避，无需 Web 环境与 secret。 */
    private final ApplicationContextRunner jwtOffRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddSecurityAutoConfiguration.class));

    @Nested
    @DisplayName("缺省：与旧版行为一致")
    class DefaultAllOn {

        @Test
        @DisplayName("不配置 local-auth → 本地认证族全量装配 + JWT 基础在（A 模式基线）")
        void defaultAssemblesFullLocalAuthFamily() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                // 受控族（单元 classpath 可见部分）
                assertThat(context).hasSingleBean(SecurityIdentityService.class);
                assertThat(context).hasSingleBean(UserDetailsService.class);
                assertThat(context).hasSingleBean(AuthStrategyRegistry.class);
                assertThat(context).hasSingleBean(PasswordAuthStrategy.class);
                assertThat(context).hasSingleBean(VerificationCodeService.class);
                assertThat(context).hasSingleBean(VerificationCodeStore.class);
                assertThat(context).hasSingleBean(VerificationCodeRateLimiter.class);
                assertThat(context).hasSingleBean(SmsCodeAuthStrategy.class);
                assertThat(context).hasSingleBean(EmailCodeAuthStrategy.class);
                // 不受控项
                assertThat(context).hasSingleBean(JwtSigner.class);
                assertThat(context).hasSingleBean(JwtTokenProvider.class);
                assertThat(context).hasSingleBean(SecurityPermissionService.class);
                assertThat(context).hasSingleBean(AuditLogService.class);
                assertThat(context).hasSingleBean(AuditLogAspect.class);
                assertThat(context).hasSingleBean(SecurityFilterChain.class);
            });
        }
    }

    @Nested
    @DisplayName("显式关闭：整族消失，基础能力保留")
    class LocalAuthOff {

        @Test
        @DisplayName("local-auth=false（jwt 缺省 true、无宿主认证 Bean）→ 族全消失、上下文正常启动（含 NullBean 组合）")
        void off_disablesFamilyAndKeepsJwtInfra() {
            runner
                    .withPropertyValues("fastddd.security.local-auth.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(SecurityIdentityService.class);
                        assertThat(context).doesNotHaveBean(UserDetailsService.class);
                        assertThat(context).doesNotHaveBean(AuthStrategyRegistry.class);
                        assertThat(context).doesNotHaveBean(PasswordAuthStrategy.class);
                        assertThat(context).doesNotHaveBean(VerificationCodeService.class);
                        assertThat(context).doesNotHaveBean(VerificationCodeStore.class);
                        assertThat(context).doesNotHaveBean(VerificationCodeRateLimiter.class);
                        assertThat(context).doesNotHaveBean(SmsCodeAuthStrategy.class);
                        assertThat(context).doesNotHaveBean(EmailCodeAuthStrategy.class);
                        // JWT 验证基础/权限 SPI/审计/桥接/默认链不受 local-auth 影响
                        assertThat(context).hasSingleBean(JwtSigner.class);
                        assertThat(context).hasSingleBean(JwtTokenProvider.class);
                        assertThat(context).hasSingleBean(SecurityPermissionService.class);
                        assertThat(context).hasSingleBean(AuditLogService.class);
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    });
        }

        @Test
        @DisplayName("local-auth=false + 宿主基础 Bean → 宿主 Bean 存在且框架族不复活")
        void off_hostBaseBeansDoNotReassembleFamily() {
            runner
                    .withPropertyValues("fastddd.security.local-auth.enabled=false")
                    .withUserConfiguration(HostBaseBeans.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(PasswordEncoder.class);
                        assertThat(context.getBeanNamesForType(VerificationCodeService.class)).hasSize(1);
                        // 宿主 AM 生效（@Primary）；框架 authenticationManager 为 NullBean，其名仍出现在
                        // 按类型查找中（实测），故以宿主 Bean 名存在性断言，不数总数
                        assertThat(context).hasBean("hostAuthenticationManager");
                        // 框架族仍不装配
                        assertThat(context).doesNotHaveBean(PasswordAuthStrategy.class);
                        assertThat(context).doesNotHaveBean(SmsCodeAuthStrategy.class);
                        assertThat(context).doesNotHaveBean(EmailCodeAuthStrategy.class);
                        assertThat(context).doesNotHaveBean(AuthStrategyRegistry.class);
                        assertThat(context).doesNotHaveBean(SecurityIdentityService.class);
                    });
        }

        @Test
        @DisplayName("jwt=false + local-auth=false → SecurityAutoConfiguration 退让且族不装配，上下文启动")
        void off_withJwtOff_leavesNothingFrameworkAssembled() {
            jwtOffRunner
                    .withPropertyValues(
                            "fastddd.security.jwt.enabled=false",
                            "fastddd.security.local-auth.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(JwtSigner.class);
                        assertThat(context).doesNotHaveBean(JwtTokenProvider.class);
                        assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
                        assertThat(context).doesNotHaveBean(UserDetailsService.class);
                        assertThat(context).doesNotHaveBean(SecurityIdentityService.class);
                        assertThat(context).doesNotHaveBean(VerificationCodeService.class);
                        assertThat(context).doesNotHaveBean(VerificationCodeStore.class);
                    });
        }
    }

    @Nested
    @DisplayName("宿主自定义链：不关闭框架认证入口")
    class HostChain {

        @Test
        @DisplayName("宿主提供 SecurityFilterChain（local-auth 缺省）→ 链退让但本地认证族照常装配")
        void hostChainDoesNotDisableLocalAuthFamily() {
            runner
                    .withUserConfiguration(ConsumerSecurityChain.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                        assertThat(context.getBean(SecurityFilterChain.class))
                                .isSameAs(context.getBean(ConsumerSecurityChain.class).consumerChain);
                        // 链退让 ≠ 族消失：必须显式 local-auth=false 才关族
                        assertThat(context).hasSingleBean(VerificationCodeService.class);
                        assertThat(context).hasSingleBean(PasswordAuthStrategy.class);
                        assertThat(context).hasSingleBean(AuthStrategyRegistry.class);
                    });
        }
    }

    @Nested
    @DisplayName("冲突与 dev-mode")
    class Conflict {

        @Test
        @DisplayName("local-auth=false + code.dev-mode=true → 启动失败，异常指名两个属性")
        void devModeWithLocalAuthOffFailsFast() {
            runner
                    .withPropertyValues(
                            "fastddd.security.local-auth.enabled=false",
                            "fastddd.security.code.dev-mode=true")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(rootCauseMessage(context.getStartupFailure()))
                                .contains("fastddd.security.local-auth.enabled")
                                .contains("fastddd.security.code.dev-mode");
                    });
        }

        @Test
        @DisplayName("code.dev-mode=true（local-auth 缺省）→ Noop sender 正常装配，不误伤")
        void devModeAloneStillAssemblesNoopSenders() {
            runner
                    .withPropertyValues("fastddd.security.code.dev-mode=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasBean("noopSmsVerificationCodeSender");
                        assertThat(context).hasBean("noopEmailVerificationCodeSender");
                    });
        }
    }

    /** 提取启动失败异常链的根因消息，用于断言 fail-fast 文案。 */
    private static String rootCauseMessage(Throwable failure) {
        Throwable t = failure;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? "" : t.getMessage();
    }

    /** 模拟宿主自带的认证基础 Bean（允许存在，但不得触发框架本地认证族）。 */
    @Configuration
    static class HostBaseBeans {
        @Bean
        PasswordEncoder hostPasswordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        VerificationCodeService hostVerificationCodeService() {
            return mock(VerificationCodeService.class);
        }

        @Bean
        @Primary // 框架 authenticationManager 在 jwt=true 时无条件注册（1.0.0 既有行为），宿主自有 AM 需 @Primary 消歧
        AuthenticationManager hostAuthenticationManager() {
            return mock(AuthenticationManager.class);
        }
    }

    /** 模拟宿主自定义安全链。 */
    @Configuration
    static class ConsumerSecurityChain {
        SecurityFilterChain consumerChain = mock(SecurityFilterChain.class);

        @Bean
        SecurityFilterChain consumerSecurityFilterChain() {
            return consumerChain;
        }
    }

    /** 为默认链构建提供 MvcRequestMatcher 所需的 HandlerMappingIntrospector Bean（沿用既有口径）。 */
    @Configuration
    static class MvcMatcherSupport {
        @Bean
        org.springframework.web.servlet.handler.HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new org.springframework.web.servlet.handler.HandlerMappingIntrospector();
        }
    }
}
