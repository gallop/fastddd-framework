package com.gallop.fastddd.consumer.eventnodb;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.SpringEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECO-006C6 验收消费者：自定义 {@link EventPublisher} 在默认（无库、无配置）形态下正常退让接管。
 *
 * <p>EventAutoConfiguration 的 springEventPublisher 为 {@code @ConditionalOnMissingBean(EventPublisher)}——
 * 消费者自定义实现必须胜出且框架默认实现整体退让。本类同样不做任何数据源/排除/mode 配置，
 * 验证依赖最小化整改未破坏该公开扩展契约。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = EventCustomPublisherFallbackTest.App.class)
class EventCustomPublisherFallbackTest {

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    RecordingEventPublisher recordingEventPublisher;

    @Autowired
    ApplicationContext applicationContext;

    @SpringBootApplication
    static class App {
        @Bean
        RecordingEventPublisher recordingEventPublisher() {
            return new RecordingEventPublisher();
        }
    }

    /** 消费者自定义 EventPublisher：记录发布调用（不依赖任何框架实现）。 */
    static class RecordingEventPublisher implements EventPublisher {

        final List<DomainEvent> published = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvent(@NonNull DomainEvent event) {
            published.add(event);
        }

        @Override
        public void publishEvents(@NonNull Collection<? extends DomainEvent> events) {
            published.addAll(events);
        }

        @Override
        public void publishEventAsync(@NonNull DomainEvent event) {
            published.add(event);
        }

        @Override
        public void publishEventsAsync(@NonNull Collection<? extends DomainEvent> events) {
            published.addAll(events);
        }

        @Override
        public boolean hasListeners(@NonNull Class<? extends DomainEvent> eventType) {
            return false;
        }

        @Override
        public int getListenerCount(@NonNull Class<? extends DomainEvent> eventType) {
            return 0;
        }
    }

    @Test
    void customEventPublisher_winsAndFrameworkDefaultBacksOff() {
        // 注入的 EventPublisher 就是消费者自定义实现；框架默认 SpringEventPublisher 整体退让
        assertSame(recordingEventPublisher, eventPublisher,
                "自定义 EventPublisher 应胜出（@ConditionalOnMissingBean 退让契约）");
        assertEquals(0, applicationContext.getBeansOfType(SpringEventPublisher.class).size(),
                "框架默认 SpringEventPublisher 应退让（不应装配）");
    }

    @Test
    void customPublisher_receivesPublishedEvent_withoutDatabase() {
        // 自定义实现真实接管发布调用，且默认无库契约保持（零 DataSource）
        eventPublisher.publishEvent(new EventNoDbDefaultContractTest.NoDbEvent());

        assertEquals(1, recordingEventPublisher.published.size(),
                "自定义发布器应真实接管 publishEvent 调用");
        assertTrue(recordingEventPublisher.published.get(0)
                        instanceof EventNoDbDefaultContractTest.NoDbEvent,
                "记录的事件应为发布的事件实例");
        assertEquals(0, applicationContext.getBeansOfType(DataSource.class).size(),
                "自定义发布器形态下默认无库契约保持（零 DataSource）");
    }
}
