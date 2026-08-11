package com.gallop.fastddd.event.outbox;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Outbox事件服务接口
 * 
 * 提供Outbox事件的核心业务功能，包括事件存储、查询、状态管理等。
 * 
 * @author gallop
 * @since 2025-01-09
 */
public interface OutboxEventService {
    
    /**
     * 保存领域事件到Outbox表
     * 
     * @param domainEvent 领域事件
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     * @return 保存的Outbox事件
     */
    OutboxEvent saveEvent(DomainEvent domainEvent, String aggregateId, String aggregateType);
    
    /**
     * 批量保存领域事件到Outbox表
     * 
     * @param domainEvents 领域事件列表
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     * @return 保存的Outbox事件列表
     */
    List<OutboxEvent> saveEvents(List<DomainEvent> domainEvents, String aggregateId, String aggregateType);
    
    /**
     * 根据事件ID查找事件
     * 
     * @param eventId 事件ID
     * @return 事件实体
     */
    Optional<OutboxEvent> findByEventId(String eventId);
    
    /**
     * 查找待处理的事件
     * 
     * @param page 分页参数
     * @return 待处理事件列表
     */
    IPage<OutboxEvent> findPendingEvents(Page<OutboxEvent> page);
    
    /**
     * 查找指定状态的事件
     * 
     * @param status 事件状态
     * @param page 分页参数
     * @return 事件列表
     */
    IPage<OutboxEvent> findEventsByStatus(OutboxEventStatus status, Page<OutboxEvent> page);
    
    /**
     * 查找指定聚合根的事件
     * 
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     * @param page 分页参数
     * @return 事件列表
     */
    IPage<OutboxEvent> findEventsByAggregate(String aggregateId, String aggregateType, Page<OutboxEvent> page);
    
    /**
     * 查找指定事件类型的事件
     * 
     * @param eventType 事件类型
     * @param page 分页参数
     * @return 事件列表
     */
    IPage<OutboxEvent> findEventsByType(String eventType, Page<OutboxEvent> page);
    
    /**
     * 标记事件为处理中
     * 
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean markAsProcessing(String eventId);
    
    /**
     * 批量标记事件为处理中
     * 
     * @param eventIds 事件ID列表
     * @return 成功标记的事件数量
     */
    int batchMarkAsProcessing(List<String> eventIds);
    
    /**
     * 标记事件为已发布
     * 
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean markAsPublished(String eventId);
    
    /**
     * 标记事件为失败
     *
     * <p>EVT-002：当重试次数耗尽（retryCount &gt;= maxRetryCount）时，自动流转为死信
     * （{@link OutboxEventStatus#DEAD_LETTER}），不再停留 FAILED。
     *
     * @param eventId 事件ID
     * @param errorMessage 错误信息
     * @return 是否成功
     */
    boolean markAsFailed(String eventId, String errorMessage);

    /**
     * 显式标记事件为死信（EVT-002 步骤2：dead-letter 明确方法名）。
     *
     * <p>供手动干预或超限事件补流转使用；正常路径由 {@link #markAsFailed} 自动触发。
     *
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean markAsDeadLetter(String eventId);

    /**
     * 抢占待处理事件（EVT-002 步骤2：claim 明确方法名）。
     *
     * <p>悲观锁（SELECT ... FOR UPDATE）查询 PENDING / 可重试 FAILED 事件，
     * 并在同一事务内批量置为 PROCESSING——原子抢占，杜绝并发 worker 重复处理。
     *
     * @param limit 抢占上限
     * @return 被抢占（已置 PROCESSING）的事件ID列表
     */
    List<String> claimEvents(int limit);

    /**
     * 抢占一批待投递事件并设置租约（EVT-003：Dispatcher 专用）。
     *
     * <p>在单一 {@code @Transactional} 内：悲观锁查询（PENDING + 可重试 FAILED）→ 原子批量置
     * PROCESSING 并写 {@code claimed_at}/{@code lease_expires_at} → 返回完整事件实体供 transport 投递。
     * FOR UPDATE 锁覆盖 SELECT→UPDATE 边界，COMMIT 后其他 dispatcher 即见 PROCESSING，
     * 杜绝多实例重复投递（修复 ADR-003 §1 P0-3）。
     *
     * @param limit        抢占上限
     * @param leaseTimeout 租约时长（超过则 reclaim 回 PENDING）
     * @return 已抢占（PROCESSING）的事件实体列表，含 payload 供 transport.send
     */
    List<OutboxEvent> claimBatch(int limit, Duration leaseTimeout);

    /**
     * 回收租约过期的 PROCESSING 事件（EVT-003）：dispatcher 崩溃后留下的"卡住"事件。
     *
     * <p>单一 {@code @Transactional}：悲观锁查询过期 PROCESSING → 批量重置回 PENDING（清租约字段，
     * 保留 retryCount）。下一轮 {@link #claimBatch} 即可重新投递。
     *
     * @param now   当前时间
     * @param limit 回收上限
     * @return 重置回 PENDING 的事件数
     */
    int reclaimExpiredLeases(LocalDateTime now, int limit);
    
    /**
     * 标记事件为已取消
     * 
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean markAsCancelled(String eventId);
    
    /**
     * 重置事件状态为待处理（用于手动重试）
     * 
     * @param eventId 事件ID
     * @return 是否成功
     */
    boolean resetToPending(String eventId);
    
    /**
     * 获取待处理事件数量
     * 
     * @return 待处理事件数量
     */
    long getPendingEventCount();
    
    /**
     * 获取指定状态的事件数量
     * 
     * @param status 事件状态
     * @return 事件数量
     */
    long getEventCountByStatus(OutboxEventStatus status);
    
    /**
     * 清理历史数据
     * 
     * @param beforeTime 时间阈值
     * @return 清理的记录数
     */
    int cleanupHistoryData(LocalDateTime beforeTime);
    
    /**
     * 查找超过最大重试次数的失败事件
     *
     * @param page 分页参数
     * @return 失败事件列表
     */
    IPage<OutboxEvent> findFailedEventsExceedingMaxRetries(Page<OutboxEvent> page);

    /**
     * 查找死信事件（EVT-002：按 DEAD_LETTER 终态显式检索）。
     *
     * @param page 分页参数
     * @return 死信事件列表
     */
    IPage<OutboxEvent> findDeadLetterEvents(Page<OutboxEvent> page);
    
    /**
     * 获取事件统计信息
     * 
     * @return 事件统计信息
     */
    OutboxEventStatistics getStatistics();
}