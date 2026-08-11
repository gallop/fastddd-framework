package com.gallop.fastddd.event.rocketmq;

import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 事件自动配置（EVT-003 重设计）。
 *
 * <p>仅在 RocketMQ 存在于 classpath 时才加载。EVT-003 后本配置<b>不再装配扫描/调度逻辑</b>——
 * 原 {@code RocketMQPublisher}（双 @Scheduled 自行扫描 outbox 表 + 投递 + 重试）已删除，
 * 调度/状态机职责移交框架内置的 {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher}。
 * 本配置仅注册 RocketMQ 作为 {@link OutboxTransport} 实现（仅投递单事件，ADR-003 §2.1）。
 *
 * <p>装配条件：{@code rocketmq.enabled=true} + RocketMQTemplate bean 在场 + 用户显式选择
 * {@code fastddd.event.outbox.transport=rocketmq}。三者满足时提供 {@link RocketMqOutboxTransport}
 * 覆盖 {@code OutboxDispatcherConfiguration} 的 local 默认传输。
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
     * 注册 RocketMQ 作为 Outbox 传输（EVT-003）。
     *
     * <p>取代原 {@code rocketMQPublisher} bean。仅投递（{@link RocketMqOutboxTransport#send}），
     * 不扫描 outbox 表、不创建存储、不改事件状态——全部由 OutboxDispatcher 驱动。
     *
     * <p>装配需同时满足：RocketMQTemplate bean 在场 + 显式 {@code fastddd.event.outbox.transport=rocketmq}。
     * 后者确保用户未选 RocketMQ 时不抢占 local 默认传输。
     *
     * @param rocketMQTemplate RocketMQ模板
     * @param rocketMQConfig   RocketMQ配置
     * @return RocketMqOutboxTransport实例
     */
    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnProperty(prefix = "fastddd.event.outbox", name = "transport", havingValue = "rocketmq")
    @ConditionalOnMissingBean(OutboxTransport.class)
    public RocketMqOutboxTransport rocketMqOutboxTransport(RocketMQTemplate rocketMQTemplate,
                                                           RocketMQConfig rocketMQConfig) {
        return new RocketMqOutboxTransport(rocketMQTemplate, rocketMQConfig);
    }
}

