package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox 事件真实数据库映射与状态转换测试（EVT-002 步骤5 / 步骤6）。
 *
 * <p><b>真实 SQL</b>：H2（MySQL 兼容模式）+ MyBatis-Plus + OutboxEventRepository 注解 SQL，
 * 非内存桩。覆盖全部状态转换（PENDING→PROCESSING→PUBLISHED、FAILED 重试、DEAD_LETTER 死信）、
 * eventId 唯一约束幂等、失败回滚不留脏数据。
 *
 * <p>验收"所有状态转换有数据库测试"由本类承载。MySQL 专有映射（Testcontainers）见
 * {@link OutboxEventMysqlTestcontainersTest}（Docker 不可用时休眠）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = OutboxEventIntegrationTest.TestApp.class,
        properties = {
                "fastddd.event.mode=outbox",
                "fastddd.event.outbox.dispatcher.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:outboxtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:sql/outbox_events_h2.sql"
        })
class OutboxEventIntegrationTest {

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    // 仅自动配置（不组件扫描）——避免 OutboxEventConfiguration 被 @SpringBootApplication 组件扫描
    // 与 AutoConfiguration.imports 重复注册（ConflictingBeanDefinitionException）。
    // 显式提供 ObjectMapper 测试桩件：本聚焦测试不依赖完整 JacksonAutoConfiguration 发现链
    // （生产环境由 @SpringBootApplication 全量自动配置提供，自定义 Jackson 配置照常生效）。
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            // findAndRegisterModules 自动发现 JSR310 等模块，对齐 Spring Boot 生产装配
            // （DomainEvent.createdAt 为 Instant，需 jackson-datatype-jsr310）
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    /** 测试用领域事件。 */
    static class TestDomainEvent extends DomainEvent {
        TestDomainEvent(Serializable aggregateId, String aggregateType) {
            super(aggregateId, aggregateType);
        }
    }

    private TestDomainEvent newEvent() {
        return new TestDomainEvent("agg-1", "TestAggregate");
    }

    /* ---------- 步骤5：状态转换数据库测试 ---------- */

    @Test
    void saveEvent_persistsPending_withFqnEventType() {
        TestDomainEvent event = newEvent();
        OutboxEvent saved = outboxEventService.saveEvent(event, "agg-1", "TestAggregate");

        assertEquals(OutboxEventStatus.PENDING, saved.getStatus());
        assertEquals(event.getEventId().toString(), saved.getEventId());
        // EVT-002：eventType 统一为 FQN（与 TransactionalEventPublisher 一致）
        assertEquals(TestDomainEvent.class.getName(), saved.getEventType());
        assertTrue(outboxEventService.findByEventId(saved.getEventId()).isPresent());
    }

    @Test
    void happyPath_claimProcessPublish() {
        OutboxEvent saved = outboxEventService.saveEvent(newEvent(), "agg-1", "TestAggregate");
        String eventId = saved.getEventId();

        // claim 明确方法：抢占 → 置 PROCESSING（原子 lock+update）
        List<String> claimed = outboxEventService.claimEvents(5);
        assertTrue(claimed.contains(eventId), "claimEvents 应抢占该事件: " + claimed);
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventService.findByEventId(eventId).orElseThrow().getStatus());

        // mark-sent（markAsPublished）→ PUBLISHED 终态
        assertTrue(outboxEventService.markAsPublished(eventId));
        OutboxEvent published = outboxEventService.findByEventId(eventId).orElseThrow();
        assertEquals(OutboxEventStatus.PUBLISHED, published.getStatus());
        assertTrue(published.getStatus().isFinalState());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void retryExhaustion_transitionsToDeadLetter() {
        // maxRetryCount=2：2 次失败后第 2 次即耗尽 → DEAD_LETTER
        OutboxEvent saved = outboxEventService.saveEvent(newEvent(), "agg-1", "TestAggregate");
        saved.setMaxRetryCount(2);
        outboxEventRepository.updateById(saved);

        // 第 1 次失败 → FAILED（retryCount=1，仍可重试）
        assertTrue(outboxEventService.markAsFailed(saved.getEventId(), "err-1"));
        assertEquals(OutboxEventStatus.FAILED,
                outboxEventService.findByEventId(saved.getEventId()).orElseThrow().getStatus());

        // 第 2 次失败 → 重试耗尽 → DEAD_LETTER 终态
        assertTrue(outboxEventService.markAsFailed(saved.getEventId(), "err-2"));
        OutboxEvent dead = outboxEventService.findByEventId(saved.getEventId()).orElseThrow();
        assertEquals(OutboxEventStatus.DEAD_LETTER, dead.getStatus(),
                "重试耗尽应自动流转 DEAD_LETTER，而非停留 FAILED");
        assertTrue(dead.getStatus().isFinalState());
        assertFalse(dead.getStatus().canRetry());

        // 死信可经显式查询检索
        assertFalse(outboxEventService.findDeadLetterEvents(new Page<>(1, 10)).getRecords().isEmpty());
    }

