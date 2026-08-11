package com.gallop.fastddd.event.outbox.transport;

import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

/**
 * LocalOutboxTransport 单元测试（EVT-003 步骤7）。
 *
 * <p>默认本地传输投递 {@link OutboxEvent} 信封（非反序列化的领域事件子类，详见 LocalOutboxTransport
 * Javadoc）。验证信封原样经 ApplicationEventPublisher 发布。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@ExtendWith(MockitoExtension.class)
class LocalOutboxTransportTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Test
    void send_republishesOutboxEventEnvelope() {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId("e-1")
                .aggregateId("agg-1")
                .aggregateType("Order")
                .eventType("OrderCreatedEvent")
                .eventData("{\"amount\":100}")
                .status(OutboxEventStatus.PROCESSING)
                .build();

        new LocalOutboxTransport(applicationEventPublisher).send(outboxEvent);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertSame(outboxEvent, captor.getValue(),
                "本地传输应原样发布 outbox 事件信封");
        assertEquals("e-1", ((OutboxEvent) captor.getValue()).getEventId());
    }
}
