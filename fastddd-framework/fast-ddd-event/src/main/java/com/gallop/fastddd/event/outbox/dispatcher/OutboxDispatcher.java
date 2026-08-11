package com.gallop.fastddd.event.outbox.dispatcher;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.OutboxEvent;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransportException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Outbox 调度器（EVT-003 / ADR-003 §2.1）。
 *
 * <p>框架内置的调度大脑，职责单一：从 outbox 表抢占一批事件 → 经 {@link OutboxTransport} 投递 →
 * 按结果做状态转换（{@code PUBLISHED} / {@code FAILED}→重试→{@code DEAD_LETTER}）→ 周期回收
 * 过期租约。它<b>不</b>直接操作 outbox 表存储——所有表写入经 {@link OutboxEventService}，
 * 所有外部投递经 {@link OutboxTransport} SPI。
 *
 * <p>调度模型（EVT-003 重设计，取代 RocketMQPublisher 的双 @Scheduled 扫描）：
 * <ul>
 *   <li><b>dispatch 周期</b>：{@code claimBatch(batchSize, leaseTimeout)} 在单一事务内
 *       悲观锁抢占（PENDING + 可重试 FAILED）并写租约，COMMIT 后才调 {@code transport.send}
 *       ——投递在事务之外，不持有 FOR UPDATE 锁。send 成功 → markAsPublished；失败 →
 *       markAsFailed（重试耗尽自动入死信，见 EVT-002）。单循环同时覆盖新事件与重试事件。</li>
 *   <li><b>reclaim 周期</b>：扫描租约过期的 PROCESSING（dispatcher 崩溃残留），重置回 PENDING
 *       供下一轮重新投递，保证 at-least-once。</li>
 * </ul>
 *
 * <p><b>编程式调度</b>：使用 JDK {@link ScheduledExecutorService}（守护线程），周期取自
 * {@link EventProperties.Outbox#getDispatcher()} 的 {@link java.time.Duration} 配置，
 * 单一事实源。不依赖 Spring 的 @EnableScheduling（后者此前在 EventAutoConfiguration 上全局开启，
 * 即使 local 模式也会激活 @Scheduled 后处理器——ADR §1.16 缺陷；随 EVT-003 移除）。
 *
 * <p><b>异常隔离</b>：单次 dispatch/reclaim 周期内的异常被捕获记录，不传播到 executor
 * （否则 scheduleWithFixedDelay 会终止该任务，静默停止投递）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    /** 单条错误信息入库截断长度（与原 RocketMQPublisher.handlePublishFailure 保持一致）。 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final OutboxEventService outboxEventService;
    private final OutboxTransport transport;
    private final EventProperties properties;

    /** 用 AtomicReference 而非直接字段，确保构造失败时 @PreDestroy 不会 NPE。 */
    private final AtomicReference<ScheduledExecutorService> executorRef = new AtomicReference<>();

    public OutboxDispatcher(OutboxEventService outboxEventService,
                            OutboxTransport transport,
                            EventProperties properties) {
        this.outboxEventService = outboxEventService;
        this.transport = transport;
        this.properties = properties;
    }

    /**
     * 启动 dispatch + reclaim 两个周期任务（守护线程）。
     */
    @PostConstruct
    void start() {
        EventProperties.Dispatcher d = properties.getOutbox().getDispatcher();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, daemonThreadFactory());
        executorRef.set(executor);

        long pollMs = Math.max(1, d.getPollInterval().toMillis());
        long reclaimMs = Math.max(1, d.getReclaimInterval().toMillis());

        // 初始延迟取各自周期的较小值，给上下文一个完成启动的窗口，避免 t=0 抢占
        executor.scheduleWithFixedDelay(
                this::dispatchSafely, Math.min(pollMs, 5000L), pollMs, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(
                this::reclaimSafely, reclaimMs, reclaimMs, TimeUnit.MILLISECONDS);

        log.info("[Outbox-Dispatcher] 已启动: transport={}, batchSize={}, pollInterval={}ms, "
                        + "retryInterval={}ms, reclaimInterval={}ms, leaseTimeout={}ms",
                transport.getClass().getName(),
                d.getBatchSize(), pollMs, d.getRetryInterval().toMillis(),
                reclaimMs, d.getLeaseTimeout().toMillis());
    }

    /**
     * 优雅关闭调度线程池，等待在途周期完成。
     */
    @PreDestroy
    void stop() {
        ScheduledExecutorService executor = executorRef.getAndSet(null);
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[Outbox-Dispatcher] 强制关闭：在途任务未在 5s 内完成");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("[Outbox-Dispatcher] 已停止");
    }

    /**
     * 单次投递周期（包异常，防 scheduleWithFixedDelay 静默终止）。
     */
    private void dispatchSafely() {
        try {
            dispatch();
        } catch (Throwable t) {
            // 兜底：单条事件级异常已在 dispatchOne 处理，这里只记周期级未预期错误
            log.error("[Outbox-Dispatcher] dispatch 周期未预期异常", t);
        }
    }

    private void reclaimSafely() {
        try {
            int reclaimed = outboxEventService.reclaimExpiredLeases(LocalDateTime.now(),
                    properties.getOutbox().getDispatcher().getBatchSize());
            if (reclaimed > 0) {
                log.info("[Outbox-Dispatcher] 回收过期租约: reclaimed={}", reclaimed);
            }
        } catch (Throwable t) {
            log.error("[Outbox-Dispatcher] reclaim 周期未预期异常", t);
        }
    }

    /**
     * 抢占一批事件并逐个投递。public 便于测试直接驱动单次周期。
     */
    public void dispatch() {
        EventProperties.Dispatcher d = properties.getOutbox().getDispatcher();
        List<OutboxEvent> batch = outboxEventService.claimBatch(d.getBatchSize(), d.getLeaseTimeout());
        if (batch.isEmpty()) {
            return;
        }
        int success = 0;
        int failure = 0;
        for (OutboxEvent event : batch) {
            if (dispatchOne(event)) {
                success++;
            } else {
                failure++;
            }
        }
        if (success > 0 || failure > 0) {
            log.info("[Outbox-Dispatcher] 投递批次完成: success={}, failure={}", success, failure);
        }
    }

    /**
     * 投递单个事件并做状态转换。
     *
     * @return true=已标记 PUBLISHED；false=已标记 FAILED（或流转死信）
     */
    private boolean dispatchOne(OutboxEvent event) {
        try {
            transport.send(event);
            outboxEventService.markAsPublished(event.getEventId());
            return true;
        } catch (OutboxTransportException e) {
            log.warn("[Outbox-Dispatcher] 传输失败: eventId={}, timeout={}",
                    event.getEventId(), e.isTimeout(), e);
            outboxEventService.markAsFailed(event.getEventId(), truncate(e.getMessage()));
            return false;
        } catch (Throwable t) {
            // 兜底：transport 抛了非声明异常，同样视作失败（重试耗尽自动入死信）
            log.warn("[Outbox-Dispatcher] 传输未预期异常: eventId={}", event.getEventId(), t);
            outboxEventService.markAsFailed(event.getEventId(), truncate(t.toString()));
            return false;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread thread = new Thread(runnable, "outbox-dispatcher-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
