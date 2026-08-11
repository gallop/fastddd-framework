package com.gallop.fastddd.event;

import com.gallop.fastddd.event.outbox.OutboxEventConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 事件模块自动配置类。
 *
 * <p>EVT-001 装配边界修复：
 * <ul>
 *   <li><b>移除宽 {@code @ComponentScan("com.gallop.fastddd.event")}</b>：此前该扫描覆盖
 *       outbox 子包，无条件拾取 {@link OutboxEventConfiguration}（@Configuration）与
 *       {@code OutboxEventServiceImpl}（@Service），导致 local 场景（无 DataSource）下
 *       Outbox 链路被强制装配并因缺 ObjectMapper/DataSource 失败。改为显式 @Bean 声明
 *       event-core，Outbox 链路由 {@link OutboxEventConfiguration} 自带条件门禁。</li>
 *   <li><b>显式模式开关</b>：{@link EventProperties#getMode()}（local 默认 / outbox）。
 *       {@code springEventPublisher} 仅在 local 模式装配；outbox 模式由
 *       {@link OutboxEventConfiguration}（mode=outbox + DataSource + MyBatis）装配
 *       {@code transactionalEventPublisher}。</li>
 *   <li><b>模块专用 executor 名</b>：{@code taskExecutor}（与 Spring Boot 默认
 *       {@code applicationTaskExecutor} 冲突的通用名）→ {@code eventTaskExecutor}。</li>
 * </ul>
 *
 * @author gallop
 * @since 2025-09-01
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(EventProperties.class)
public class EventAutoConfiguration {

    /**
     * 本地事件发布器（local 模式，默认）。
     *
     * <p>基于 Spring {@link ApplicationEventPublisher}，无数据库依赖。
     * outbox 模式下退让（{@link OutboxEventConfiguration} 提供
     * {@code transactionalEventPublisher} 作为 {@link EventPublisher}）。
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    @ConditionalOnProperty(
            prefix = "fastddd.event", name = "mode", havingValue = "local", matchIfMissing = true)
    public EventPublisher springEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringEventPublisher(applicationEventPublisher);
    }

    /**
     * 事件模块专用异步任务执行器。
     *
     * <p>EVT-001：bean 名由通用 {@code taskExecutor} 改为模块专用 {@code eventTaskExecutor}，
     * 避免与 Spring Boot 默认 {@code applicationTaskExecutor} 或业务 executor 互相抑制。
     * 业务方可定义同名 bean 覆盖。
     */
    @Bean("eventTaskExecutor")
    @ConditionalOnMissingBean(name = "eventTaskExecutor")
    public TaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }
}