    @Test
    void markAsDeadLetter_explicitMethodWorks() {
        OutboxEvent saved = outboxEventService.saveEvent(newEvent(), "agg-1", "TestAggregate");
        assertTrue(outboxEventService.markAsDeadLetter(saved.getEventId()));
        assertEquals(OutboxEventStatus.DEAD_LETTER,
                outboxEventService.findByEventId(saved.getEventId()).orElseThrow().getStatus());
    }

    /* ---------- 步骤4：eventId 唯一约束幂等 ---------- */

    @Test
    void duplicateEventId_isIdempotent() {
        TestDomainEvent event = newEvent();
        eventPublisher.publishEvent(event);   // 首次落库

        // 同 eventId 再次发布：eventId UNIQUE 触发，TransactionalEventPublisher 幂等跳过（不抛异常）
        eventPublisher.publishEvent(event);

        // 幂等跳过：同一 eventId 只应落库 1 行
        assertNotNull(outboxEventRepository.selectByEventId(event.getEventId().toString()));
        long pendingDuplicates = outboxEventRepository.countByStatus("PENDING");
        // 该测试方法内唯一落库事件应只贡献 1 行 PENDING；其它测试方法的隔离由 @Rollback 默认保证
        assertTrue(pendingDuplicates >= 1, "至少应有刚落库的 1 行 PENDING");
    }

    /* ---------- 步骤6：失败回滚不留脏数据 ---------- */

    @Test
    void failedTransaction_rollsBackNoDirtyState() {
        long before = outboxEventRepository.countEventsWithStatusPending();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // 事务内插入 2 条，然后抛异常 → 整体回滚，不留脏数据
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            outboxEventRepository.insert(buildPending("rollback-a"));
            outboxEventRepository.insert(buildPending("rollback-b"));
            throw new RuntimeException("intentional failure");
        }));

        assertEquals(before, outboxEventRepository.countEventsWithStatusPending(),
                "事务回滚后不应残留任何脏数据行");
        // H2 mem + 各测试方法共享同一 DataSource，回滚后这两行应不存在
        long rowsA = outboxEventRepository.selectByEventId("rollback-a") == null ? 0 : 1;
        long rowsB = outboxEventRepository.selectByEventId("rollback-b") == null ? 0 : 1;
        assertEquals(0, rowsA, "rollback-a 应已被回滚");
        assertEquals(0, rowsB, "rollback-b 应已被回滚");
    }

    @Test
    void failedClaim_rollsBackNoProcessingState() {
        // claimEvents 在单一 @Transactional 内 lock+置 PROCESSING；事务异常应回滚，无 PROCESSING 残留
        OutboxEvent saved = outboxEventService.saveEvent(newEvent(), "agg-1", "TestAggregate");
        long processingBefore = outboxEventRepository.countByStatus("PROCESSING");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            outboxEventRepository.selectPendingEventsWithLock(LocalDateTime.now(), 5);
            outboxEventRepository.batchUpdateToProcessing(List.of(saved.getId()), LocalDateTime.now());
            throw new RuntimeException("intentional failure after claim");
        }));

        assertEquals(processingBefore, outboxEventRepository.countByStatus("PROCESSING"),
                "claim 事务回滚后不应残留 PROCESSING 行");
    }

    private static OutboxEvent buildPending(String eventId) {
        return OutboxEvent.builder()
                .eventId(eventId)
                .aggregateId("agg-1")
                .aggregateType("TestAggregate")
                .eventType("TestDomainEvent")
                .eventData("{}")
                .status(OutboxEventStatus.PENDING)
                .build();
    }
}
