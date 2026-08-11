package com.gallop.fastddd.security;

import com.gallop.fastddd.security.controller.AuthController;
import com.gallop.fastddd.security.jwt.JwtSigner;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SEC-002 装配契约验证。
 *
 * <p>用 {@link ApplicationContextRunner}/{@link WebApplicationContextRunner} 验证三组行为：
 * <ol>
 *   <li><b>disabled 完全关闭</b>：{@code fastddd.security.jwt.enabled=false}（无 secret）时，
 *       不创建任何 JWT Bean（{@link JwtSigner}/{@link JwtTokenProvider}）与认证端点
 *       （{@link AuthController}），上下文正常启动；最小消费者无需 secret 即可运行。</li>
 *   <li><b>disabled 下消费方自定义安全链胜出</b>：框架不再装配自己的 SecurityFilterChain，
 *       消费方提供的链成为唯一链。</li>
 *   <li><b>enabled fail-fast</b>：启用状态下，secret 缺失/过短/明显占位符值一律拒绝启动，
 *       并在异常消息中指明配置路径 {@code fastddd.security.jwt.secret}。</li>
 * </ol>
 *
 * <p>本测试替代原先 {@code @Disabled} 的 {@code SecurityIntegrationTest}，以切片方式覆盖
 * 自动配置装配（无需启动完整 @SpringBootTest Web 上下文）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class JwtEnableConditionWiringTest {

    /** 合法测试 secret：≥64 UTF-8 字节、且不含占位符片段（不触发 SEC-002 占位符校验）。 */
    private static final String VALID_TEST_SECRET =
            "test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm";

    /** disabled 场景：SecurityAutoConfiguration 退避，无需 Web 环境，使用轻量上下文运行器。 */
    private final ApplicationContextRunner disabledRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddSecurityAutoConfiguration.class));

    /**
     * enabled 场景：SecurityAutoConfiguration 激活 @EnableWebSecurity，需要 Web 环境运行器。
     *
     * <p>注册 {@link HandlerMappingIntrospector} 以满足 {@code filterChain} 内
     * {@code requestMatchers} 对 {@code MvcRequestMatcher} 的依赖（spring-webmvc 在类路径上时
     * Spring Security 6 强制要求该 Bean）——这属于 MVC 集成范畴，非 SEC-002 JWT 装配契约；
     * 同时将 {@code permit-all-paths} 置空以隔离 String 路径匹配的额外内部行为。
     */
    private final WebApplicationContextRunner enabledRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddSecurityAutoConfiguration.class))
            .withUserConfiguration(MvcMatcherSupport.class)
            .withPropertyValues("fastddd.security.http.permit-all-paths=");

    @Nested
    @DisplayName("disabled：JWT 完全关闭")
    class Disabled {

        @Test
        @DisplayName("enabled=false 且无 secret → 正常启动，不创建 JWT Bean 与认证端点")
        void disabled_startsAndCreatesNoJwtBeansOrAuthEndpoint() {
            disabledRunner
                    .withPropertyValues("fastddd.security.jwt.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(JwtSigner.class);
                        assertThat(context).doesNotHaveBean(JwtTokenProvider.class);
                        // AuthController 由 ApiResponse 类存在 + enabled 双重门控；disabled 下一律不创建
                        assertThat(context).doesNotHaveBean(AuthController.class);
                    });
        }

        @Test
        @DisplayName("enabled=false 下消费方自定义 SecurityFilterChain 胜出（框架不装配自己的链）")
        void disabled_consumerSecurityFilterChainWins() {
            disabledRunner
                    .withPropertyValues("fastddd.security.jwt.enabled=false")
                    .withUserConfiguration(ConsumerSecurityChain.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        // 框架 SecurityAutoConfiguration 退避，仅有消费方提供的唯一链
                        assertThat(context).hasSingleBean(SecurityFilterChain.class);
                        assertThat(context.getBean(SecurityFilterChain.class))
                                .isSameAs(context.getBean(ConsumerSecurityChain.class).consumerChain);
                    });
        }
    }

    @Nested
    @DisplayName("enabled：合法 secret 正常装配")
    class Enabled {

        @Test
        @DisplayName("enabled=true + 合法 secret → 装配 JwtSigner 与 JwtTokenProvider")
        void enabled_validSecret_wiresJwtBeans() {
            enabledRunner
                    .withPropertyValues(
                            "fastddd.security.jwt.enabled=true",
                            "fastddd.security.jwt.secret=" + VALID_TEST_SECRET)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(JwtSigner.class);
                        assertThat(context).hasSingleBean(JwtTokenProvider.class);
                    });
        }
    }

    @Nested
    @DisplayName("enabled：fail-fast 拒绝不安全 secret")
    class EnabledFailFast {

        @Test
        @DisplayName("缺少 secret → 启动失败，异常指明 fastddd.security.jwt.secret")
        void missingSecret_failsFast() {
            enabledRunner
                    .withPropertyValues("fastddd.security.jwt.enabled=true")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(rootCauseMessage(context.getStartupFailure()))
                                .contains("fastddd.security.jwt.secret");
                    });
        }

        @Test
        @DisplayName("secret 长度不足 → 启动失败")
        void shortSecret_failsFast() {
            enabledRunner
                    .withPropertyValues(
                            "fastddd.security.jwt.enabled=true",
                            "fastddd.security.jwt.secret=too-short")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(rootCauseMessage(context.getStartupFailure()))
                                .contains("fastddd.security.jwt.secret")
                                .contains("64");
                    });
        }

        @Test
        @DisplayName("secret 为明显占位符值 → 启动失败")
        void placeholderSecret_failsFast() {
            // 用占位符片段填充到 ≥64 字节以隔离"占位符"检查（避免被长度检查抢先命中）
            String placeholder = "your-secret-here-please-replace-before-production-use-xxxxxxxxxxxxx";
            assertThat(placeholder.getBytes()).hasSizeGreaterThanOrEqualTo(JwtProperties.MINIMUM_HMAC_SECRET_BYTES);

            enabledRunner
                    .withPropertyValues(
                            "fastddd.security.jwt.enabled=true",
                            "fastddd.security.jwt.secret=" + placeholder)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(rootCauseMessage(context.getStartupFailure()))
                                .contains("placeholder");
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

    /** 模拟消费方在 disabled 下自带的安全链。 */
    @Configuration
    static class ConsumerSecurityChain {
        SecurityFilterChain consumerChain = mock(SecurityFilterChain.class);

        @Bean
        SecurityFilterChain consumerSecurityFilterChain() {
            return consumerChain;
        }
    }

    /** 为 enabled 场景提供 MvcRequestMatcher 所需的 HandlerMappingIntrospector Bean。 */
    @Configuration
    static class MvcMatcherSupport {
        @Bean
        org.springframework.web.servlet.handler.HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new org.springframework.web.servlet.handler.HandlerMappingIntrospector();
        }
    }
}
