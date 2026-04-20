package com.gallop.fastddd.event.rocketmq;

import com.gallop.fastddd.common.domain.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通用领域事件实现
 * 用于封装从 RocketMQ 接收到的事件消息
 *
 * @author gallop
 * @since 2025-01-22
 * @version 1.1.0 - 继承统一的 common.domain.DomainEvent
 */
public class GenericDomainEvent extends DomainEvent {

    /**
     * 原始事件ID（来自 RocketMQ 消息）
     * 父类的 eventId 是自动生成的 UUID，用于 Spring 事件系统
     * 此字段保留原始的事件ID用于追溯和日志
     */
    private String originalEventId;

    private String eventType;
    private String eventData;
    private LocalDateTime occurredAt;
    private String version;

    /**
     * 无参构造函数（用于反序列化）
     */
    public GenericDomainEvent() {
        super((String) null, null);
    }

    /**
     * 完整构造函数
     */
    public GenericDomainEvent(String eventId, String eventType, String aggregateId,
                             String aggregateType, String eventData, LocalDateTime occurredAt) {
        super(aggregateId, aggregateId, aggregateType);
        this.originalEventId = eventId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.occurredAt = occurredAt;
        this.version = "1.0";
    }

    /**
     * 获取原始事件ID（来自 RocketMQ 消息）
     *
     * @return 原始事件ID字符串
     */
    public String getOriginalEventId() {
        return originalEventId;
    }

    /**
     * 设置原始事件ID
     *
     * @param originalEventId 原始事件ID
     */
    public void setOriginalEventId(String originalEventId) {
        this.originalEventId = originalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    /**
     * 获取事件发生时间（兼容旧接口）
     *
     * @return 事件发生时间
     */
    public LocalDateTime getOccurredOn() {
        return occurredAt;
    }

    /**
     * 获取事件创建时间戳（从 Instant 转换为 LocalDateTime）
     *
     * @return 事件创建时间
     */
    public LocalDateTime getOccurredAt() {
        return occurredAt != null ? occurredAt :
               (getCreatedAt() != null ? LocalDateTime.ofInstant(getCreatedAt(), java.time.ZoneId.systemDefault()) : null);
    }

    public Long getVersion() {
        return version != null ? Long.parseLong(version) : 1L;
    }

    public String getEventData() {
        return eventData != null ? eventData : "{}";
    }

    // Setters
    public void setAggregateType(String aggregateType) {
        // 注意：aggregateId 在父类中是 final，无法修改
        // 此方法保留但不做任何操作
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public String getVersionString() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 设置事件ID（实际上设置 originalEventId）
     *
     * @param eventId 事件ID
     */
    public void setEventId(String eventId) {
        this.originalEventId = eventId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * 设置聚合根ID（注意：此方法不会实际修改聚合根ID）
     *
     * @param aggregateId 聚合根ID
     * @deprecated 父类的 aggregateId 是 final，无法修改
     */
    @Deprecated
    public void setAggregateId(String aggregateId) {
        // 父类的 aggregateId 是 final，无法修改
        // 此方法保留用于兼容但不做任何操作
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "GenericDomainEvent{" +
                "originalEventId='" + originalEventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + getAggregateId() + '\'' +
                ", aggregateType='" + getAggregateType() + '\'' +
                ", occurredAt=" + occurredAt +
                ", version='" + version + '\'' +
                '}';
    }
}
