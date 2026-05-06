package com.gallop.fastddd.ai;

import com.gallop.fastddd.ai.autoconfigure.AiAutoConfiguration;
import com.gallop.fastddd.ai.llm.PromptTemplateLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI自动配置测试类（Spring AI 版本）
 *
 * @author gallop
 * @since 1.0.0-SNAPSHOT
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class));

    @Test
    void testAiAutoConfigurationEnabledByDefault() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProperties.class);
                    assertThat(context.getBean(AiProperties.class).isEnabled()).isTrue();
                    // PromptTemplateLoader is created unconditionally when ai.enabled=true
                    assertThat(context).hasSingleBean(PromptTemplateLoader.class);
                });
    }

    @Test
    void testAiAutoConfigurationDisabledWhenPropertyFalse() {
        contextRunner
                .withPropertyValues("fastddd.ai.enabled:false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AiProperties.class);
                });
    }

    @Test
    void testPromptTemplateLoaderCreatedWhenEnabled() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(PromptTemplateLoader.class);
                });
    }

    @Test
    void testAiPropertiesDefaults() {
        contextRunner
                .run(context -> {
                    AiProperties props = context.getBean(AiProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    // LLM 配置已迁移到 spring.ai.openai.*，AiProperties 只保留 enabled 开关
                });
    }
}
