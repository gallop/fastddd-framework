package com.gallop.fastddd.event.rocketmq;

import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * RocketMqOutboxTransport 单元测试（EVT-003 步骤7）。
 *
 * <p>Mock {@link RocketMQTemplate} 验证：成功路径调 syncSend 到正确 destination（topic:tag）；
 * 失败（含超时）抛 {@link OutboxTransportException} 携带 eventId 与 timeout 标记。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@ExtendWith(MockitoExtension.class)
class RocketMqOutboxTransportTest {

    @Mock
    RocketMQTemplate rocketMQTemplate;

    private RocketMQConfig config;
    private RocketMqOutboxTransport transport;

    @BeforeEach
    void setUp() {
        config = new RocketMQConfig();
        config.setDefaultTopic("domain-events");
        config.setSendTimeout(1500);
        transport = new RocketMqOutboxTransport(rocketMQTemplate, config);
    }

    private static OutboxEvent event() {
        return OutboxEvent.builder()
                .eventId("e-1")
                .aggregateId("agg-1")
                .aggregateType("Order")
                .eventType("OrderCreatedEvent")
                .eventData("{\"amount\":100}")
                .status(OutboxEventStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void send_success_callsSyncSendWithTopicColonTag() {
        OutboxEvent event = event();

        transport.send(event);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(destinationCaptor.capture(), messageCaptor.capture(), eq(1500L));

        assertEquals("domain-events:Order.OrderCreatedEvent", destinationCaptor.getValue());
        // KEYS 头携带 eventId，便于 RocketMQ 侧按 eventId 索引/去重
        assertEquals("e-1", messageCaptor.getValue().getHeaders().get("KEYS"));
    }

    @Test
    void send_failure_wrapsAsTransportException() {
        OutboxEvent event = event();
        doThrow(new RuntimeException("broker unreachable"))
                .when(rocketMQTemplate).syncSend(any(String.class), any(Message.class), anyLong());

        OutboxTransportException ex = assertThrows(OutboxTransportException.class,
                () -> transport.send(event));
        assertEquals("e-1", ex.getEventId());
        assertFalse(ex.isTimeout(), "一般 broker 异常不应判为超时");
        // 根因保留，便于诊断
        assertTrue(ex.getCause() instanceof RuntimeException);
    }

    @Test
    void send_timeout_markedAsTimeout() {
        OutboxEvent event = event();
        // 异常类名含 "Timeout" → isTimeout 启发式命中
        doThrow(new TestTimeoutException("send timed out"))
                .when(rocketMQTemplate).syncSend(any(String.class), any(Message.class), anyLong());

        OutboxTransportException ex = assertThrows(OutboxTransportException.class,
                () -> transport.send(event));
        assertTrue(ex.isTimeout(), "超时类异常应被识别");
    }

    /** 仅供 isTimeout 启发式验证用的异常类型（类名含 "Timeout"）。 */
    static class TestTimeoutException extends RuntimeException {
        TestTimeoutException(String message) {
            super(message);
        }
    }
}
