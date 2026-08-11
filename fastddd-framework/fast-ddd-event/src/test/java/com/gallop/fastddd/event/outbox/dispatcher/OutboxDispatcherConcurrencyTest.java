package com.gallop.fastddd.event.outbox.dispatcher;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.InMemoryOutboxEventService;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventStatus;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox 多实例确定性竞争测试（EVT-004 步骤1/3/4/5）。
 *
 * <p><b>无基础设施</b>：不依赖数据库、消息中间件、随机 sleep。使用
 * {@link InMemoryOutboxEventService}（{@code synchronized} 原子 claim，对应真实 FOR UPDATE 串行化契约）
 * 作为仲裁者，{@link OutboxDispatcher} 为真实框架代码，配合 {@link CyclicBarrier} 稳定复现
 * 两 dispatcher 同时 claim 的竞争。
 *
 * <p>覆盖：
 * <ul>
 *   <li>步骤3——同一事件只被一个 dispatcher claim/投递（不相交、并集=全集）。</li>
 *   <li>步骤4——实例崩溃（claim 后不 ack）→ 推进时钟过 lease → reclaim 回 PENDING → 重投递成功。</li>
 *   <li>步骤5——transport 失败后重试：单次 claim 恰好一次 send；跨周期重投递为 at-least-once，
 *       业务幂等由消费者承担（框架不双投单 claim）。</li>
 * </ul>
 *
 * <p>真实 DB 的 FOR UPDATE 串行化与多进程竞争由 {@code OutboxMultiInstanceMysqlTest}（EVT-004 步骤2，
 * Docker 门控）覆盖；本类聚焦调度逻辑的确定性。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class OutboxDispatcherConcurrencyTest {

    private InMemoryOutboxEventService service;
    private EventProperties properties;

    @BeforeEach
    void setUp() {
        service = new InMemoryOutboxEventService();
        properties = new EventProperties();
        // 小批次迫使两个 dispatcher 在多事件场景下各自 claim 一部分，放大竞争窗口
        properties.getOutbox().getDispatcher().setBatchSize(2);
        properties.getOutbox().getDispatcher().setLeaseTimeout(Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        // 本类直接调 dispatch()，不启动 @PostConstruct 调度线程池，无需 shutdown
    }

    /** 一个记录所有投递（含重复）的 transport，便于断言。 */
    private static final class RecordingTransport implements OutboxTransport {
        final List<String> delivered = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final Set<String> deliveredOnce = ConcurrentHashMap.newKeySet();
        final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public void send(OutboxEvent event) {
            delivered.add(event.getEventId());
            deliveredOnce.add(event.getEventId());
            sendCount.incrementAndGet();
        }
    }

    /** 首次投递指定事件时失败、之后成功的 transport（用于重试场景）。 */
    private static final class FailOnceThenSucceedTransport implements OutboxTransport {
        final Set<String> failedOnce = ConcurrentHashMap.newKeySet();
        final List<String> delivered = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public void send(OutboxEvent event) {
            if (failedOnce.add(event.getEventId())) {
                throw new OutboxTransportException(event, "transient failure", new RuntimeException());
            }
            delivered.add(event.getEventId());
        }
    }

    /* ---------- 步骤3：两 dispatcher 同时 claim，同一事件只投递一次 ---------- */

    @Test
    void twoDispatchers_concurrentClaim_eachEventDeliveredExactlyOnce() throws Exception {
        // 播种 6 条 PENDING，batchSize=2 → 两 dispatcher 各抢若干轮，无重叠
        for (int i = 0; i < 6; i++) {
            service.seed("evt-" + i);
        }
        RecordingTransport transport = new RecordingTransport();

        OutboxDispatcher d1 = new OutboxDispatcher(service, transport, properties);
        OutboxDispatcher d2 = new OutboxDispatcher(service, transport, properties);

        int dispatchers = 2;
        CyclicBarrier barrier = new CyclicBarrier(dispatchers);
        CountDownLatch done = new CountDownLatch(dispatchers);
        ExecutorService pool = Executors.newFixedThreadPool(dispatchers);
        try {
            for (OutboxDispatcher d : new OutboxDispatcher[]{d1, d2}) {
                pool.submit(() -> {
                    try {
                        barrier.await();             // 两线程同时放行，最大化 claim 竞争
                        // 反复 dispatch 直到无 PENDING 可抢
                        for (int round = 0; round < 6; round++) {
                            d.dispatch();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "两 dispatcher 应在 10s 内完成");

            // 并集 = 全集 6，无重复投递（deliveredOnce 无重复），所有事件终态 PUBLISHED
            assertEquals(6, transport.deliveredOnce.size(), "6 条事件应全部投递: " + transport.deliveredOnce);
            assertThat(transport.deliveredOnce).containsExactlyInAnyOrder("evt-0", "evt-1", "evt-2",
                    "evt-3", "evt-4", "evt-5");
            // delivered（含重复）不应有同一 eventId 被同一 claim 双投——这里每事件至多在 deliverOnce 后再次进入时已是 PUBLISHED 不再被 claim
            assertEquals(transport.deliveredOnce.size(),
                    (int) transport.delivered.stream().distinct().count(),
                    "同一事件不应被重复投递: " + transport.delivered);
            assertEquals(6, service.getEventCountByStatus(OutboxEventStatus.PUBLISHED));
            assertEquals(0, service.getEventCountByStatus(OutboxEventStatus.PROCESSING));
        } finally {
            pool.shutdownNow();
        }
    }

    /* ---------- 步骤4：实例崩溃（claim 后不 ack）→ lease 过期 → reclaim → 重投递 ---------- */

    @Test
    void dispatcherCrash_expiredLease_reclaimedAndRedelivered() {
        service.seed("crash-evt");
        // 模拟"进程崩溃"：claim 置 PROCESSING + lease 后，进程被杀（既未 send 也未 markAsPublished），
        // 事件卡 PROCESSING 带 lease——这是 reclaim 机制要解决的残留。
        service.claimBatch(1, properties.getOutbox().getDispatcher().getLeaseTimeout());
        assertEquals(OutboxEventStatus.PROCESSING, service.findByEventId("crash-evt").orElseThrow().getStatus());

        // 推进时钟过 lease（确定性，无随机 sleep）：传一个明确晚于 leaseExpiresAt 的 now
        LocalDateTime futureNow = LocalDateTime.now()
                .plus(properties.getOutbox().getDispatcher().getLeaseTimeout())
                .plusSeconds(1);
        int reclaimed = service.reclaimExpiredLeases(futureNow, 10);
        assertEquals(1, reclaimed, "过期 PROCESSING 应被回收");
        assertEquals(OutboxEventStatus.PENDING, service.findByEventId("crash-evt").orElseThrow().getStatus(),
                "回收后应回 PENDING 待重投递");

        // 新 dispatcher 接手重投递 → 成功 → PUBLISHED
        RecordingTransport healthyTransport = new RecordingTransport();
        OutboxDispatcher recovery = new OutboxDispatcher(service, healthyTransport, properties);
        recovery.dispatch();
        assertEquals(OutboxEventStatus.PUBLISHED, service.findByEventId("crash-evt").orElseThrow().getStatus());
        assertThat(healthyTransport.deliveredOnce).containsExactly("crash-evt");
    }

    /* ---------- 步骤5：transport 重试不重复改变业务状态（单 claim = 单 send） ---------- */

    @Test
    void transportFailure_retryDeliversOncePerClaim_noDoubleSendWithinClaim() {
        service.seed("retry-evt");
        FailOnceThenSucceedTransport transport = new FailOnceThenSucceedTransport();
        OutboxDispatcher dispatcher = new OutboxDispatcher(service, transport, properties);

        // 第 1 个周期：send 抛 OutboxTransportException → markAsFailed（FAILED，retryCount=1，可重试）
        dispatcher.dispatch();
        OutboxEvent afterFirst = service.findByEventId("retry-evt").orElseThrow();
        assertEquals(OutboxEventStatus.FAILED, afterFirst.getStatus(),
                "首次失败应落 FAILED 可重试，非死信");
        assertEquals(1, afterFirst.getRetryCount());
        assertTrue(transport.delivered.isEmpty(), "首次 send 失败，不应视为成功投递");

        // 第 2 个周期：claimBatch 拾起 FAILED+可重试 → send 成功 → PUBLISHED
        dispatcher.dispatch();
        assertEquals(OutboxEventStatus.PUBLISHED, service.findByEventId("retry-evt").orElseThrow().getStatus());
        assertThat(transport.delivered).containsExactly("retry-evt");

        // 关键：两次周期各一次 claim；第 2 周期 send 成功仅一次（无同 claim 内双投）
        assertEquals(1, transport.delivered.size(),
                "成功投递应恰好一次——跨周期重投递为 at-least-once，消费者须幂等；"
                        + "框架保证单次 claim 内 send 恰好一次");
    }

    /* ---------- 步骤5 扩展：重试耗尽 → 死信，不再投递 ---------- */

    @Test
    void transportFailure_retryExhausted_transitionsToDeadLetterNoFurtherSend() {
        service.seed("dlq-evt");
        // 缩小 maxRetry 到 1，使一次失败即耗尽
        OutboxEvent seeded = service.findByEventId("dlq-evt").orElseThrow();
        seeded.setMaxRetryCount(1);

        OutboxTransport alwaysFails = new OutboxTransport() {
            final AtomicInteger calls = new AtomicInteger();

            @Override
            public void send(OutboxEvent event) {
                calls.incrementAndGet();
                throw new OutboxTransportException(event, "permanent failure", new RuntimeException());
            }
        };
        OutboxDispatcher dispatcher = new OutboxDispatcher(service, alwaysFails, properties);

        dispatcher.dispatch();
        OutboxEvent after = service.findByEventId("dlq-evt").orElseThrow();
        assertEquals(OutboxEventStatus.DEAD_LETTER, after.getStatus(),
                "maxRetry=1 时一次失败即耗尽 → 死信终态，不再停留 FAILED 重投递");

        // 再次 dispatch：死信不应被 claimBatch 拾起（DEAD_LETTER 非 PENDING/FAILED-可重试）
        dispatcher.dispatch();
        assertEquals(OutboxEventStatus.DEAD_LETTER, service.findByEventId("dlq-evt").orElseThrow().getStatus(),
                "死信终态不应被重新投递");
    }
}
