package com.gallop.fastddd.consumer.eventrocketmq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.rocketmq.RocketMQEventListener;
import com.gallop.fastddd.event.rocketmq.RocketMQListenerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 情形一：成功投递（发布链 = TransactionalEventPublisher 事务落库 PENDING →
 * 框架 OutboxDispatcher 周期抢占 → RocketMqOutboxTransport syncSend 真实 Broker；
 * 消费链 = 框架 RocketMQEventListener（rocketmq-spring 容器）→ GenericDomainEvent →
 * 本地 @EventListener）。消息关联键 = event-id（outbox event_id / 消息 KEYS /
 * 消费端 originalEventId 三向一致；event-id 为框架生成的 UUID，测试回查后打印
 * ECO08-T1-EVENT-ID 标记行供 broker 侧 mqadmin 审计）。
 *
 * <p>显式环境门控：{@code -Dfastddd.test.rocketmq=true}；连接与隔离参数经系统属性
 * {@code fastddd.test.rocketmq.name-server} 与 {@code fastddd.test.rocketmq.t1.*} 传入
 * （run-unique topic/group，共享 Broker 上执行时必须覆盖；event-id 由框架 UUID 保证唯一）。</p>
 */
@SpringBootTest(classes = HappyPathE2ETest.App.class, properties = {
        // —— outbox 模式：消费者显式选择 + 显式连接池（H2 MySQL 兼容模式 + 自备 DDL）——
        "fastddd.event.mode=outbox",
        "spring.datasource.url=jdbc:h2:mem:eco08t1;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/outbox_events_h2.sql",
        // —— 框架 RocketMQ 传输（RocketMQEventAutoConfiguration 装配条件）——
        "rocketmq.enabled=true",
        "fastddd.event.outbox.transport=rocketmq",
        "rocketmq.publisher.send-timeout=5000",
        // —— 消费链：框架 RocketMQEventListener（@Import 注册，rocketmq.consumer.* 绑定）——
        "rocketmq.consumer.enabled=true",
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.rocketmq", matches = "true",
        disabledReason = "需真实 RocketMQ（-Dfastddd.test.rocketmq=true 显式开启）")
class HappyPathE2ETest {

    static final String NAME_SERVER = System.getProperty("fastddd.test.rocketmq.name-server", "127.0.0.1:9876");
    static final String TOPIC = System.getProperty("fastddd.test.rocketmq.t1.topic", "fastddd_eco08_t1_ok_local");
    static final String PRODUCER_GROUP = System.getProperty("fastddd.test.rocketmq.t1.producer-group", "fastddd-eco08-p1-local");
    static final String CONSUMER_GROUP = System.getProperty("fastddd.test.rocketmq.t1.consumer-group", "fastddd-eco08-c1-local");
    static final String AGGREGATE_TYPE = "Eco08Order";

    /** 消费端接收证据（框架监听器 → GenericDomainEvent → 本地监听器）。 */
    static final CountDownLatch RECEIVED = new CountDownLatch(1);
    static final AtomicInteger RECEIVED_COUNT = new AtomicInteger();
    static volatile String receivedOriginalEventId;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @org.springframework.test.context.DynamicPropertySource
    static void rmq(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("rocketmq.name-server", () -> NAME_SERVER);
        registry.add("rocketmq.producer.group", () -> PRODUCER_GROUP);
        registry.add("rocketmq.publisher.default-topic", () -> TOPIC);
        registry.add("rocketmq.consumer.topic", () -> TOPIC);
        registry.add("rocketmq.consumer.group", () -> CONSUMER_GROUP);
    }

    @Test
    void happyPath_outboxToRealBrokerToConsumer() throws Exception {
        // 1. 业务事务内发布领域事件 → outbox 落库 PENDING（与业务原子性契约）
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(st -> eventPublisher.publishEvent(new OrderPlacedEvent()));

        OutboxEvent row = latestRow();
        assertNotNull(row, "outbox 行应已落库");
        assertEquals(OutboxEventStatus.PENDING, row.getStatus(), "发布后初始状态应为 PENDING");
        String eventId = row.getEventId();
        // 外部审计锚点（mqadmin queryMsgByKey -k <eventId>）
        System.out.println("ECO08-T1-EVENT-ID: " + eventId);

        // 2. 框架 OutboxDispatcher（后台周期）→ transport → 真实 Broker → PUBLISHED（90s 超时，不无限等待）
        long deadline = System.currentTimeMillis() + 90_000;
        OutboxEventStatus st = row.getStatus();
        while (System.currentTimeMillis() < deadline) {
            st = outboxEventRepository.selectByEventId(eventId).getStatus();
            if (st == OutboxEventStatus.PUBLISHED) {
                break;
            }
            Thread.sleep(1000);
        }
        assertEquals(OutboxEventStatus.PUBLISHED, st, "90s 内应经真实投递转为 PUBLISHED（实测状态=" + st + "）");
        assertNotNull(outboxEventRepository.selectByEventId(eventId).getPublishedAt(), "publishedAt 应记录");

        // 3. 真实消费链：框架 RocketMQEventListener → 本地监听器收到（90s 超时）
        assertTrue(RECEIVED.await(90, TimeUnit.SECONDS), "90s 内测试消费者应收到消息（框架监听器路径）");
        assertEquals(eventId, receivedOriginalEventId, "消费端 originalEventId 应与 outbox event_id 一致");
        assertEquals(1, RECEIVED_COUNT.get(), "CLUSTERING 模式单消费者应恰好收到一次");
    }

    private OutboxEvent latestRow() {
        List<OutboxEvent> rows = outboxEventRepository.selectList(
                new QueryWrapper<OutboxEvent>().eq("aggregate_type", AGGREGATE_TYPE)
                        .orderByDesc("id"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 测试领域事件（outbox eventData 序列化含 Instant，jsr310 契约）。 */
    static class OrderPlacedEvent extends DomainEvent {
        private final java.time.Instant placedAt = java.time.Instant.now();
        private final long amount = 42L;

        OrderPlacedEvent() {
            super("eco08-t1-agg-001", AGGREGATE_TYPE);
        }

        public java.time.Instant getPlacedAt() {
            return placedAt;
        }

        public long getAmount() {
            return amount;
        }
    }

    @SpringBootApplication
    @Import(RocketMQEventListener.class)
    static class App {

        /** 非 web 消费者契约（ECO-006C6）：自备 ObjectMapper（findAndRegisterModules）。 */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /** 框架 RocketMQEventListener 的 @Autowired 依赖（自动配置条目受 rocketmq.consumer.enabled 门控）。 */
        @Bean
        RocketMQListenerConfig rocketMQListenerConfig() {
            return new RocketMQListenerConfig();
        }

        /** 消费端接收证据采集：框架监听器发布 GenericDomainEvent 后在此记录原始 event-id。 */
        @EventListener(com.gallop.fastddd.event.rocketmq.GenericDomainEvent.class)
        public void onDomainEvent(com.gallop.fastddd.event.rocketmq.GenericDomainEvent event) {
            RECEIVED_COUNT.incrementAndGet();
            receivedOriginalEventId = event.getOriginalEventId();
            RECEIVED.countDown();
        }
    }
}
