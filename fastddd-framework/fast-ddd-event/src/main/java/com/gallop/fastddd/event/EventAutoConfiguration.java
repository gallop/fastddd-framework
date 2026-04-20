package com.gallop.fastddd.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.TransactionalEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 事件模块自动配置类
 * 
 * 提供事件发布和监听的自动配置，包括：
 * - EventPublisher的默认实现
 * - 异步事件处理支持
 * - 组件扫描配置
 * 
 * @author gallop
 * @since 2025-09-01
 * @version 1.0.0-SNAPSHOT
 */
@AutoConfiguration
@EnableAsync
@EnableScheduling
@ComponentScan(
    basePackages = "com.gallop.fastddd.event",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.gallop\\.fastddd\\.event\\.rocketmq\\..*"
    )
)
public class EventAutoConfiguration {
    
    /**
     * 配置事务性事件发布器（当存在OutboxEventRepository时）
     * 
     * @param outboxEventRepository Outbox事件仓储
     * @param objectMapper JSON序列化器
     * @return TransactionalEventPublisher实例
     */
    @Bean
    @ConditionalOnBean(OutboxEventRepository.class)
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher transactionalEventPublisher(OutboxEventRepository outboxEventRepository,
                                                     ObjectMapper objectMapper) {
        return new TransactionalEventPublisher(outboxEventRepository, objectMapper);
    }
    
    /**
     * 配置默认的事件发布器（当不存在OutboxEventRepository时）
     * 
     * @param applicationEventPublisher Spring的应用事件发布器
     * @return SpringEventPublisher实例
     */
    @Bean
    @ConditionalOnMissingBean({EventPublisher.class, OutboxEventRepository.class})
    public EventPublisher springEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringEventPublisher(applicationEventPublisher);
    }
    
    /**
     * 配置异步任务执行器
     * 
     * @return TaskExecutor实例
     */
    @Bean("taskExecutor")
    @ConditionalOnMissingBean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }
}