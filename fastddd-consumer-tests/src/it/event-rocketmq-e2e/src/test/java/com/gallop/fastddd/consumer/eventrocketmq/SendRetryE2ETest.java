package com.gallop.fastddd.consumer.eventrocketmq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import com.gallop.fastddd.event.rocketmq.RocketMQConfig;
import com.gallop.fastddd.event.rocketmq.RocketMqOutboxTransport;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2 情形二：发送失败重试（Outbox FAILED 状态 / retryCount / 指数退避 / 恢复后真实 Broker 投递）。
 *
 * <p><b>故障注入方式（如实登记）</b>：本测试 App 以 @Bean 提供装饰版 {@link OutboxTransport}
 * （{@link FaultInjectingOutboxTransport}），前 1 次 {@code send} 抛
 * {@link OutboxTransportException}（与真实 transport 投递失败同类型，模拟客户端侧发送失败），
 * 之后全部委托给框架真实 {@link RocketMqOutboxTransport}。框架自动配置的 transport bean 经
 * {@code @ConditionalOnMissingBean(OutboxTransport)} 退让——<b>Broker 零改动、不冒充 Broker 自身故障、
 * 不停共享 Broker、不改网络与任何既有服务配置</b>；恢复后的投递经真实 RocketMQTemplate 到达真实 Broker。</p>
 *
 * <p>发布链与情形一相同（TransactionalEventPublisher 事务落库 → 框架 OutboxDispatcher 周期抢占）；
 * 本情形不注册消费者（broker 可检索性由外部 mqadmin queryMsgByKey 按 event-id 审计，
 * 消费链已在情形一验证）。显式环境门控与 run-unique 隔离参数同情形一（{@code t2.*}）。</p>
 */
