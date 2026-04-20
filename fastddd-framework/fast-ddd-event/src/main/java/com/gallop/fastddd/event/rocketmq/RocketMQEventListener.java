package com.gallop.fastddd.event.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RocketMQ 事件监听器
 * 负责接收来自 RocketMQ 的消息并转换为领域事件进行处理
 * 支持重试机制和死信队列处理
 * 
 * @author gallop
 * @since 2025-01-22
 */
@Component
@RocketMQMessageListener(
    topic = "${rocketmq.consumer.topic:domain-events}",
    consumerGroup = "${rocketmq.consumer.group:domain-event-consumer}",
    messageModel = MessageModel.CLUSTERING,
    consumeMode = ConsumeMode.CONCURRENTLY,
    maxReconsumeTimes = 3
)
public class RocketMQEventListener implements RocketMQListener<EventMessage> {

    private static final Logger logger = LoggerFactory.getLogger(RocketMQEventListener.class);

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RocketMQListenerConfig listenerConfig;

    @Override
    public void onMessage(EventMessage eventMessage) {
        logger.info("Received event message: eventId={}, eventType={}, aggregateId={}",
                   eventMessage.getEventId(), eventMessage.getEventType(), eventMessage.getAggregateId());

        try {
            // 处理事件消息
            processEventMessage(eventMessage);

            logger.info("Successfully processed event: eventId={}", eventMessage.getEventId());

        } catch (Exception e) {
            logger.error("Failed to process event message: eventId={}, error={}",
                        eventMessage.getEventId(), e.getMessage(), e);

            // 记录处理失败的事件
            recordFailedEvent(eventMessage, e);

            // 重新抛出异常以触发 RocketMQ 的重试机制
            throw new RuntimeException("Event processing failed", e);
        }
    }

    /**
     * 处理事件消息
     * 
     * @param eventMessage 事件消息
     */
    private void processEventMessage(EventMessage eventMessage) {
        try {
            // 将事件消息转换为领域事件
            DomainEvent domainEvent = convertToEvent(eventMessage);
            
            // 发布领域事件到应用程序上下文
            applicationEventPublisher.publishEvent(domainEvent);
            
        } catch (Exception e) {
            logger.error("Failed to convert or publish event: eventId={}", eventMessage.getEventId(), e);
            throw e;
        }
    }

    /**
     * 将事件消息转换为领域事件
     * 
     * @param eventMessage 事件消息
     * @return 领域事件
     */
    private DomainEvent convertToEvent(EventMessage eventMessage) {
        try {
            // 根据事件类型动态创建对应的领域事件实例
            String eventType = eventMessage.getEventType();
            String eventData = eventMessage.getEventData();
            
            // 创建通用的领域事件实例
            GenericDomainEvent domainEvent = new GenericDomainEvent();
            domainEvent.setEventId(eventMessage.getEventId());
            domainEvent.setEventType(eventType);
            domainEvent.setAggregateId(eventMessage.getAggregateId());
            domainEvent.setAggregateType(eventMessage.getAggregateType());
            domainEvent.setEventData(eventData);
            domainEvent.setOccurredAt(eventMessage.getOccurredAt());
            domainEvent.setVersion(eventMessage.getVersion());
            
            return domainEvent;
            
        } catch (Exception e) {
            logger.error("Failed to convert event message to domain event: eventId={}", 
                        eventMessage.getEventId(), e);
            throw new RuntimeException("Event conversion failed", e);
        }
    }

    /**
     * 记录处理失败的事件
     * 
     * @param eventMessage 事件消息
     * @param error 错误信息
     */
    private void recordFailedEvent(EventMessage eventMessage, Exception error) {
        try {
            logger.warn("Recording failed event: eventId={}, error={}", 
                       eventMessage.getEventId(), error.getMessage());
            
            // 这里可以将失败的事件记录到数据库或其他存储系统
            // 用于后续的监控和分析
            
        } catch (Exception e) {
            logger.error("Failed to record failed event: eventId={}", 
                        eventMessage.getEventId(), e);
        }
    }
}