package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.common.domain.DomainEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内、确定性 {@link OutboxEventService} 测试替身（EVT-004 步骤1）。
 *
 * <p><b>用途</b>：为 EVT-004 的确定性并发测试提供不依赖 DB/MQ 的 claim 仲裁者——
 * 用 {@code synchronized} 原子化 claimBatch，忠实体现实服务经 FOR UPDATE 提供的"原子抢占"契约。
 * 配合 {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher}（真实框架代码）+ barrier/latch，
 * 稳定复现多 dispatcher 竞争、崩溃恢复、重投递幂等等场景。真实 DB 的 FOR UPDATE 串行化由
 * MySQL Testcontainers 测试（EVT-004 步骤2）覆盖。
 *
 * <p><b>未用方法</b>抛 {@link UnsupportedOperationException}——本替身仅实现 dispatcher 链路
 * 与断言所需方法（saveEvent / findByEventId / claimBatch / markAsPublished / markAsFailed /
 * reclaimExpiredLeases / getEventCountByStatus）。
 *
 * <p><b>时钟</b>：claimBatch 的 claimedAt 用 {@link LocalDateTime#now()}（与现服务一致），
 * lease 回收的判定时间由调用方经 {@link #reclaimExpiredLeases(LocalDateTime, int)} 显式传入——
 * 测试传一个明确晚于 leaseExpiresAt 的时间即可稳定触发，无需随机 sleep。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class InMemoryOutboxEventService implements OutboxEventService {

    private final ConcurrentMap<String, OutboxEvent> store = new ConcurrentHashMap<>();
    private long idSeq = 0;

    /** 测试播种：以 PENDING 落库一条事件，返回带 id 的实体。 */
    public synchronized OutboxEvent seed(String eventId) {
        OutboxEvent event = OutboxEvent.builder()
                .id(++idSeq)
                .eventId(eventId)
                .aggregateId("agg-1")
                .aggregateType("TestAggregate")
                .eventType("TestDomainEvent")
                .eventData("{}")
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .build();
        store.put(eventId, event);
        return event;
    }

    @Override
    public synchronized OutboxEvent saveEvent(DomainEvent domainEvent, String aggregateId, String aggregateType) {
        OutboxEvent event = OutboxEvent.builder()
                .id(++idSeq)
                .eventId(domainEvent.getEventId().toString())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(domainEvent.getClass().getName())
                .eventData("{}")
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(3)
                .build();
        store.put(event.getEventId(), event);
        return event;
    }

    @Override
    public synchronized List<OutboxEvent> claimBatch(int limit, Duration leaseTimeout) {
        // 原子抢占：synchronized 保证两个并发 dispatcher 的 claimBatch 串行——
        // 第二个调用看见第一个写入的 PROCESSING，跳过，确保不相交（对应真实 FOR UPDATE 串行化）。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiresAt = now.plus(leaseTimeout);
        List<OutboxEvent> claimed = new ArrayList<>();
        for (OutboxEvent event : store.values()) {
            if (claimed.size() >= limit) {
                break;
            }
            if (event.getStatus() == OutboxEventStatus.PENDING
                    || (event.getStatus() == OutboxEventStatus.FAILED && event.canRetry())) {
                event.claim(now, leaseExpiresAt);
                claimed.add(event);
            }
        }
        return claimed;
    }

    @Override
    public synchronized int reclaimExpiredLeases(LocalDateTime now, int limit) {
        int reclaimed = 0;
        for (OutboxEvent event : store.values()) {
            if (reclaimed >= limit) {
                break;
            }
            if (event.isLeaseExpired(now)) {
                event.resetExpiredLease(now);
                reclaimed++;
            }
        }
        return reclaimed;
    }

    @Override
    public synchronized boolean markAsPublished(String eventId) {
        OutboxEvent event = store.get(eventId);
        if (event != null) {
            event.markAsPublished();
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean markAsFailed(String eventId, String errorMessage) {
        OutboxEvent event = store.get(eventId);
        if (event == null) {
            return false;
        }
        event.markAsFailed(errorMessage);
        if (!event.canRetry()) {
            event.markAsDeadLetter();
        }
        return true;
    }

    @Override
    public synchronized boolean markAsDeadLetter(String eventId) {
        OutboxEvent event = store.get(eventId);
        if (event == null) {
            return false;
        }
        event.markAsDeadLetter();
        return true;
    }

    @Override
    public synchronized Optional<OutboxEvent> findByEventId(String eventId) {
        return Optional.ofNullable(store.get(eventId));
    }

    @Override
    public synchronized long getEventCountByStatus(OutboxEventStatus status) {
        return store.values().stream().filter(e -> e.getStatus() == status).count();
    }

    /* ---------- 以下为 dispatcher 链路与断言不需要的方法：显式不支持 ---------- */

    @Override
    public List<OutboxEvent> saveEvents(List<DomainEvent> domainEvents, String aggregateId, String aggregateType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findPendingEvents(Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findEventsByStatus(OutboxEventStatus status, Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findEventsByAggregate(String aggregateId, String aggregateType, Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findEventsByType(String eventType, Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markAsProcessing(String eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int batchMarkAsProcessing(List<String> eventIds) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> claimEvents(int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markAsCancelled(String eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean resetToPending(String eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getPendingEventCount() {
        return getEventCountByStatus(OutboxEventStatus.PENDING);
    }

    @Override
    public int cleanupHistoryData(LocalDateTime beforeTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findFailedEventsExceedingMaxRetries(Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPage<OutboxEvent> findDeadLetterEvents(Page<OutboxEvent> page) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutboxEventStatistics getStatistics() {
        throw new UnsupportedOperationException();
    }
}
