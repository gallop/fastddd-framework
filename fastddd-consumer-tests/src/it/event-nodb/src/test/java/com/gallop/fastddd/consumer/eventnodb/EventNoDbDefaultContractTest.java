package com.gallop.fastddd.consumer.eventnodb;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.DomainEventListener;
import com.gallop.fastddd.event.EventProperties;
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

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECO-006C6 验收消费者：fast-ddd-event 的「开箱默认」无库契约。
 *
 * <p>与 event-minimal（EVT-001，依赖 {@code spring.autoconfigure.exclude} +
 * {@code fastddd.event.mode: local} 前提）不同，本类刻意<b>不做任何配置</b>：
 * 无 application.yml、无数据源、无 H2、无自动配置排除、无显式 mode——
 * 真实无库消费者的默认形态。C5 探针实证修复前该形态启动失败
 * （event 直依 mybatis-plus-spring-boot3-starter → spring-boot-starter-jdbc →
 * HikariCP，{@code DataSourceConfiguration$Hikari} 无 url 急切建池）；
 * C6 拆解为池中立的四件套后，默认 local 模式零数据库依赖。
 *
 * <p>验收：① 默认 mode=local（EventProperties 默认值，无配置求值）；
 * ② 无库发布领域事件并被本地监听器接收；③ 零 DataSource / Outbox Bean。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = EventNoDbDefaultContractTest.App.class)
class EventNoDbDefaultContractTest {

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    EventProperties eventProperties;

    @Autowired
    NoDbEventListener eventListener;

    @Autowired
    ApplicationContext applicationContext;

    @SpringBootApplication
    static class App {
        @Bean
        NoDbEventListener noDbEventListener() {
            return new NoDbEventListener();
        }
    }

    /** 测试用领域事件。 */
    static class NoDbEvent extends DomainEvent {
        NoDbEvent() {
            super("nodb-agg-1", "NoDbEvent");
        }
    }

    /** 监听器：接收 SpringDomainEventAdapter（@DomainEventListener 为元 @EventListener，需注册为 bean）。 */
    static class NoDbEventListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean received = new AtomicBoolean(false);

        @DomainEventListener
        void on(SpringDomainEventAdapter adapter) {
            if (adapter.getDomainEvent() instanceof NoDbEvent) {
                received.set(true);
                latch.countDown();
            }
        }
    }

    @Test
    void defaultModeIsLocal_withoutAnyConfiguration() {
        // ① 无任何配置时 EventProperties 默认 mode=local（matchIfMissing 装配前提成立）
        assertEquals("local", eventProperties.getMode(),
                "无显式配置时 fastddd.event.mode 应默认 local");
    }

    @Test
    void defaultLocalMode_publishesAndReceivesEventWithoutDatabase() throws InterruptedException {
        // ② 无库发布领域事件并被本地监听器接收（默认装配的 SpringEventPublisher + 本地事件链路）。
        // 不按 SpringEventPublisher 具体类断言：@EnableAsync 下 @Async 方法触发 JDK 代理
        // （仅实现 EventPublisher 接口），getBeansOfType(具体类) 恒为 0（C2 同型教训）——
        // 发布/接收行为即装配证明。
        assertNotNull(eventPublisher, "默认 local 模式应装配 EventPublisher");

        eventPublisher.publishEvent(new NoDbEvent());
        assertTrue(eventListener.latch.await(2, TimeUnit.SECONDS),
                "领域事件应在 2s 内被本地监听器接收（无库默认形态）");
        assertTrue(eventListener.received.get(), "监听器应已收到 NoDbEvent");
    }

    @Test
    void defaultLocalMode_hasZeroDataSourceAndOutboxBeans() {
        // ③ 零 DataSource（不强制建池 = C5 F1 修复的直接验证）+ 零 Outbox Bean
        assertEquals(0, applicationContext.getBeansOfType(DataSource.class).size(),
                "默认 local 模式不得装配任何 DataSource（C5 F1：修复前 Hikari 急切建池）");
        assertFalse(applicationContext.containsBeanDefinition("outboxEventService")
                        || applicationContext.getBeansOfType(OutboxEventService.class).size() > 0,
                "默认 local 模式不应装配 OutboxEventService");
        assertFalse(applicationContext.getBeansOfType(OutboxEventRepository.class).size() > 0,
                "默认 local 模式不应装配 OutboxEventRepository");
        assertFalse(applicationContext.getBeansOfType(MybatisPlusInterceptor.class).size() > 0,
                "默认 local 模式不应装配 MybatisPlusInterceptor（Outbox 专有）");
    }
}
