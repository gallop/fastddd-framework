package com.gallop.fastddd.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI功能配置属性
 * <p>
 * 控制FastDDD框架中AI相关功能的开关。
 * LLM 配置（api-key、base-url、model 等）已迁移到 Spring AI 标准配置：
 * spring.ai.openai.*
 * </p>
 *
 * @author gallop
 * @since 1.0.0-SNAPSHOT
 */
@Data
@ConfigurationProperties(prefix = "fastddd.ai")
public class AiProperties {

    /**
     * 是否启用AI功能（主开关）
     * <p>当设置为false时，所有AI功能将被禁用</p>
     */
    private boolean enabled = true;
}
