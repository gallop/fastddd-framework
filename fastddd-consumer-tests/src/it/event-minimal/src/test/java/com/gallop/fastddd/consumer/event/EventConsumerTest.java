package com.gallop.fastddd.consumer.event;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.DomainEventListener;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.SpringDomainEventAdapter;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EVT-001 验收消费者：fast-ddd-event 在 local 模式（无 DataSource、无 MyBatis 活跃）下可启动、
 * 可发布领域事件，且零 Outbox Bean。
 *
 * <p>历史（FRM-002 审计）：EventAutoConfiguration 的宽 @ComponentScan 无条件拾取 outbox 子包，
 * local 场景下 OutboxEventServiceImpl 因缺 ObjectMapper 装配失败
 * （UnsatisfiedDependencyException）。EVT-001 移除宽扫描、以 fastddd.event.mode 显式门禁
 * OutboxEventConfiguration（outbox 模式 + DataSource + MyBatis），local 模式整体退让。
 *
 * <p>验收：① local 模式无数据库启动成功；② 发布领域事件被监听器接收；③ conditions 中无 Outbox Bean。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = EventConsumerTest.App.class)
class EventConsumerTest {

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TestEventListener eventListener;

    @Autowired
    ApplicationContext applicationContext;

    @SpringBootApplication
    static class App {
        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    /** 测试用领域事件。 */
    static class TestEvent extends DomainEvent {
        TestEvent() {
            super("agg-1", "TestEvent");
        }
    }

    /** 监听器：接收 SpringDomainEventAdapter（@DomainEventListener 为元 @EventListener，需注册为 bean）。 */
    static class TestEventListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean received = new AtomicBoolean(false);

        @DomainEventListener
        void on(SpringDomainEventAdapter adapter) {
            if (adapter.getDomainEvent() instanceof TestEvent) {
                received.set(true);
                latch.countDown();
            }
        }
    }

    @Test
    void localMode_startsAndPublishesEventWithoutDatabase() throws InterruptedException {
        // ① local 模式无数据库启动成功（context 加载完成即证明）+ EventPublisher 装配
        assertNotNull(eventPublisher, "local 模式应装配 EventPublisher");

        // ② 发布领域事件被监听器接收
        eventPublisher.publishEvent(new TestEvent());
        assertTrue(eventListener.latch.await(2, TimeUnit.SECONDS),
                "领域事件应在 2s 内被本地监听器接收");
        assertTrue(eventListener.received.get(), "监听器应已收到 TestEvent");
    }

    @Test
    void localMode_noOutboxBeansInConditions() {
        // ③ conditions 中无 Outbox Bean（OutboxEventConfiguration 整体退让）
        assertFalse(applicationContext.containsBeanDefinition("outboxEventService")
                        || applicationContext.getBeansOfType(OutboxEventService.class).size() > 0,
                "local 模式不应装配 OutboxEventService");
        assertFalse(applicationContext.getBeansOfType(OutboxEventRepository.class).size() > 0,
                "local 模式不应装配 OutboxEventRepository");
        assertFalse(applicationContext.getBeansOfType(MybatisPlusInterceptor.class).size() > 0,
                "local 模式不应装配 MybatisPlusInterceptor（Outbox 专有）");
    }
}
