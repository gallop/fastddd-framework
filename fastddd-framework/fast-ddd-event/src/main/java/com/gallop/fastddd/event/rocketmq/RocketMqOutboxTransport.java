package com.gallop.fastddd.event.rocketmq;

import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;

/**
 * RocketMQ Outbox 传输（EVT-003，{@code fastddd.event.outbox.transport=rocketmq}）。
 *
 * <p>仅负责把单个已落库的 {@link OutboxEvent} 投递到 RocketMQ（{@code RocketMQTemplate.syncSend}），
 * <b>不扫描 outbox 表、不创建存储、不改事件状态</b>——全部由
 * {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher} 驱动。
 *
 * <p>从原 {@code RocketMQPublisher.publishEvent} 抽取传输逻辑（ADR-003 §2.1：传输与调度分离）。
 * 抛弃原反射 {@code syncSend} 兜底——本 bean 仅在 {@link RocketMQTemplate} 真实存在于 classpath
 * 且为 bean 时装配（见 {@link RocketMQEventAutoConfiguration} 的 @ConditionalOnClass），可强类型直接调用。
 *
 * <p>投递失败（含超时、broker 拒绝、连接异常）抛 {@link OutboxTransportException}，
 * 由 Dispatcher 捕获后 markAsFailed（重试耗尽自动入死信，见 EVT-002）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class RocketMqOutboxTransport implements OutboxTransport {

    private static final Logger log = LoggerFactory.getLogger(RocketMqOutboxTransport.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;

    public RocketMqOutboxTransport(RocketMQTemplate rocketMQTemplate, RocketMQConfig rocketMQConfig) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.rocketMQConfig = rocketMQConfig;
    }

    @Override
    public void send(OutboxEvent event) throws OutboxTransportException {
        String tag = determineTag(event);
        String destination = rocketMQConfig.getDefaultTopic() + ":" + tag;
        EventMessage payload = buildEventMessage(event);
        org.springframework.messaging.Message<EventMessage> message = MessageBuilder
                .withPayload(payload)
                .setHeader("KEYS", event.getEventId())
                .setHeader("TAGS", tag)
                .build();
        try {
            rocketMQTemplate.syncSend(destination, message, rocketMQConfig.getSendTimeout());
            log.debug("[RocketMqOutboxTransport] 投递成功: eventId={}, destination={}",
                    event.getEventId(), destination);
        } catch (Exception e) {
            throw new OutboxTransportException(event,
                    "RocketMQ 投递失败: destination=" + destination + ", eventId=" + event.getEventId(),
                    e, isTimeout(e));
        }
    }

    /** 构建消息载荷（沿用原 RocketMQPublisher 的 EventMessage 契约，保持线格式兼容）。 */
    private EventMessage buildEventMessage(OutboxEvent event) {
        EventMessage msg = new EventMessage();
        msg.setEventId(event.getEventId());
        msg.setEventType(event.getEventType());
        msg.setAggregateId(event.getAggregateId());
        msg.setAggregateType(event.getAggregateType());
        msg.setEventData(event.getEventData());
        msg.setOccurredAt(event.getCreatedAt());
        msg.setSentAt(LocalDateTime.now());
        msg.setVersion("1.0");
        return msg;
    }

    private String determineTag(OutboxEvent event) {
        return event.getAggregateType() + "." + event.getEventType();
    }

    /** 启发式判定是否为超时类失败（消息含 timeout/超时），供运维指标分类。 */
    private static boolean isTimeout(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String name = cur.getClass().getName();
            if (name.contains("Timeout") || name.contains("RemotingTimeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
