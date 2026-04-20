package com.gallop.fastddd.test;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.DomainEventListener;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.SpringDomainEventAdapter;
import com.gallop.fastddd.event.SpringEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(EventIntegrationTest.TestConfig.class)
public class EventIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeEach
    void resetState() {
        testEventListener.reset();
    }

    @Test
    public void shouldDispatchEventSynchronously() throws InterruptedException {
        // Given
        String testData = "test-data-" + System.currentTimeMillis();
        TestEvent testEvent = new TestEvent(testData);
        long currentThreadId = Thread.currentThread().getId();

        // When
        eventPublisher.publishEvent(testEvent);

        // Then - synchronous dispatch completes in the same call stack
        assertTrue(testEventListener.getEventReceived().await(1, TimeUnit.SECONDS),
                   "Event should be received within 1 second");
        assertTrue(testEventListener.isEventProcessed(), "Event should be processed");
        assertEquals(currentThreadId, testEventListener.getProcessingThreadId(),
                     "Event should be processed in the same thread (synchronous execution)");
        assertEquals(testData, testEventListener.getReceivedEventData(),
                     "Event data should match");
    }

    @Test
    public void shouldHandleMultipleEventsSequentially() throws InterruptedException {
        // Given
        int eventCount = 3;

        // When
        for (int i = 0; i < eventCount; i++) {
            TestEvent event = new TestEvent("event-" + i);
            eventPublisher.publishEvent(event);
        }

        // Then
        assertTrue(testEventListener.getEventReceived().await(1, TimeUnit.SECONDS),
                   "Events should be received within 1 second");
        assertEquals(eventCount, testEventListener.getEventCount(),
                     "All events should be processed");
    }

    public static class TestEvent extends DomainEvent {
        private final String message;

        public TestEvent(String message) {
            super("test", "TestEvent");
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    static class TestEventListener {
        private final AtomicBoolean eventProcessed = new AtomicBoolean(false);
        private final AtomicLong processingThreadId = new AtomicLong(-1);
        private volatile CountDownLatch eventReceived = new CountDownLatch(1);
        private volatile String receivedEventData;
        private volatile int eventCount = 0;

        @DomainEventListener
        public void handleSpringDomainEvent(SpringDomainEventAdapter adapter) {
            DomainEvent domainEvent = adapter.getDomainEvent();
            if (domainEvent instanceof TestEvent testEvent) {
                eventProcessed.set(true);
                processingThreadId.set(Thread.currentThread().getId());
                receivedEventData = testEvent.getMessage();
                eventCount++;
                eventReceived.countDown();
            }
        }

        public void reset() {
            eventProcessed.set(false);
            processingThreadId.set(-1);
            receivedEventData = null;
            eventCount = 0;
            eventReceived = new CountDownLatch(1);
        }

        public boolean isEventProcessed() {
            return eventProcessed.get();
        }

        public long getProcessingThreadId() {
            return processingThreadId.get();
        }

        public CountDownLatch getEventReceived() {
            return eventReceived;
        }

        public String getReceivedEventData() {
            return receivedEventData;
        }

        public int getEventCount() {
            return eventCount;
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public EventPublisher eventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            return new SpringEventPublisher(applicationEventPublisher);
        }

        @Bean
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }
}
