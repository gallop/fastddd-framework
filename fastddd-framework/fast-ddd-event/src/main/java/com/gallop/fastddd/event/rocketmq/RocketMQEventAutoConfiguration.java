package com.gallop.fastddd.event.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 事件自动配置
 * <p>
 * 仅在 RocketMQ 存在于 classpath 时才加载
 *
 * @author gallop
 * @since 2025-01-24
 */
@AutoConfiguration
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = false)
public class RocketMQEventAutoConfiguration {

    /**
     * RocketMQ发布器配置
     */
    @Bean
    @ConditionalOnMissingBean(RocketMQConfig.class)
    @ConfigurationProperties(prefix = "rocketmq.publisher")
    public RocketMQConfig rocketMQConfig() {
        return new RocketMQConfig();
    }

    /**
     * RocketMQ监听器配置
     */
    @Bean
    @ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(RocketMQListenerConfig.class)
    @ConfigurationProperties(prefix = "rocketmq.consumer")
    public RocketMQListenerConfig rocketMQListenerConfig() {
        return new RocketMQListenerConfig();
    }

    /**
     * 配置RocketMQ消息发布器
     *
     * @param outboxEventRepository Outbox事件仓储
     * @param rocketMQTemplate RocketMQ模板
     * @param rocketMQConfig RocketMQ配置
     * @param objectMapper JSON序列化器
     * @return RocketMQPublisher实例
     */
    @Bean
    @ConditionalOnBean({OutboxEventRepository.class, RocketMQTemplate.class})
    @ConditionalOnMissingBean(RocketMQPublisher.class)
    public RocketMQPublisher rocketMQPublisher(OutboxEventRepository outboxEventRepository,
                                              RocketMQTemplate rocketMQTemplate,
                                              RocketMQConfig rocketMQConfig,
                                              ObjectMapper objectMapper) {
        return new RocketMQPublisher(outboxEventRepository, rocketMQTemplate, rocketMQConfig, objectMapper);
    }
}
