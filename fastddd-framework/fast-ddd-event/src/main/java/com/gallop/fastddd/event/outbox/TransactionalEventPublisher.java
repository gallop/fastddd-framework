package com.gallop.fastddd.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublishException;
import com.gallop.fastddd.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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

        } catch (DuplicateKeyException e) {
            // EVT-002 步骤4：eventId UNIQUE 触发的重复插入视为幂等成功（事件已落库），不抛异常。
            // 保证生产者重投递 / 事务边界外重试不会因唯一约束报错而中断业务事务。
            logger.info("Outbox event already stored (idempotent skip): type={}, eventId={}",
                event.getClass().getSimpleName(), event.getEventId());
        } catch (Exception e) {
            if (isDuplicateKeyViolation(e)) {
                logger.info("Outbox event already stored (idempotent skip, wrapped): type={}, eventId={}",
                    event.getClass().getSimpleName(), event.getEventId());
                return;
            }
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
     * 判断异常链是否为唯一约束（eventId UNIQUE）违反（EVT-002 步骤4）。
     *
     * <p>Spring 通常将 SQLSTATE 23505 翻译为 {@link DuplicateKeyException} 由上层 catch 直接命中；
     * 此方法兜底 MyBatis-Plus 偶发的未翻译原生异常（按 SQLSTATE 23505 / 异常类名启发式判定）。
     */
    private static boolean isDuplicateKeyViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof DuplicateKeyException) {
                return true;
            }
            String name = cur.getClass().getName();
            // SQLSTATE 23505 = unique violation（H2/PostgreSQL/MySQL 通用）
            if (name.endsWith("JdbcSQLIntegrityConstraintViolationException")
                    || name.endsWith("SQLIntegrityConstraintViolationException")
                    || (cur.getMessage() != null && cur.getMessage().contains("23505"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
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