@SpringBootTest(classes = SendRetryE2ETest.App.class, properties = {
        "fastddd.event.mode=outbox",
        "spring.datasource.url=jdbc:h2:mem:eco08t2;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/outbox_events_h2.sql",
        "rocketmq.enabled=true",
        "fastddd.event.outbox.transport=rocketmq",
        "rocketmq.publisher.send-timeout=5000",
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.rocketmq", matches = "true",
        disabledReason = "需真实 RocketMQ（-Dfastddd.test.rocketmq=true 显式开启）")
class SendRetryE2ETest {

    static final String NAME_SERVER = System.getProperty("fastddd.test.rocketmq.name-server", "127.0.0.1:9876");
    static final String TOPIC = System.getProperty("fastddd.test.rocketmq.t2.topic", "fastddd_eco08_t2_retry_local");
    static final String PRODUCER_GROUP = System.getProperty("fastddd.test.rocketmq.t2.producer-group", "fastddd-eco08-p2-local");
    static final String AGGREGATE_TYPE = "Eco08Shipment";

    /** 装饰器统计：send 总尝试 / 注入故障次数（测试断言 + 证据输出）。 */
    static final AtomicInteger SEND_ATTEMPTS = new AtomicInteger();
    static final AtomicInteger INJECTED_FAULTS = new AtomicInteger();

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
    }

    @Test
    @Timeout(600)
    void sendFailure_marksFailedWithBackoff_thenRealBrokerDeliveryAfterRecovery() throws Exception {
        // 1. 业务事务内发布 → outbox PENDING
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(st -> eventPublisher.publishEvent(new ShipmentDispatchedEvent()));

        // 2. 等待首次投递被注入故障 → FAILED（dispatch 初始延迟≤5s + 周期 5s，90s 上限）
        long deadline = System.currentTimeMillis() + 90_000;
        OutboxEvent failed = null;
        while (System.currentTimeMillis() < deadline) {
            OutboxEvent row = latestRow();
            if (row != null && row.getStatus() == OutboxEventStatus.FAILED) {
                failed = row;
                break;
            }
            Thread.sleep(1000);
        }
        assertNotNull(failed, "90s 内应观察到注入故障导致的 FAILED 状态");
        LocalDateTime observedFailedAt = LocalDateTime.now();
        String eventId = failed.getEventId();
        // 外部审计锚点（mqadmin queryMsgByKey -k <eventId>）
        System.out.println("ECO08-T2-EVENT-ID: " + eventId);
        System.out.println("ECO08-T2-FAULT-INJECTION: decorator threw OutboxTransportException on send attempt #1");

        // 3. 断言失败状态机：FAILED + retryCount=1 + 错误信息含注入标记
        assertEquals(1, failed.getRetryCount(), "首次失败后 retryCount 应为 1");
        assertNotNull(failed.getErrorMessage(), "errorMessage 应记录失败原因");
        assertTrue(failed.getErrorMessage().contains("ECO-RELEASE-08"),
                "errorMessage 应含注入标记（实测=" + failed.getErrorMessage() + "）");

        // 4. 断言指数退避：框架 2^retryCount 分钟（retryCount=1 → 2min），观测窗 [90s, 210s] 容纳轮询/落库时延
        assertNotNull(failed.getNextRetryAt(), "nextRetryAt 应已安排");
        long backoffSec = Duration.between(observedFailedAt, failed.getNextRetryAt()).getSeconds();
        System.out.println("ECO08-T2-BACKOFF-SECONDS: " + backoffSec);
        assertTrue(backoffSec >= 90 && backoffSec <= 210,
                "退避窗口应在 2min±容差内（观测 FAILED 时刻→nextRetryAt = " + backoffSec + "s，期望≈120s）");

        // 5. 退避到期后框架 Dispatcher 重新抢占 → 委托真实 transport → 真实 Broker → PUBLISHED（8min 上限）
        long publishDeadline = System.currentTimeMillis() + 480_000;
        OutboxEvent published = failed;
        while (System.currentTimeMillis() < publishDeadline) {
            published = outboxEventRepository.selectByEventId(eventId);
            if (published.getStatus() == OutboxEventStatus.PUBLISHED) {
                break;
            }
            Thread.sleep(2000);
        }
        assertEquals(OutboxEventStatus.PUBLISHED, published.getStatus(),
                "退避到期后应恢复投递并 PUBLISHED（实测状态=" + published.getStatus() + "）");
        assertNotNull(published.getPublishedAt(), "publishedAt 应记录");
        assertEquals(1, published.getRetryCount(), "恢复成功后 retryCount 应保持 1（不再累计）");
        assertEquals(1, INJECTED_FAULTS.get(), "装饰器应恰好注入 1 次故障");
        assertTrue(SEND_ATTEMPTS.get() >= 2,
                "恢复投递应经装饰器委托真实 transport（send 总尝试=" + SEND_ATTEMPTS.get() + "）");
        System.out.println("ECO08-T2-SEND-ATTEMPTS: " + SEND_ATTEMPTS.get());
    }

    private OutboxEvent latestRow() {
        List<OutboxEvent> rows = outboxEventRepository.selectList(
                new QueryWrapper<OutboxEvent>().eq("aggregate_type", AGGREGATE_TYPE)
                        .orderByDesc("id"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 测试领域事件。 */
    static class ShipmentDispatchedEvent extends DomainEvent {
        private final java.time.Instant dispatchedAt = java.time.Instant.now();
        private final String warehouse = "WH-01";

        ShipmentDispatchedEvent() {
            super("eco08-t2-agg-001", AGGREGATE_TYPE);
        }

        public java.time.Instant getDispatchedAt() {
            return dispatchedAt;
        }

        public String getWarehouse() {
            return warehouse;
        }
    }

    /** 故障注入装饰器：前 {@code failFirstN} 次 send 抛 OutboxTransportException，其余委托真实 transport。 */
    static class FaultInjectingOutboxTransport implements OutboxTransport {

        private final OutboxTransport delegate;
        private final int failFirstN;

        FaultInjectingOutboxTransport(OutboxTransport delegate, int failFirstN) {
            this.delegate = delegate;
            this.failFirstN = failFirstN;
        }

        @Override
        public void send(OutboxEvent event) throws OutboxTransportException {
            int attempt = SEND_ATTEMPTS.incrementAndGet();
            if (attempt <= failFirstN) {
                INJECTED_FAULTS.incrementAndGet();
                throw new OutboxTransportException(event,
                        "ECO-RELEASE-08 情形二客户端侧故障注入（测试 JVM 内 transport 装饰器，非 Broker 故障）: attempt="
                                + attempt + ", eventId=" + event.getEventId(),
                        new IllegalStateException("simulated client-side send failure"), false);
            }
            delegate.send(event);
        }
    }

    @SpringBootApplication
    static class App {

        /** 非 web 消费者契约（ECO-006C6）：自备 ObjectMapper（findAndRegisterModules）。 */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /**
         * 故障注入 transport（覆盖自动装配；RocketMQEventAutoConfiguration 经
         * @ConditionalOnMissingBean(OutboxTransport) 退让）。真实 transport 作为委托目标。
         */
        @Bean
        OutboxTransport outboxTransport(RocketMQTemplate rocketMQTemplate, RocketMQConfig rocketMQConfig) {
            return new FaultInjectingOutboxTransport(
                    new RocketMqOutboxTransport(rocketMQTemplate, rocketMQConfig), 1);
        }
    }
}
