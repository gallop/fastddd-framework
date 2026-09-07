package com.gallop.fastddd.consumer.eventoutbox;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.TransactionalEventPublisher;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECO-006C6 验收消费者：fast-ddd-event outbox 模式，消费者显式提供连接池。
 *
 * <p>C6 依赖最小化后 fast-ddd-event 池中立（不传递任何池实现），outbox 消费者显式携带
 * spring-boot-starter-jdbc（HikariCP）+ 测试域 H2（MySQL 兼容模式）+ 消费者自备
 * outbox_events DDL。全量消费者自动配置形态（@SpringBootApplication，含 Jackson 自动发现链）。
 *
 * <p>验收：① outbox 装配面（TransactionalEventPublisher / OutboxEventService / Mapper /
 * 拦截器 / 显式 Hikari 池）；② publishEvent 经真实 Mapper 落库为 PENDING（事务提交）；
 * ③ 既有状态转换 PENDING→PROCESSING（claimEvents）→PUBLISHED（markAsPublished）；
 * ④ 调用方事务回滚时 outbox 行不落库（与业务事务原子性契约）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = EventOutboxConsumerTest.App.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:eventoutbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/outbox_events_h2.sql"
})
class EventOutboxConsumerTest {

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @SpringBootApplication
    static class App {

        /**
         * 消费者自备 ObjectMapper（非 web outbox 消费者契约）：JacksonAutoConfiguration 的
         * ObjectMapper @Bean 依赖 Jackson2ObjectMapperBuilder（spring-web）；本消费者仅
         * event + jdbc（无 web），须自行提供。findAndRegisterModules 自动发现 JSR310
         * （DomainEvent.createdAt 为 Instant），对齐 Spring Boot 生产装配。
         */
        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        }
    }

    /** 测试用领域事件（每次构造生成唯一 eventId）。 */
    static class OrderPlacedEvent extends DomainEvent {
        OrderPlacedEvent(Serializable aggregateId) {
            super(aggregateId, "Order");
        }
    }

    @Test
    void outboxMode_assemblesTransactionalPublisherMapperAndExplicitPool() {
        // ① outbox 装配面：EventPublisher 为事务性实现；Mapper/服务/拦截器在场；
        //    DataSource 为消费者显式提供的 Hikari 池（C6 后 event 池中立，池来自消费者）
        assertTrue(eventPublisher instanceof TransactionalEventPublisher,
                "outbox 模式应装配 TransactionalEventPublisher");
        assertNotNull(outboxEventService, "outbox 模式应装配 OutboxEventService");
        assertNotNull(outboxEventRepository, "outbox 模式应装配 OutboxEventRepository（@MapperScan）");
        assertTrue(applicationContext.getBeansOfType(MybatisPlusInterceptor.class).size() == 1,
                "outbox 模式应装配 MybatisPlusInterceptor（乐观锁+分页）");

        DataSource dataSource = applicationContext.getBeansOfType(DataSource.class).values().iterator().next();
        assertTrue(dataSource instanceof HikariDataSource,
                "连接池应由消费者显式提供（HikariDataSource），而非框架传递");
    }

    @Test
    void publishEvent_persistsPendingRow_throughRealMapperInTransaction() {
        // ② 经真实 Mapper（注解 SQL + H2）落库为 PENDING，eventId/aggregate/eventType FQN 映射正确
        OrderPlacedEvent event = new OrderPlacedEvent("order-1001");
        eventPublisher.publishEvent(event);

        OutboxEvent saved = outboxEventRepository.selectByEventId(event.getEventId().toString());
        assertNotNull(saved, "publishEvent 后应能按 eventId 查到 outbox 行（事务已提交）");
        assertEquals(OutboxEventStatus.PENDING, saved.getStatus(), "新落库事件应为 PENDING");
        assertEquals("order-1001", saved.getAggregateId());
        assertEquals("Order", saved.getAggregateType());
        assertEquals(OrderPlacedEvent.class.getName(), saved.getEventType(), "eventType 应为事件 FQN");
        assertNotNull(saved.getEventData(), "event_data 应已序列化（JSON）");
    }

    @Test
    void happyPath_stateTransitions_pending_processing_published() {
        // ③ 既有状态转换：publish（PENDING）→ claimEvents（PROCESSING）→ markAsPublished（PUBLISHED 终态）
        OrderPlacedEvent event = new OrderPlacedEvent("order-2002");
        eventPublisher.publishEvent(event);
        String eventId = event.getEventId().toString();

        List<String> claimed = outboxEventService.claimEvents(10);
        assertTrue(claimed.contains(eventId), "claimEvents 应抢占该事件: " + claimed);
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventService.findByEventId(eventId).orElseThrow().getStatus(),
                "claim 后应为 PROCESSING");

        assertTrue(outboxEventService.markAsPublished(eventId), "markAsPublished 应成功");
        OutboxEvent published = outboxEventService.findByEventId(eventId).orElseThrow();
        assertEquals(OutboxEventStatus.PUBLISHED, published.getStatus());
        assertTrue(published.getStatus().isFinalState(), "PUBLISHED 应为终态");
        assertNotNull(published.getPublishedAt(), "发布时间应已记录");
    }

    @Test
    void callerTransactionRollback_outboxRowNotPersisted() {
        // ④ 事务契约：publishEvent 的 @Transactional(REQUIRED) 参与调用方事务——回滚则 outbox 行不落库
        OrderPlacedEvent event = new OrderPlacedEvent("order-3003");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
            return null;
        });

        assertNull(outboxEventRepository.selectByEventId(event.getEventId().toString()),
                "调用方事务回滚时 outbox 行不应落库（与业务事务原子）");
    }
}
