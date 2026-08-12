package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.advice.SmartResponseAdvice;
import com.gallop.fastddd.web.config.FastDddJacksonAutoConfiguration;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FastDDD Web 自动配置测试
 *
 * <p>WEB-002：响应包装与 Jackson 扩展均为 opt-in（默认关）。
 * 验证「无属性 → 两者均不注册」「显式 opt-in → 注册」的门控机械性。
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
class FastDddWebAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FastDddWebAutoConfiguration.class,
                    FastDddJacksonAutoConfiguration.class,
                    JacksonAutoConfiguration.class));

    @Test
    void shouldAutoConfigureGlobalExceptionHandler() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
        });
    }

    @Test
    void shouldNotOverrideExistingGlobalExceptionHandler() {
        contextRunner
                .withBean("globalExceptionHandler", GlobalExceptionHandler.class, GlobalExceptionHandler::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
                });
    }

    /**
     * WEB-002：默认（无 opt-in 属性）—— 响应包装与 Jackson 扩展均不注册。
     *
     * <p>两者门控均在类级别 {@code @ConditionalOnProperty(..., matchIfMissing=false)}：
     * 属性缺失 → 配置类/advice 不装配 → bean 缺席。「仅引入 fast-ddd-web 不改写既有 JSON」的机械锁定。
     */
    @Test
    void shouldNotRegisterWrapperOrJacksonByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SmartResponseAdvice.class);
            assertThat(context).doesNotHaveBean("fastDddJacksonCustomizer");
        });
    }

    /**
     * WEB-002 opt-in：显式开启 Jackson 扩展后注册 customizer。
     */
    @Test
    void shouldRegisterJacksonCustomizerWhenOptedIn() {
        contextRunner
                .withPropertyValues("fastddd.jackson.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("fastDddJacksonCustomizer");
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context.getBean(ObjectMapper.class)).isNotNull();
                });
    }

    /**
     * WEB-002 opt-in：显式开启响应包装后注册 SmartResponseAdvice。
     */
    @Test
    void shouldRegisterResponseWrapperWhenOptedIn() {
        contextRunner
                .withPropertyValues("fastddd.web.response-wrapper.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmartResponseAdvice.class);
                });
    }
}
