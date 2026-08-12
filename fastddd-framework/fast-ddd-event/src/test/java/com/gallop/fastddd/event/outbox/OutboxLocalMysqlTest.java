package com.gallop.fastddd.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 MySQL Outbox 多实例验证（QA-002）。
 *
 * <p>仅在 {@code -Dfastddd.test.local-mysql=true} 时运行。调用方必须提供
 * {@code fastddd.test.mysql.url}、{@code fastddd.test.mysql.username} 与
 * {@code fastddd.test.mysql.password}，并指向可丢弃的专用数据库。测试启动时只会重建
 * {@code outbox_events} 表，不会创建、删除或清理数据库本身。</p>
 */
@SpringBootTest(classes = OutboxLocalMysqlTest.TestApp.class, properties = {
        "fastddd.event.mode=outbox",
        "fastddd.event.outbox.dispatcher.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/outbox_events_mysql_local_reset.sql,classpath:sql/outbox_events_mysql.sql"
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.local-mysql", matches = "true",
        disabledReason = "需显式指定隔离 MySQL 数据库，详见类 Javadoc")
class OutboxLocalMysqlTest {

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredProperty("fastddd.test.mysql.url"));
        registry.add("spring.datasource.username", () -> requiredProperty("fastddd.test.mysql.username"));
        registry.add("spring.datasource.password", () -> requiredProperty("fastddd.test.mysql.password"));
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("启用本地 MySQL 验证时必须提供 -D" + name);
        }
        return value;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    private void seedPending(String eventId) {
        outboxEventRepository.insert(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateId("agg-1")
                .aggregateType("TestAggregate")
                .eventType("TestDomainEvent")
                .eventData("{}")
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .build());
    }

    @Test
    void concurrentClaim_eachEventClaimedByExactlyOneInstance() throws Exception {
        int total = 30;
        for (int i = 0; i < total; i++) {
            seedPending("local-race-" + i);
        }

        int instances = 4;
        Set<String> allClaimed = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CyclicBarrier barrier = new CyclicBarrier(instances);
        CountDownLatch done = new CountDownLatch(instances);
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        try {
            for (int t = 0; t < instances; t++) {
                pool.submit(() -> {
                    Set<String> mine = new HashSet<>();
                    try {
                        barrier.await();
                        for (int round = 0; round < total; round++) {
                            List<OutboxEvent> batch = outboxEventService.claimBatch(3, Duration.ofMinutes(2));
                            if (batch.isEmpty()) {
                                break;
                            }
                            for (OutboxEvent event : batch) {
                                assertTrue(mine.add(event.getEventId()), "同一实例不应重复 claim");
                            }
                        }
                        allClaimed.addAll(mine);
                    } catch (Throwable th) {
                        error.set(th);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS), "多实例应在 60 秒内完成抢占");
            if (error.get() != null) {
                throw new AssertionError("并发抢占异常", error.get());
            }
            assertEquals(total, allClaimed.size(), "每条事件应仅被一个实例 claim");
            assertEquals(total, outboxEventService.getEventCountByStatus(OutboxEventStatus.PROCESSING));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLease_isReclaimedAndCanBeClaimedAgain() {
        seedPending("local-crash");
        Duration lease = Duration.ofSeconds(5);

        assertEquals(1, outboxEventService.claimBatch(1, lease).size());
        int reclaimed = outboxEventService.reclaimExpiredLeases(LocalDateTime.now().plus(lease).plusSeconds(1), 10);
        assertEquals(1, reclaimed);
        assertEquals(OutboxEventStatus.PENDING,
                outboxEventService.findByEventId("local-crash").orElseThrow().getStatus());
        assertEquals(1, outboxEventService.claimBatch(1, lease).size());
    }
}
