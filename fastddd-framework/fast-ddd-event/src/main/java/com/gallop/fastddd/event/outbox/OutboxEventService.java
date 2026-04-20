package com.gallop.fastddd.event.outbox;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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
     * @param eventId 事件ID
     * @param errorMessage 错误信息
     * @return 是否成功
     */
    boolean markAsFailed(String eventId, String errorMessage);
    
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
     * 获取事件统计信息
     * 
     * @return 事件统计信息
     */
    OutboxEventStatistics getStatistics();
}