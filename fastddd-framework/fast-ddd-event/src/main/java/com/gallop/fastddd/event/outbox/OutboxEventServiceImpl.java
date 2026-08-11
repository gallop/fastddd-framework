package com.gallop.fastddd.event.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Outbox事件服务实现类
 * 
 * 提供Outbox事件的核心业务功能实现。
 * 
 * @author gallop
 * @since 2025-01-09
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {
    
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public OutboxEvent saveEvent(DomainEvent domainEvent, String aggregateId, String aggregateType) {
        try {
            String eventData = objectMapper.writeValueAsString(domainEvent);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(domainEvent.getEventId().toString())
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(domainEvent.getClass().getName())
                    .eventData(eventData)
                    .status(OutboxEventStatus.PENDING)
                    .build();
            
            outboxEventRepository.insert(outboxEvent);
            OutboxEvent savedEvent = outboxEvent;
            log.debug("保存Outbox事件成功: eventId={}, aggregateId={}, eventType={}", 
                     savedEvent.getEventId(), aggregateId, savedEvent.getEventType());
            
            return savedEvent;
        } catch (JsonProcessingException e) {
            log.error("序列化领域事件失败: eventId={}, eventType={}", 
                     domainEvent.getEventId(), domainEvent.getClass().getSimpleName(), e);
            throw new RuntimeException("序列化领域事件失败", e);
        }
    }
    
    @Override
    @Transactional
    public List<OutboxEvent> saveEvents(List<DomainEvent> domainEvents, String aggregateId, String aggregateType) {
        List<OutboxEvent> outboxEvents = domainEvents.stream()
                .map(event -> {
                    try {
                        String eventData = objectMapper.writeValueAsString(event);
                        return OutboxEvent.builder()
                                .eventId(event.getEventId().toString())
                                .aggregateId(aggregateId)
                                .aggregateType(aggregateType)
                                .eventType(event.getClass().getName())
                                .eventData(eventData)
                                .status(OutboxEventStatus.PENDING)
                                .build();
                    } catch (JsonProcessingException e) {
                        log.error("序列化领域事件失败: eventId={}, eventType={}", 
                                 event.getEventId(), event.getClass().getSimpleName(), e);
                        throw new RuntimeException("序列化领域事件失败", e);
                    }
                })
                .collect(Collectors.toList());
        
        outboxEvents.forEach(outboxEventRepository::insert);
        List<OutboxEvent> savedEvents = outboxEvents;
        log.debug("批量保存Outbox事件成功: count={}, aggregateId={}", savedEvents.size(), aggregateId);
        
        return savedEvents;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEvent> findByEventId(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        return Optional.ofNullable(event);
    }
    
    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findPendingEvents(Page<OutboxEvent> page) {
        return outboxEventRepository.selectPendingEvents(LocalDateTime.now(), page);
    }
    
    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findEventsByStatus(OutboxEventStatus status, Page<OutboxEvent> page) {
        return outboxEventRepository.selectByStatusOrderByCreatedAtAsc(status.name(), page);
    }
    
    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findEventsByAggregate(String aggregateId, String aggregateType, Page<OutboxEvent> page) {
        return outboxEventRepository.selectByAggregateIdAndAggregateTypeOrderByCreatedAtDesc(
                aggregateId, aggregateType, page);
    }
    
    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findEventsByType(String eventType, Page<OutboxEvent> page) {
        return outboxEventRepository.selectByEventTypeOrderByCreatedAtDesc(eventType, page);
    }
    
    @Override
    @Transactional
    public boolean markAsProcessing(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            if (event.getStatus().canRetry()) {
                event.markAsProcessing();
                outboxEventRepository.updateById(event);
                log.debug("标记事件为处理中: eventId={}", eventId);
                return true;
            } else {
                log.warn("事件状态不允许标记为处理中: eventId={}, status={}", eventId, event.getStatus());
            }
        } else {
            log.warn("未找到事件: eventId={}", eventId);
        }
        return false;
    }
    
    @Override
    @Transactional
    public int batchMarkAsProcessing(List<String> eventIds) {
        List<OutboxEvent> events = eventIds.stream()
                .map(outboxEventRepository::selectByEventId)
                .filter(event -> event != null)
                .filter(event -> event.getStatus().canRetry())
                .collect(Collectors.toList());
        
        List<Long> ids = events.stream()
                .map(OutboxEvent::getId)
                .collect(Collectors.toList());
        
        if (!ids.isEmpty()) {
            int updatedCount = outboxEventRepository.batchUpdateToProcessing(ids, LocalDateTime.now());
            log.debug("批量标记事件为处理中: count={}", updatedCount);
            return updatedCount;
        }
        
        return 0;
    }
    
    @Override
    @Transactional
    public boolean markAsPublished(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            event.markAsPublished();
            outboxEventRepository.updateById(event);
            log.debug("标记事件为已发布: eventId={}", eventId);
            return true;
        } else {
            log.warn("未找到事件: eventId={}", eventId);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean markAsFailed(String eventId, String errorMessage) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            event.markAsFailed(errorMessage);
            // EVT-002：重试耗尽自动流转死信，避免停留 FAILED + 超 max_retry_count 触发 CHECK 约束
            if (!event.canRetry()) {
                event.markAsDeadLetter();
                outboxEventRepository.updateById(event);
                log.warn("事件重试耗尽，流转死信: eventId={}, retryCount={}, maxRetryCount={}",
                        eventId, event.getRetryCount(), event.getMaxRetryCount());
                return true;
            }
            event.calculateNextRetryTime();
            outboxEventRepository.updateById(event);
            log.debug("标记事件为失败: eventId={}, retryCount={}, nextRetryAt={}",
                     eventId, event.getRetryCount(), event.getNextRetryAt());
            return true;
        } else {
            log.warn("未找到事件: eventId={}", eventId);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean markAsDeadLetter(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            int updated = outboxEventRepository.updateToDeadLetter(
                    eventId, LocalDateTime.now(), event.getErrorMessage());
            log.debug("标记事件为死信: eventId={}, updated={}", eventId, updated);
            return updated > 0;
        }
        log.warn("未找到事件: eventId={}", eventId);
        return false;
    }

    @Override
    @Transactional
    public List<String> claimEvents(int limit) {
        // EVT-002 步骤2：claim 明确方法名——悲观锁查询 + 同事务批量置 PROCESSING，原子抢占
        List<OutboxEvent> locked = outboxEventRepository.selectPendingEventsWithLock(LocalDateTime.now(), limit);
        if (locked.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> ids = locked.stream().map(OutboxEvent::getId).collect(Collectors.toList());
        outboxEventRepository.batchUpdateToProcessing(ids, LocalDateTime.now());
        return locked.stream().map(OutboxEvent::getEventId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<OutboxEvent> claimBatch(int limit, Duration leaseTimeout) {
        // EVT-003：Dispatcher 专用抢占——FOR UPDATE 查询 + 同事务批量置 PROCESSING 并写租约，
        // 锁跨越 SELECT→UPDATE 边界，COMMIT 后其他 dispatcher 即见 PROCESSING（修复 ADR-003 §1 P0-3）。
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> locked = outboxEventRepository.selectPendingEventsWithLock(now, limit);
        if (locked.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = locked.stream().map(OutboxEvent::getId).collect(Collectors.toList());
        LocalDateTime leaseExpiresAt = now.plus(leaseTimeout);
        outboxEventRepository.batchClaimWithLease(ids, now, leaseExpiresAt);
        // locked 内存对象已是 PENDING/FAILED 快照，回填抢占后的状态/租约字段供 Dispatcher 直接投递
        locked.forEach(e -> e.claim(now, leaseExpiresAt));
        log.debug("EVT-003 抢占一批事件: count={}, leaseExpiresAt={}", locked.size(), leaseExpiresAt);
        return locked;
    }

    @Override
    @Transactional
    public int reclaimExpiredLeases(LocalDateTime now, int limit) {
        // EVT-003：回收 dispatcher 崩溃后卡在 PROCESSING 的过期租约——回 PENDING 重新投递
        List<OutboxEvent> expired = outboxEventRepository.selectExpiredLeaseProcessing(now, limit);
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(OutboxEvent::getId).collect(Collectors.toList());
        int reset = outboxEventRepository.batchResetExpiredLeases(ids, now);
        log.info("EVT-003 回收过期租约: reclaimed={}, now={}", reset, now);
        return reset;
    }
    
    @Override
    @Transactional
    public boolean markAsCancelled(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            event.setStatus(OutboxEventStatus.CANCELLED);
            outboxEventRepository.updateById(event);
            log.debug("标记事件为已取消: eventId={}", eventId);
            return true;
        } else {
            log.warn("未找到事件: eventId={}", eventId);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean resetToPending(String eventId) {
        OutboxEvent event = outboxEventRepository.selectByEventId(eventId);
        if (event != null) {
            if (event.canRetry()) {
                event.setStatus(OutboxEventStatus.PENDING);
                event.setNextRetryAt(null);
                event.setErrorMessage(null);
                outboxEventRepository.updateById(event);
                log.debug("重置事件为待处理: eventId={}", eventId);
                return true;
            } else {
                log.warn("事件不能重置为待处理: eventId={}, status={}, retryCount={}", 
                        eventId, event.getStatus(), event.getRetryCount());
            }
        } else {
            log.warn("未找到事件: eventId={}", eventId);
        }
        return false;
    }
    
    @Override
    @Transactional(readOnly = true)
    public long getPendingEventCount() {
        return outboxEventRepository.countPendingEvents(LocalDateTime.now());
    }
    
    @Override
    @Transactional(readOnly = true)
    public long getEventCountByStatus(OutboxEventStatus status) {
        return outboxEventRepository.countByStatus(status.name());
    }
    
    @Override
    @Transactional
    public int cleanupHistoryData(LocalDateTime beforeTime) {
        int publishedDeleted = outboxEventRepository.deletePublishedEventsBefore(beforeTime);
        int cancelledDeleted = outboxEventRepository.deleteCancelledEventsBefore(beforeTime);
        int totalDeleted = publishedDeleted + cancelledDeleted;
        
        log.info("清理历史数据完成: 已发布事件={}, 已取消事件={}, 总计={}", 
                publishedDeleted, cancelledDeleted, totalDeleted);
        
        return totalDeleted;
    }
    
    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findFailedEventsExceedingMaxRetries(Page<OutboxEvent> page) {
        return outboxEventRepository.selectFailedEventsExceedingMaxRetries(page);
    }

    @Override
    @Transactional(readOnly = true)
    public IPage<OutboxEvent> findDeadLetterEvents(Page<OutboxEvent> page) {
        return outboxEventRepository.selectDeadLetterEvents(page);
    }
    
    @Override
    @Transactional(readOnly = true)
    public OutboxEventStatistics getStatistics() {
        long pendingCount = getEventCountByStatus(OutboxEventStatus.PENDING);
        long processingCount = getEventCountByStatus(OutboxEventStatus.PROCESSING);
        long publishedCount = getEventCountByStatus(OutboxEventStatus.PUBLISHED);
        long failedCount = getEventCountByStatus(OutboxEventStatus.FAILED);
        long cancelledCount = getEventCountByStatus(OutboxEventStatus.CANCELLED);
        long totalCount = pendingCount + processingCount + publishedCount + failedCount + cancelledCount;
        
        // 查询超过最大重试次数的失败事件数量
        long failedExceedingMaxRetriesCount = outboxEventRepository
                .selectFailedEventsExceedingMaxRetries(new Page<>(1, Integer.MAX_VALUE))
                .getTotal();
        
        return OutboxEventStatistics.builder()
                .pendingCount(pendingCount)
                .processingCount(processingCount)
                .publishedCount(publishedCount)
                .failedCount(failedCount)
                .cancelledCount(cancelledCount)
                .totalCount(totalCount)
                .failedExceedingMaxRetriesCount(failedExceedingMaxRetriesCount)
                .build();
    }
}