package com.gallop.fastddd.event.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 死信队列处理器
 * 负责处理重试失败后进入死信队列的消息
 * 
 * @author gallop
 * @since 2025-01-22
 */
@Component
@ConditionalOnProperty(name = "rocketmq.consumer.dlq-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
    topic = "${rocketmq.consumer.dlq-topic:domain-events-dlq}",
    consumerGroup = "${rocketmq.consumer.group:domain-event-consumer}-dlq",
    messageModel = MessageModel.CLUSTERING,
    consumeMode = ConsumeMode.CONCURRENTLY
)
public class DeadLetterQueueHandler implements RocketMQListener<EventMessage> {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueHandler.class);

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RocketMQListenerConfig listenerConfig;

    // 统计信息
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    @Override
    public void onMessage(EventMessage eventMessage) {
        logger.warn("Processing dead letter queue message: eventId={}, eventType={}, aggregateId={}", 
                   eventMessage.getEventId(), eventMessage.getEventType(), eventMessage.getAggregateId());
        
        try {
            // 处理死信队列消息
            processDeadLetterMessage(eventMessage);
            
            processedCount.incrementAndGet();
            logger.info("Successfully processed dead letter message: eventId={}, total processed: {}", 
                       eventMessage.getEventId(), processedCount.get());
            
        } catch (Exception e) {
            failedCount.incrementAndGet();
            logger.error("Failed to process dead letter message: eventId={}, error={}, total failed: {}", 
                        eventMessage.getEventId(), e.getMessage(), failedCount.get(), e);
            
            // 记录彻底失败的消息
            recordPermanentFailure(eventMessage, e);
        }
    }

    /**
     * 处理死信队列消息
     * 
     * @param eventMessage 事件消息
     */
    private void processDeadLetterMessage(EventMessage eventMessage) {
        // 1. 记录死信消息到数据库或日志系统
        recordDeadLetterMessage(eventMessage);
        
        // 2. 发送告警通知
        sendAlertNotification(eventMessage);
        
        // 3. 尝试补偿处理（可选）
        attemptCompensation(eventMessage);
        
        // 4. 更新监控指标
        updateMetrics(eventMessage);
    }

    /**
     * 记录死信消息
     * 
     * @param eventMessage 事件消息
     */
    private void recordDeadLetterMessage(EventMessage eventMessage) {
        try {
            logger.info("Recording dead letter message: eventId={}, eventType={}, aggregateId={}, aggregateType={}", 
                       eventMessage.getEventId(), eventMessage.getEventType(), 
                       eventMessage.getAggregateId(), eventMessage.getAggregateType());
            
            // 这里可以将死信消息记录到专门的数据库表中
            // 用于后续的人工处理或分析
            
            DeadLetterRecord record = new DeadLetterRecord();
            record.setEventId(eventMessage.getEventId());
            record.setEventType(eventMessage.getEventType());
            record.setAggregateId(eventMessage.getAggregateId());
            record.setAggregateType(eventMessage.getAggregateType());
            record.setEventData(eventMessage.getEventData());
            record.setOriginalOccurredAt(eventMessage.getOccurredAt());
            record.setDeadLetterAt(LocalDateTime.now());
            record.setStatus("PENDING_MANUAL_REVIEW");
            
            // 保存到数据库（这里只是示例，实际需要实现数据访问层）
            // deadLetterRepository.save(record);
            
        } catch (Exception e) {
            logger.error("Failed to record dead letter message: eventId={}", eventMessage.getEventId(), e);
        }
    }

    /**
     * 发送告警通知
     * 
     * @param eventMessage 事件消息
     */
    private void sendAlertNotification(EventMessage eventMessage) {
        try {
            logger.warn("Sending alert notification for dead letter message: eventId={}", eventMessage.getEventId());
            
            // 这里可以集成告警系统，如钉钉、邮件、短信等
            // 通知相关人员处理死信消息
            
            String alertMessage = String.format(
                "Dead Letter Queue Alert: Event %s (type: %s) failed to process after maximum retries. "
                + "Aggregate: %s (%s). Please check and handle manually.",
                eventMessage.getEventId(), eventMessage.getEventType(),
                eventMessage.getAggregateId(), eventMessage.getAggregateType()
            );
            
            // alertService.sendAlert(alertMessage);
            
        } catch (Exception e) {
            logger.error("Failed to send alert notification: eventId={}", eventMessage.getEventId(), e);
        }
    }

    /**
     * 尝试补偿处理
     * 
     * @param eventMessage 事件消息
     */
    private void attemptCompensation(EventMessage eventMessage) {
        try {
            logger.info("Attempting compensation for dead letter message: eventId={}", eventMessage.getEventId());
            
            // 根据事件类型执行不同的补偿逻辑
            String eventType = eventMessage.getEventType();
            
            switch (eventType) {
                case "UserCreated":
                    // 用户创建事件的补偿逻辑
                    compensateUserCreatedEvent(eventMessage);
                    break;
                case "OrderPlaced":
                    // 订单创建事件的补偿逻辑
                    compensateOrderPlacedEvent(eventMessage);
                    break;
                default:
                    logger.info("No specific compensation logic for event type: {}", eventType);
                    break;
            }
            
        } catch (Exception e) {
            logger.error("Failed to attempt compensation: eventId={}", eventMessage.getEventId(), e);
        }
    }

    /**
     * 更新监控指标
     * 
     * @param eventMessage 事件消息
     */
    private void updateMetrics(EventMessage eventMessage) {
        try {
            // 更新死信队列相关的监控指标
            // metricsService.incrementDeadLetterCount(eventMessage.getEventType());
            
        } catch (Exception e) {
            logger.error("Failed to update metrics: eventId={}", eventMessage.getEventId(), e);
        }
    }

    /**
     * 记录彻底失败的消息
     * 
     * @param eventMessage 事件消息
     * @param error 错误信息
     */
    private void recordPermanentFailure(EventMessage eventMessage, Exception error) {
        try {
            logger.error("Recording permanent failure: eventId={}, error={}", 
                        eventMessage.getEventId(), error.getMessage());
            
            // 记录彻底失败的消息，需要人工介入
            // permanentFailureRepository.save(eventMessage, error);
            
        } catch (Exception e) {
            logger.error("Failed to record permanent failure: eventId={}", eventMessage.getEventId(), e);
        }
    }

    /**
     * 补偿用户创建事件
     */
    private void compensateUserCreatedEvent(EventMessage eventMessage) {
        // 实现用户创建事件的补偿逻辑
        logger.info("Compensating UserCreated event: {}", eventMessage.getEventId());
    }

    /**
     * 补偿订单创建事件
     */
    private void compensateOrderPlacedEvent(EventMessage eventMessage) {
        // 实现订单创建事件的补偿逻辑
        logger.info("Compensating OrderPlaced event: {}", eventMessage.getEventId());
    }

    /**
     * 获取处理统计信息
     */
    public String getProcessingStats() {
        return String.format("Dead Letter Queue Stats - Processed: %d, Failed: %d", 
                           processedCount.get(), failedCount.get());
    }

    /**
     * 死信记录实体类
     */
    private static class DeadLetterRecord {
        private String eventId;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private String eventData;
        private LocalDateTime originalOccurredAt;
        private LocalDateTime deadLetterAt;
        private String status;

        // Getters and Setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        
        public String getAggregateType() { return aggregateType; }
        public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
        
        public String getEventData() { return eventData; }
        public void setEventData(String eventData) { this.eventData = eventData; }
        
        public LocalDateTime getOriginalOccurredAt() { return originalOccurredAt; }
        public void setOriginalOccurredAt(LocalDateTime originalOccurredAt) { this.originalOccurredAt = originalOccurredAt; }
        
        public LocalDateTime getDeadLetterAt() { return deadLetterAt; }
        public void setDeadLetterAt(LocalDateTime deadLetterAt) { this.deadLetterAt = deadLetterAt; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}