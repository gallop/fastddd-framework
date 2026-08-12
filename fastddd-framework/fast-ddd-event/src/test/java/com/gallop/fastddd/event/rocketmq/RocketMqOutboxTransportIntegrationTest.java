package com.gallop.fastddd.event.rocketmq;

import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * RocketMQ Outbox 真实投递门禁（QA-002）。
 *
 * <p>仅在 {@code -Dfastddd.test.rocketmq=true} 时运行。测试通过框架自动装配的
 * {@link RocketMqOutboxTransport} 向真实 Broker 同步发送一条带事件 ID 的消息；测试后可使用
 * RocketMQ 的 {@code queryMsgByKey} 按该 ID 审计投递结果。</p>
 */
@SpringBootTest(classes = RocketMqOutboxTransportIntegrationTest.TestApp.class, properties = {
        "rocketmq.enabled=true",
        "rocketmq.producer.group=fastddd-rc-gate-producer",
        "rocketmq.publisher.default-topic=fastddd_rc_framework_gate",
        "rocketmq.publisher.send-timeout=5000",
        "fastddd.event.outbox.transport=rocketmq"
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.rocketmq", matches = "true",
        disabledReason = "需真实 RocketMQ（-Dfastddd.test.rocketmq=true 显式开启）")
class RocketMqOutboxTransportIntegrationTest {

    static final String EVENT_ID = "fastddd-rc1-rocketmq-gate-20260813";

    @Autowired
    OutboxTransport outboxTransport;

    @DynamicPropertySource
    static void rocketMqProperties(DynamicPropertyRegistry registry) {
        registry.add("rocketmq.name-server",
                () -> System.getProperty("fastddd.test.rocketmq.name-server", "127.0.0.1:9876"));
    }

    @Test
    void send_usesFrameworkTransportAndRealBroker() {
        assertInstanceOf(RocketMqOutboxTransport.class, outboxTransport);
        OutboxEvent event = OutboxEvent.builder()
                .eventId(EVENT_ID)
                .aggregateId("rc1-gate")
                .aggregateType("FrameworkGate")
                .eventType("RocketMqDelivered")
                .eventData("{\"source\":\"fastddd-rc1-gate\"}")
                .status(OutboxEventStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        outboxTransport.send(event);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApp {
    }
}
