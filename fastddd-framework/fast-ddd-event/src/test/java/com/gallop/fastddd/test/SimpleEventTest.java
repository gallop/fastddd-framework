package com.gallop.fastddd.test;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.DomainEventListener;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.SpringDomainEventAdapter;
import com.gallop.fastddd.event.SpringEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(SimpleEventTest.TestConfig.class)
public class SimpleEventTest {

    @Autowired
    private EventPublisher simpleEventPublisher;

    @Autowired
    private SimpleEventListener eventListener;

    @Test
    void shouldPublishAndHandleEventSynchronously() throws InterruptedException {
        // Given
        TestEvent event = new TestEvent("test message");

        // When
        simpleEventPublisher.publishEvent(event);

        // Then
        assertTrue(eventListener.waitForEvent(1000), "Event should be processed within 1 second");
        assertTrue(eventListener.isEventProcessed(), "Event should be processed");
        assertEquals("test message", eventListener.getLastMessage());
    }

    static class TestEvent extends DomainEvent {
        private final String message;

        public TestEvent(String message) {
            super("test", "TestEvent");
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    static class SimpleEventListener {
        private final AtomicBoolean eventProcessed = new AtomicBoolean(false);
        private final CountDownLatch latch = new CountDownLatch(1);
        private String lastMessage;

        @DomainEventListener
        public void handleSpringDomainEvent(SpringDomainEventAdapter adapter) {
            DomainEvent domainEvent = adapter.getDomainEvent();
            if (domainEvent instanceof TestEvent testEvent) {
                this.lastMessage = testEvent.getMessage();
                eventProcessed.set(true);
                latch.countDown();
            }
        }

        public boolean waitForEvent(long timeoutMs) throws InterruptedException {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean isEventProcessed() {
            return eventProcessed.get();
        }

        public String getLastMessage() {
            return lastMessage;
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public EventPublisher simpleEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
            return new SpringEventPublisher(applicationEventPublisher);
        }

        @Bean
        public SimpleEventListener simpleEventListener() {
            return new SimpleEventListener();
        }
    }
}
