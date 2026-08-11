package com.gallop.fastddd.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事件模块配置属性。
 *
 * <p>EVT-001：显式 local/outbox 模式开关。此前 EventAutoConfiguration 通过宽
 * {@code @ComponentScan("com.gallop.fastddd.event")} 无条件拾取 outbox 子包，
 * 导致 local 场景（无 DataSource）下 OutboxEventServiceImpl 因缺 ObjectMapper/DataSource
 * 装配失败。现以 {@code fastddd.event.mode} 显式门禁：仅 {@code outbox} 模式且依赖满足
 * （DataSource + MyBatis 在场）时才装配 Outbox 链路，local 模式（默认）零 Outbox Bean。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Data
@ConfigurationProperties(prefix = "fastddd.event")
public class EventProperties {

    /**
     * 事件模式：
     * <ul>
     *   <li>{@code local}（默认）：基于 Spring {@link org.springframework.context.ApplicationEventPublisher}
     *       的本地事件发布，无数据库依赖。</li>
     *   <li>{@code outbox}：事务性 Outbox 持久化，需 DataSource + MyBatis 在场。</li>
     * </ul>
     */
    private String mode = "local";
}
