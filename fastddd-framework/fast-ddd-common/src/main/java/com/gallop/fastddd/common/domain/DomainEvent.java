package com.gallop.fastddd.common.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 *
 * 所有领域特定事件都应该继承此抽象类。该类封装了通用的事件属性，
 * 并作为系统中所有事件的基础。
 *
 * 此类位于 common 模块，不依赖 Spring 框架，保持领域层的纯净性。
 * Spring 特定的事件发布功能由 fast-ddd-event 模块提供。
 *
 * @author FastDDD Framework Team
 * @version 1.1.0
 */
public abstract class DomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一标识符
     */
    private final UUID eventId;

    /**
     * 事件创建时间戳
     */
    private final Instant createdAt;

    /**
     * 聚合根ID
     */
    private final String aggregateId;

    /**
     * 聚合根类型
     */
    private final String aggregateType;

    /**
     * 构造函数
     *
     * @param aggregateId 聚合根ID
     * @param aggregateIdStr 聚合根ID字符串
     * @param aggregateType 聚合根类型
     */
    protected DomainEvent(Serializable aggregateId, String aggregateIdStr, String aggregateType) {
        this.eventId = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.aggregateId = aggregateIdStr != null ? aggregateIdStr : (aggregateId != null ? aggregateId.toString() : null);
        this.aggregateType = aggregateType;
    }

    /**
     * 构造函数（简化版）
     *
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     */
    protected DomainEvent(Serializable aggregateId, String aggregateType) {
        this(aggregateId, aggregateId != null ? aggregateId.toString() : null, aggregateType);
    }

    /**
     * 获取事件ID
     *
     * @return 事件唯一标识符
     */
    public UUID getEventId() {
        return eventId;
    }

    /**
     * 获取事件创建时间戳
     *
     * @return 创建时间戳
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取聚合根ID
     *
     * @return 聚合根ID
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * 获取聚合根类型
     *
     * @return 聚合根类型
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 获取事件类型（默认为类名）
     *
     * @return 事件类型
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return String.format("%s{eventId=%s, createdAt=%s, aggregateId=%s, aggregateType=%s}",
            getClass().getSimpleName(), eventId, createdAt, aggregateId, aggregateType);
    }
}
