package com.gallop.fastddd.event;

import com.gallop.fastddd.common.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collection;

/**
 * 基于Spring的事件发布器实现
 *
 * 使用Spring的ApplicationEventPublisher来发布领域事件。
 * 支持同步和异步事件发布，并提供监听器管理功能。
 *
 * @author FastDDD Framework Team
 * @version 1.1.0 - 使用统一的 common.domain.DomainEvent，通过 SpringDomainEventAdapter 适配
 */
public class SpringEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SpringEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ApplicationEventMulticaster eventMulticaster;

    /**
     * 构造函数
     *
     * @param applicationEventPublisher Spring的应用事件发布器
     * @param eventMulticaster Spring的事件多播器（可选）
     */
    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                               ApplicationEventMulticaster eventMulticaster) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.eventMulticaster = eventMulticaster;
    }

    /**
     * 构造函数（仅使用ApplicationEventPublisher）
     *
     * @param applicationEventPublisher Spring的应用事件发布器
     */
    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this(applicationEventPublisher, null);
    }

    @Override
    public void publishEvent(@NonNull DomainEvent event) {
        Assert.notNull(event, "Event must not be null");

        try {
            logger.debug("Publishing domain event: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId());

            // 将 common.domain.DomainEvent 包装为 Spring ApplicationEvent
            SpringDomainEventAdapter springEvent = new SpringDomainEventAdapter(event);
            applicationEventPublisher.publishEvent(springEvent);

            logger.debug("Successfully published domain event: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId());

        } catch (Exception e) {
            logger.error("Failed to publish domain event: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId(), e);
            throw new EventPublishException(
                String.format("Failed to publish event %s with ID %s",
                    event.getClass().getSimpleName(), event.getEventId()), e);
        }
    }

    @Override
    public void publishEvents(@NonNull Collection<? extends DomainEvent> events) {
        Assert.notNull(events, "Events collection must not be null");
        Assert.noNullElements(events, "Events collection must not contain null elements");

        if (events.isEmpty()) {
            logger.debug("No events to publish");
            return;
        }

        logger.debug("Publishing {} domain events", events.size());

        try {
            for (DomainEvent event : events) {
                publishEvent(event);
            }

            logger.debug("Successfully published all {} domain events", events.size());

        } catch (Exception e) {
            logger.error("Failed to publish batch of {} domain events", events.size(), e);
            throw new EventPublishException(
                String.format("Failed to publish batch of %d events", events.size()), e);
        }
    }

    @Override
    @Async
    public void publishEventAsync(@NonNull DomainEvent event) {
        Assert.notNull(event, "Event must not be null");

        try {
            logger.debug("Publishing domain event asynchronously: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId());

            // 将 common.domain.DomainEvent 包装为 Spring ApplicationEvent
            SpringDomainEventAdapter springEvent = new SpringDomainEventAdapter(event);
            applicationEventPublisher.publishEvent(springEvent);

            logger.debug("Successfully published domain event asynchronously: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId());

        } catch (Exception e) {
            logger.error("Failed to publish domain event asynchronously: {} with ID: {}",
                event.getClass().getSimpleName(), event.getEventId(), e);
            // 异步方法中不抛出异常，只记录日志
        }
    }

    @Override
    @Async
    public void publishEventsAsync(@NonNull Collection<? extends DomainEvent> events) {
        Assert.notNull(events, "Events collection must not be null");
        Assert.noNullElements(events, "Events collection must not contain null elements");

        if (events.isEmpty()) {
            logger.debug("No events to publish asynchronously");
            return;
        }

        logger.debug("Publishing {} domain events asynchronously", events.size());

        try {
            for (DomainEvent event : events) {
                publishEventAsync(event);
            }

            logger.debug("Successfully initiated async publishing of all {} domain events", events.size());

        } catch (Exception e) {
            logger.error("Failed to publish batch of {} domain events asynchronously", events.size(), e);
            // 异步方法中不抛出异常，只记录日志
        }
    }

    @Override
    public boolean hasListeners(@NonNull Class<? extends DomainEvent> eventType) {
        Assert.notNull(eventType, "Event type must not be null");

        // 简化实现：由于ApplicationEventMulticaster的API限制，
        // 我们无法直接获取监听器列表，因此总是返回true
        // 实际的监听器检查会在事件发布时由Spring框架处理
        logger.debug("Checking listeners for event type: {} (simplified implementation)",
            eventType.getSimpleName());
        return true;
    }

    @Override
    public int getListenerCount(@NonNull Class<? extends DomainEvent> eventType) {
        Assert.notNull(eventType, "Event type must not be null");

        // 简化实现：由于ApplicationEventMulticaster的API限制，
        // 我们无法直接获取监听器数量，因此返回-1表示未知
        // 实际的监听器管理会由Spring框架处理
        logger.debug("Getting listener count for event type: {} (simplified implementation)",
            eventType.getSimpleName());
        return -1;
    }


}
