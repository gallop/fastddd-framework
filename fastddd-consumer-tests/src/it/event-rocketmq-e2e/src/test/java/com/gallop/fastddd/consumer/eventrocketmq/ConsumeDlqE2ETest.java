package com.gallop.fastddd.consumer.eventrocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.rocketmq.RocketMQEventListener;
import com.gallop.fastddd.event.rocketmq.RocketMqOutboxTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 情形三：消费失败与死信（真实 Broker 消费重投 + 达到测试重试上限后进入 Broker DLQ）。
 *
 * <p><b>链路</b>：框架真实 {@link RocketMqOutboxTransport} 将带固定 event-id 的消息投递到真实
 * Broker（run-unique topic）→ 框架 {@link RocketMQEventListener}（独立测试 consumer group，
 * rocketmq-spring 容器消费）→ GenericDomainEvent → 本测试 @EventListener <b>主动抛异常</b>模拟业务
 * 处理失败 → 监听器重抛 → Broker 消费重投（%RETRY%group，10s/30s/60s）→ 框架注解
 * {@code maxReconsumeTimes=3} 耗尽 → 消息进入 Broker 死信队列 %DLQ%group。</p>
 *
 * <p><b>边界（如实声明）</b>：本情形不依赖数据库（无 outbox，producer 侧直发 transport，与框架
 * RocketMqOutboxTransportIntegrationTest 同形态）；测试断言"投递尝试次数 = 1 + 3 = 4 且之后不再
 * 收到（已离开消费组进入 DLQ）"；<b>Broker %DLQ% 队列中的消息存在性由外部 mqadmin
 * queryMsgByKey 按 event-id 检索取证</b>（不在 JVM 内断言，也不手工向 DLQ 发消息伪造结果；
 * Outbox DEAD_LETTER 状态是另一层概念，本情形不涉及、不混用）。</p>
 *
 * <p>显式环境门控与 run-unique 隔离参数同情形一（{@code t3.*}；event-id 为测试直发可预指定）。</p>
 */
@SpringBootTest(classes = ConsumeDlqE2ETest.App.class, properties = {
        "rocketmq.enabled=true",
        "fastddd.event.outbox.transport=rocketmq",
        "rocketmq.publisher.send-timeout=5000",
        "rocketmq.consumer.enabled=true",
        // 无数据库形态（照 C6 门禁测试）：显式排除 DataSource 自动配置，event 保持默认 local 模式
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.rocketmq", matches = "true",
        disabledReason = "需真实 RocketMQ（-Dfastddd.test.rocketmq=true 显式开启）")
class ConsumeDlqE2ETest {

    static final String NAME_SERVER = System.getProperty("fastddd.test.rocketmq.name-server", "127.0.0.1:9876");
    static final String TOPIC = System.getProperty("fastddd.test.rocketmq.t3.topic", "fastddd_eco08_t3_dlq_local");
    static final String PRODUCER_GROUP = System.getProperty("fastddd.test.rocketmq.t3.producer-group", "fastddd-eco08-p3-local");
    static final String CONSUMER_GROUP = System.getProperty("fastddd.test.rocketmq.t3.consumer-group", "fastddd-eco08-c3-local");
    static final String EVENT_ID = System.getProperty("fastddd.test.rocketmq.t3.event-id", "fastddd-eco08-dlq-local-001");

    /** 框架注解 maxReconsumeTimes=3：总投递尝试 = 首次 1 + 重投 3 = 4。 */
    static final int EXPECTED_TOTAL_ATTEMPTS = 4;

    static final AtomicInteger ATTEMPTS = new AtomicInteger();
    static final List<String> ATTEMPT_LOG = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Autowired
    OutboxTransport outboxTransport;

    @DynamicPropertySource
    static void rmq(DynamicPropertyRegistry registry) {
        registry.add("rocketmq.name-server", () -> NAME_SERVER);
        registry.add("rocketmq.producer.group", () -> PRODUCER_GROUP);
        registry.add("rocketmq.publisher.default-topic", () -> TOPIC);
        registry.add("rocketmq.consumer.topic", () -> TOPIC);
        registry.add("rocketmq.consumer.group", () -> CONSUMER_GROUP);
    }

    @Test
    @Timeout(420)
    void consumeFailure_retriedByBroker_thenDeadLetterQueue() throws Exception {
        // 外部审计锚点（mqadmin queryMsgByKey -k <EVENT_ID>，含 %RETRY%/%DLQ% topic）
        System.out.println("ECO08-T3-EVENT-ID: " + EVENT_ID);
        System.out.println("ECO08-T3-CONSUMER-GROUP: " + CONSUMER_GROUP);

        // 1. 框架真实 transport 直发固定 event-id 消息到真实 Broker（SEND_OK 无异常即成功）
        assertInstanceOf(RocketMqOutboxTransport.class, outboxTransport, "应使用框架 RocketMqOutboxTransport");
        OutboxEvent event = OutboxEvent.builder()
                .eventId(EVENT_ID)
                .aggregateId("eco08-t3-agg-001")
                .aggregateType("Eco08Payment")
                .eventType("PaymentFailed")
                .eventData("{\"source\":\"eco08-t3\",\"note\":\"intentional consume failure\"}")
                .status(OutboxEventStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
        outboxTransport.send(event);

        // 2. 等待消费重投至尝试数 = 4（10s/30s/60s 退避，总计约 100s；5min 上限）
        long deadline = System.currentTimeMillis() + 300_000;
        while (ATTEMPTS.get() < EXPECTED_TOTAL_ATTEMPTS && System.currentTimeMillis() < deadline) {
            Thread.sleep(1000);
        }
        System.out.println("ECO08-T3-ATTEMPTS: " + ATTEMPTS.get());
        ATTEMPT_LOG.forEach(line -> System.out.println("ECO08-T3-ATTEMPT-LOG " + line));
        assertTrue(ATTEMPTS.get() >= EXPECTED_TOTAL_ATTEMPTS,
                "5min 内应观察到 1+3=4 次消费尝试（实测=" + ATTEMPTS.get() + "）");

        // 3. 稳定期：再等 30s，确认不再收到（重投耗尽 → 消息已进 Broker %DLQ%，由外部 mqadmin 取证）
        Thread.sleep(30_000);
        assertEquals(EXPECTED_TOTAL_ATTEMPTS, ATTEMPTS.get(),
                "重试耗尽进入 DLQ 后不应再有第 5 次投递（实测=" + ATTEMPTS.get() + "）");
        System.out.println("ECO08-T3-STABILIZED-ATTEMPTS: " + ATTEMPTS.get());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RocketMQEventListener.class)
    static class App {

        /** 非 web 消费者契约（ECO-006C6）：自备 ObjectMapper（findAndRegisterModules）。 */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /**
         * 消费端主动失败注入（任务书三情形之"主动处理失败"）：对目标 event-id 抛异常，
         * 经框架 RocketMQEventListener 重抛触发 Broker 真实消费重投 / 死信链路。
         */
        @EventListener(com.gallop.fastddd.event.rocketmq.GenericDomainEvent.class)
        public void onDomainEvent(com.gallop.fastddd.event.rocketmq.GenericDomainEvent event) {
            int attempt = ATTEMPTS.incrementAndGet();
            ATTEMPT_LOG.add(LocalDateTime.now().format(TS) + " attempt=" + attempt
                    + " originalEventId=" + event.getOriginalEventId());
            throw new IllegalStateException("ECO-RELEASE-08 intentional consume failure: attempt=" + attempt
                    + ", eventId=" + event.getOriginalEventId());
        }
    }
}
