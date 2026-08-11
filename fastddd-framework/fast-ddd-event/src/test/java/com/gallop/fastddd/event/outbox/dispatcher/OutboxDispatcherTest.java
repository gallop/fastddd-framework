package com.gallop.fastddd.event.outbox.dispatcher;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OutboxDispatcher 投递分支单元测试（EVT-003 步骤7）。
 *
 * <p>直接驱动 {@link OutboxDispatcher#dispatch()}，不触发 @PostConstruct 的真实调度线程池——
 * 逐分支验证：成功→markAsPublished、失败→markAsFailed（含超时与未预期异常）、空批次→无操作。
 * at-least-once / 状态转换的数据库级覆盖由 {@code OutboxEventIntegrationTest} 承载。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock
    OutboxEventService outboxEventService;

    @Mock
    OutboxTransport transport;

    EventProperties properties;

    OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new EventProperties();
        dispatcher = new OutboxDispatcher(outboxEventService, transport, properties);
    }

    private static OutboxEvent event(String eventId) {
        return OutboxEvent.builder()
                .eventId(eventId)
                .aggregateId("agg-1")
                .aggregateType("Order")
                .eventType("OrderCreatedEvent")
                .eventData("{}")
                .status(OutboxEventStatus.PROCESSING)
                .build();
    }

    @Test
    void dispatch_success_marksPublishedForEachEvent() {
        OutboxEvent e1 = event("e-1");
        OutboxEvent e2 = event("e-2");
        when(outboxEventService.claimBatch(anyInt(), any())).thenReturn(List.of(e1, e2));

        dispatcher.dispatch();

        verify(transport).send(e1);
        verify(transport).send(e2);
        verify(outboxEventService).markAsPublished("e-1");
        verify(outboxEventService).markAsPublished("e-2");
        verify(outboxEventService, never()).markAsFailed(any(), any());
    }

    @Test
    void dispatch_transportFailure_marksFailedNotPublished() {
        OutboxEvent e1 = event("e-1");
        when(outboxEventService.claimBatch(anyInt(), any())).thenReturn(List.of(e1));
        OutboxTransportException failure = new OutboxTransportException(e1, "broker refused", new RuntimeException());
        doThrow(failure).when(transport).send(e1);

        dispatcher.dispatch();

        verify(outboxEventService).markAsFailed(eq("e-1"), eq("broker refused"));
        verify(outboxEventService, never()).markAsPublished(any());
    }

    @Test
    void dispatch_timeoutFailure_marksFailed() {
        OutboxEvent e1 = event("e-1");
        when(outboxEventService.claimBatch(anyInt(), any())).thenReturn(List.of(e1));
        OutboxTransportException timeout = new OutboxTransportException(e1, "send timed out",
                new RuntimeException(), true);
        doThrow(timeout).when(transport).send(e1);

        dispatcher.dispatch();

        verify(outboxEventService).markAsFailed(eq("e-1"), eq("send timed out"));
    }

    @Test
    void dispatch_unexpectedException_marksFailedWithTruncatedMessage() {
        OutboxEvent e1 = event("e-1");
        when(outboxEventService.claimBatch(anyInt(), any())).thenReturn(List.of(e1));
        String veryLong = "k".repeat(800);
        doThrow(new RuntimeException(veryLong)).when(transport).send(e1);

        dispatcher.dispatch();

        // 未预期异常（非 OutboxTransportException）同样落 FAILED；错误信息截断到 500 字符
        verify(outboxEventService).markAsFailed(eq("e-1"), contains("k"));
        verify(outboxEventService, never()).markAsPublished(any());
    }

    @Test
    void dispatch_emptyBatch_doesNothing() {
        when(outboxEventService.claimBatch(anyInt(), any())).thenReturn(List.of());

        dispatcher.dispatch();

        verifyNoInteractions(transport);
        verify(outboxEventService, never()).markAsPublished(any());
        verify(outboxEventService, never()).markAsFailed(any(), any());
    }
}
