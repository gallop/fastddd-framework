package com.gallop.fastddd.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublishException;
import com.gallop.fastddd.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collection;

/**
 * 事务性事件发布器实现
 * 
 * 实现Outbox模式，将领域事件存储到数据库表中而不是直接发布。
 * 这确保了事件发布与业务事务的原子性。
 * 
 * @author gallop
 * @since 2025-01-09
 */
public class TransactionalEventPublisher implements EventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionalEventPublisher.class);
    
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 构造函数
     * 
     * @param outboxEventRepository Outbox事件仓储
     * @param objectMapper JSON序列化器
     */
    public TransactionalEventPublisher(OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    @Transactional
    public void publishEvent(@NonNull DomainEvent event) {
        Assert.notNull(event, "Event must not be null");
        
        try {
            logger.debug("Storing domain event to outbox: {} with ID: {}", 
                event.getClass().getSimpleName(), event.getEventId());
            
            OutboxEvent outboxEvent = createOutboxEvent(event);
            outboxEvent.initDefaults();
            
            int result = outboxEventRepository.insert(outboxEvent);
            if (result <= 0) {
                throw new EventPublishException(
                    String.format("Failed to store event %s with ID %s to outbox", 
                        event.getClass().getSimpleName(), event.getEventId()));
            }
            
            logger.debug("Successfully stored domain event to outbox: {} with ID: {}", 
                event.getClass().getSimpleName(), event.getEventId());
                
        } catch (Exception e) {
            logger.error("Failed to store domain event to outbox: {} with ID: {}", 
                event.getClass().getSimpleName(), event.getEventId(), e);
            throw new EventPublishException(
                String.format("Failed to store event %s with ID %s to outbox", 
                    event.getClass().getSimpleName(), event.getEventId()), e);
        }
    }
    
    @Override
    @Transactional
    public void publishEvents(@NonNull Collection<? extends DomainEvent> events) {
        Assert.notNull(events, "Events collection must not be null");
        Assert.noNullElements(events, "Events collection must not contain null elements");
        
        if (events.isEmpty()) {
            logger.debug("No events to store to outbox");
            return;
        }
        
        logger.debug("Storing {} domain events to outbox", events.size());
        
        try {
            for (DomainEvent event : events) {
                publishEvent(event);
            }
            
            logger.debug("Successfully stored all {} domain events to outbox", events.size());
            
        } catch (Exception e) {
            logger.error("Failed to store batch of {} domain events to outbox", events.size(), e);
            throw new EventPublishException(
                String.format("Failed to store batch of %d events to outbox", events.size()), e);
        }
    }
    
    @Override
    public void publishEventAsync(@NonNull DomainEvent event) {
        // 对于事务性发布器，异步发布与同步发布行为相同
        // 因为我们只是存储到outbox表，实际的异步处理由后台调度器完成
        publishEvent(event);
    }
    
    @Override
    public void publishEventsAsync(@NonNull Collection<? extends DomainEvent> events) {
        // 对于事务性发布器，异步发布与同步发布行为相同
        // 因为我们只是存储到outbox表，实际的异步处理由后台调度器完成
        publishEvents(events);
    }
    
    @Override
    public boolean hasListeners(@NonNull Class<? extends DomainEvent> eventType) {
        // 对于事务性发布器，我们不直接处理监听器，而是存储到outbox表
        return false;
    }
    
    @Override
    public int getListenerCount(@NonNull Class<? extends DomainEvent> eventType) {
        // 对于事务性发布器，我们不直接处理监听器
        return 0;
    }
    
    /**
     * 创建Outbox事件
     * 
     * @param domainEvent 领域事件
     * @return Outbox事件
     */
    private OutboxEvent createOutboxEvent(DomainEvent domainEvent) {
        try {
            String eventData = objectMapper.writeValueAsString(domainEvent);
            
            return OutboxEvent.builder()
                .eventId(domainEvent.getEventId().toString())
                .aggregateId(domainEvent.getAggregateId())
                .aggregateType(domainEvent.getAggregateType())
                .eventType(domainEvent.getClass().getName())
                .eventData(eventData)
                .status(OutboxEventStatus.PENDING)
                .build();
                
        } catch (JsonProcessingException e) {
            throw new EventPublishException(
                String.format("Failed to serialize event %s with ID %s", 
                    domainEvent.getClass().getSimpleName(), domainEvent.getEventId()), e);
        }
    }
}