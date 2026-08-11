package com.gallop.fastddd.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Outbox 多实例 MySQL 真实竞争测试（EVT-004 步骤2）。
 *
 * <p>在真实 MySQL 8.0（Testcontainers）上验证 {@code claimBatch} 的 FOR UPDATE 串行化契约：
 * 多 dispatcher（多线程）并发抢占同一批 PENDING 事件时，同一事件只被一个实例 claim（不相交），
 * 并集 = 全集；以及崩溃后 lease 经 reclaim 恢复的真实 SQL 路径。
 *
 * <p><b>环境门禁</b>：仅 {@code -Dfastddd.test.docker=true} 时启用（本仓库 CI 无 Docker runner，
 * 内网 Gitea）。默认休眠，不影响 {@code clean verify -Ptest}。运行命令：
 * <pre>{@code
 * export JAVA_HOME="D:/usr/local/jdk/jdk-21.0.3"
 * ./mvnw -B test -pl fastddd-framework/fast-ddd-event -Dtest=OutboxMultiInstanceMysqlTest -Dfastddd.test.docker=true
 * }</pre>
 * 报告与提交号关联：surefire 报告（target/surefire-reports/）+ 构建时 {@code git rev-parse HEAD}。
 *
 * <p>调度逻辑的确定性覆盖（不依赖基础设施）由 {@code OutboxDispatcherConcurrencyTest} 承载；
 * 本类聚焦 MySQL InnoDB 行锁/FOR UPDATE 的真实并发行为。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Testcontainers
@SpringBootTest(classes = OutboxMultiInstanceMysqlTest.TestApp.class,
        properties = {
                "fastddd.event.mode=outbox",
                "fastddd.event.outbox.dispatcher.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:sql/outbox_events_mysql.sql"
        })
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.docker", matches = "true",
        disabledReason = "需 Docker（-Dfastddd.test.docker=true 显式开启）；CI 无 runner，详见类 Javadoc")
class OutboxMultiInstanceMysqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

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

    /**
     * 步骤2/3：多个 dispatcher 线程并发 claimBatch，同一事件只被一个实例抢占。
     *
     * <p>InnoDB 行锁 + FOR UPDATE：并发事务抢占同行时串行等待，第二个事务看到 PROCESSING 后
     * 跳过（batchClaimWithLease 的 AND status IN ('PENDING','FAILED') 谓词）。
     */
    @Test
    void concurrentClaim_eachEventClaimedByExactlyOneInstance() throws Exception {
        int total = 30;
        for (int i = 0; i < total; i++) {
            seedPending("race-" + i);
        }

        int instances = 4;
        Set<String> allClaimed = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CyclicBarrier barrier = new CyclicBarrier(instances);
        CountDownLatch done = new CountDownLatch(instances);
        Duration lease = Duration.ofMinutes(2);
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        try {
            for (int t = 0; t < instances; t++) {
                pool.submit(() -> {
                    Set<String> mine = new HashSet<>();
                    try {
                        barrier.await();
                        // 反复抢占直到无 PENDING 可抢（小批次放大竞争窗口）
                        for (int round = 0; round < total; round++) {
                            List<OutboxEvent> batch = outboxEventService.claimBatch(3, lease);
                            if (batch.isEmpty()) {
                                break;
                            }
                            for (OutboxEvent e : batch) {
                                assertTrue(mine.add(e.getEventId()),
                                        "同一实例内不应重复 claim: " + e.getEventId());
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
            assertTrue(done.await(60, TimeUnit.SECONDS), "4 实例应在 60s 内完成抢占");

            if (error.get() != null) {
                throw new AssertionError("并发抢占异常", error.get());
            }
            // 并集 = 全集 30，无遗漏；PROCESSING 30（均被抢占），PENDING 0
            assertEquals(total, allClaimed.size(),
                    "全部 " + total + " 条事件应恰好被 claim 一次: " + allClaimed);
            assertEquals(total, outboxEventService.getEventCountByStatus(OutboxEventStatus.PROCESSING));
            assertEquals(0, outboxEventService.getEventCountByStatus(OutboxEventStatus.PENDING));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 步骤4：实例崩溃后 lease 可恢复——claim 置 PROCESSING+lease 后不 ack，
     * 推进时钟过 lease，reclaimExpiredLeases 真实 SQL 重置回 PENDING，再次 claim 成功。
     */
    @Test
    void crashedInstance_expiredLeaseReclaimedAndReclaimed() {
        seedPending("crash-mysql");
        Duration lease = Duration.ofSeconds(5);

        // 模拟崩溃：claim 后不 markAsPublished，事件卡 PROCESSING 带 lease
        List<OutboxEvent> claimed = outboxEventService.claimBatch(1, lease);
        assertEquals(1, claimed.size());
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventService.findByEventId("crash-mysql").orElseThrow().getStatus());

        // 推进时钟过 lease（传明确晚于 leaseExpiresAt 的 now，确定性，无随机 sleep）
        LocalDateTime futureNow = LocalDateTime.now().plus(lease).plusSeconds(1);
        int reclaimed = outboxEventService.reclaimExpiredLeases(futureNow, 10);
        assertEquals(1, reclaimed, "过期 PROCESSING 应被回收");
        assertEquals(OutboxEventStatus.PENDING,
                outboxEventService.findByEventId("crash-mysql").orElseThrow().getStatus(),
                "回收后回 PENDING 待重投递");

        // 再次 claim → 抢占成功 → markAsPublished → PUBLISHED
        List<OutboxEvent> reClaimed = outboxEventService.claimBatch(1, lease);
        assertEquals(1, reClaimed.size());
        assertTrue(outboxEventService.markAsPublished("crash-mysql"));
        assertEquals(OutboxEventStatus.PUBLISHED,
                outboxEventService.findByEventId("crash-mysql").orElseThrow().getStatus());
    }

    /**
     * 步骤5：transport 失败重试在真实 DB 上不重复抢占——一次 claimBatch 内每事件恰好一次，
     * 失败落 FAILED（可重试），下一轮 claimBatch 重新抢占。
     */
    @Test
    void failedEvent_retryableReclaimedInSubsequentClaim() {
        seedPending("retry-mysql");
        Duration lease = Duration.ofMinutes(2);

        List<OutboxEvent> first = outboxEventService.claimBatch(1, lease);
        assertEquals(1, first.size());
        // 模拟投递失败
        assertTrue(outboxEventService.markAsFailed("retry-mysql", "transient"));
        OutboxEvent afterFail = outboxEventService.findByEventId("retry-mysql").orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, afterFail.getStatus());

        // FAILED+可重试应被下一轮 claimBatch 重新抢占（selectPendingEventsWithLock 含 FAILED 分支）
        List<OutboxEvent> second = outboxEventService.claimBatch(1, lease);
        assertEquals(1, second.size(), "FAILED 可重试事件应被重新抢占");
        assertEquals("retry-mysql", second.get(0).getEventId());
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventService.findByEventId("retry-mysql").orElseThrow().getStatus());
    }
}
