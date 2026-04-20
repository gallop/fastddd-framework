package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.config.FastDddJacksonAutoConfiguration;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FastDDD Web自动配置测试
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
    void shouldAutoConfigureFastDddJacksonCustomizer() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("fastDddJacksonCustomizer");
        });
    }

    @Test
    void shouldAutoConfigureObjectMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);

            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertThat(objectMapper).isNotNull();
        });
    }

    @Test
    void shouldNotOverrideExistingGlobalExceptionHandler() {
        contextRunner
                .withBean("globalExceptionHandler", GlobalExceptionHandler.class, GlobalExceptionHandler::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    // 验证只有一个GlobalExceptionHandler bean
                    assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
                });
    }
}