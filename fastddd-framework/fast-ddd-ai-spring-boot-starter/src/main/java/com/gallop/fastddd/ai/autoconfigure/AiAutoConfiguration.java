package com.gallop.fastddd.ai.autoconfigure;

import com.gallop.fastddd.ai.AiProperties;
import com.gallop.fastddd.ai.llm.PromptTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI功能自动配置类
 * <p>
 * 配置 Prompt 模板加载器。LLM 客户端由 Spring AI 自动配置
 * （spring-ai-starter-model-openai 自动创建 OpenAiChatModel bean）。
 * </p>
 * <ul>
 *   <li>主开关：fastddd.ai.enabled（默认 true）</li>
 *   <li>LLM 配置：spring.ai.openai.*</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-SNAPSHOT
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "fastddd.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAutoConfiguration {

    public AiAutoConfiguration(AiProperties properties) {
        log.info("FastDDD AI module initialized (Spring AI), enabled: {}", properties.isEnabled());
    }

    @Bean
    @ConditionalOnMissingBean(PromptTemplateLoader.class)
    public PromptTemplateLoader promptTemplateLoader() {
        log.info("Creating Prompt Template Loader");
        return new PromptTemplateLoader();
    }
}
