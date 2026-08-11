package com.gallop.fastddd.event.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import java.time.LocalDateTime;
import java.util.List;

// 导入RocketMQTemplate用于类型检查
import org.apache.rocketmq.spring.core.RocketMQTemplate;

/**
 * RocketMQ消息发布服务
 * 负责从outbox表读取事件数据并将其作为消息发布到RocketMQ主题
 * 注意：不使用 @Service 注解，Bean 由 EventAutoConfiguration 中的 @Bean 方法创建
 * @author gallop
 * @since 2025-01-22
 */
public class RocketMQPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RocketMQPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final Object rocketMQTemplate; // 使用Object类型以支持测试
    private final RocketMQConfig rocketMQConfig;
    private final ObjectMapper objectMapper;

    public RocketMQPublisher(OutboxEventRepository outboxEventRepository,
                           Object rocketMQTemplate,
                           RocketMQConfig rocketMQConfig,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.rocketMQConfig = rocketMQConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时发布待处理的事件
     * 每隔指定时间间隔执行一次
     * 使用悲观锁防止多实例同时处理相同事件
     */
    @Scheduled(fixedDelayString = "${rocketmq.publisher.interval:5000}")
    public void publishPendingEvents() {
        if (!rocketMQConfig.isScheduledPublishEnabled()) {
            return;
        }

        try {
            long pendingCount = outboxEventRepository.countEventsWithStatusPending();
            if (pendingCount == 0) {
                logger.debug("No pending events to publish");
                return;
            }

            logger.info("Found {} pending events to publish", pendingCount);
            
            // 使用悲观锁查询待处理事件，防止多实例并发处理
            List<OutboxEvent> pendingEvents = outboxEventRepository.selectPendingEventsWithLock(LocalDateTime.now(), rocketMQConfig.getBatchSize());
            
            if (pendingEvents.isEmpty()) {
                logger.debug("No events available for processing (may be locked by other instances)");
                return;
            }
            
            int successCount = 0;
            int failureCount = 0;
            
            for (OutboxEvent event : pendingEvents) {
                try {
                    if (publishEvent(event)) {
                        outboxEventRepository.updateEventStatus(event.getEventId(), OutboxEventStatus.PUBLISHED, LocalDateTime.now());
                        successCount++;
                        logger.debug("Successfully published event: {}", event.getEventId());
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    handlePublishFailure(event, e);
                    failureCount++;
                }
            }
            
            logger.info("Event publishing completed. Success: {}, Failure: {}", successCount, failureCount);
            
        } catch (Exception e) {
            logger.error("Error occurred while publishing pending events", e);
        }
    }

    /**
     * 发布单个事件到RocketMQ
     * @param event 要发布的事件
     * @return 发布是否成功
     */
    public boolean publishEvent(OutboxEvent event) {
        try {
            String destination = rocketMQConfig.getDefaultTopic() + ":" + determineTag(event);
            
            EventMessage eventMessage = buildEventMessage(event);
            
            Message<EventMessage> message = MessageBuilder
                    .withPayload(eventMessage)
                    .setHeader("KEYS", event.getEventId())
                    .setHeader("TAGS", determineTag(event))
                    .build();
            
            // 发送消息到RocketMQ
            try {
                Method syncSendMethod = rocketMQTemplate.getClass().getMethod("syncSend", String.class, Object.class, long.class);
                syncSendMethod.invoke(rocketMQTemplate, destination, message, rocketMQConfig.getSendTimeout());
            } catch (java.lang.reflect.InvocationTargetException e) {
                // 如果是调用目标方法时的异常，重新抛出原始异常
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else if (cause instanceof Exception) {
                    throw new RuntimeException(cause);
                } else {
                    throw new RuntimeException(e);
                }
            } catch (Exception reflectionException) {
                // 如果反射失败，尝试直接转换调用
                ((RocketMQTemplate) rocketMQTemplate).syncSend(destination, message, rocketMQConfig.getSendTimeout());
            }
            
            logger.debug("Event published successfully: {} to destination: {}", event.getEventId(), destination);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to publish event: {}", event.getEventId(), e);
            return false;
        }
    }

    /**
     * 构建事件消息
     * @param event outbox事件
     * @return 事件消息
     */
    public EventMessage buildEventMessage(OutboxEvent event) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setEventId(event.getEventId());
        eventMessage.setEventType(event.getEventType());
        eventMessage.setAggregateId(event.getAggregateId());
        eventMessage.setAggregateType(event.getAggregateType());
        eventMessage.setEventData(event.getEventData());
        eventMessage.setOccurredAt(event.getCreatedAt());
        eventMessage.setSentAt(LocalDateTime.now());
        eventMessage.setVersion("1.0");
        return eventMessage;
    }

    /**
     * 确定消息的标签
     * @param event outbox事件
     * @return 标签字符串
     */
    public String determineTag(OutboxEvent event) {
        return event.getAggregateType() + "." + event.getEventType();
    }

    /**
     * 确定消息的主题和标签
     * @param event outbox事件
     * @return 主题和标签的组合字符串，格式为 "topic:tag"
     */
    public String determineTopicAndTag(OutboxEvent event) {
        return rocketMQConfig.getDefaultTopic() + ":" + determineTag(event);
    }

    /**
     * 重试失败的事件
     * 使用悲观锁防止多实例同时重试相同事件
     */
    @Scheduled(fixedDelayString = "${rocketmq.publisher.retry-interval:30000}")
    public void retryFailedEvents() {
        if (!rocketMQConfig.isScheduledPublishEnabled()) {
            return;
        }

        try {
            long failedCount = outboxEventRepository.countFailedEvents();
            if (failedCount == 0) {
                logger.debug("No failed events to retry");
                return;
            }

            logger.info("Found {} failed events to retry", failedCount);
            
            // 使用悲观锁查询失败事件，防止多实例并发重试
            List<OutboxEvent> failedEvents = outboxEventRepository.selectFailedEventsWithLock(LocalDateTime.now(), rocketMQConfig.getBatchSize());
            
            if (failedEvents.isEmpty()) {
                logger.debug("No failed events available for retry (may be locked by other instances)");
                return;
            }
            
            int retrySuccessCount = 0;
            int retryFailureCount = 0;
            
            for (OutboxEvent event : failedEvents) {
                // 检查是否超过最大重试次数
                if (event.getRetryCount() >= rocketMQConfig.getMaxRetries()) {
                    logger.warn("Event {} has exceeded max retry count ({}), skipping", 
                        event.getEventId(), rocketMQConfig.getMaxRetries());
                    continue;
                }
                
                try {
                    if (publishEvent(event)) {
                        outboxEventRepository.updateEventStatus(event.getEventId(), OutboxEventStatus.PUBLISHED, LocalDateTime.now());
                        retrySuccessCount++;
                        logger.info("Successfully retried event: {}", event.getEventId());
                    } else {
                        retryFailureCount++;
                    }
                } catch (Exception e) {
                    handlePublishFailure(event, e);
                    retryFailureCount++;
                }
            }
            
            logger.info("Event retry completed. Success: {}, Failure: {}", retrySuccessCount, retryFailureCount);
            
        } catch (Exception e) {
            logger.error("Error occurred while retrying failed events", e);
        }
    }

    /**
     * 处理发布失败的情况
     * @param event 失败的事件
     * @param exception 异常信息
     */
    public void handlePublishFailure(OutboxEvent event, Exception exception) {
        try {
            int newRetryCount = event.getRetryCount() + 1;
            String errorMessage = exception.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }
            
            outboxEventRepository.updateEventStatusWithError(
                event.getEventId(),
                OutboxEventStatus.FAILED,
                LocalDateTime.now(),
                errorMessage,
                newRetryCount
            );
            
            logger.error("Event {} failed to publish (retry count: {}): {}", 
                event.getEventId(), newRetryCount, exception.getMessage());
                
        } catch (Exception e) {
            logger.error("Failed to update event status for failed event: {}", event.getEventId(), e);
        }
    }
}