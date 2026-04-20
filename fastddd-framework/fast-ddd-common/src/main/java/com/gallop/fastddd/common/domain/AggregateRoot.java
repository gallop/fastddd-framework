package com.gallop.fastddd.common.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类，提供DDD中聚合根的通用行为
 * 聚合根的职责：
 * 1. 维护聚合的一致性边界
 * 2. 管理领域事件
 * 3. 提供聚合的唯一入口
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.1.0 - 使用统一的 common.domain.DomainEvent
 */
public abstract class AggregateRoot<ID extends Serializable> extends BaseEntity<ID> {

    private static final long serialVersionUID = 1L;

    /**
     * 领域事件列表
     * 使用统一的 common.domain.DomainEvent
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 添加领域事件
     *
     * @param event 领域事件
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }

    /**
     * 移除领域事件
     *
     * @param event 领域事件
     */
    protected void removeDomainEvent(DomainEvent event) {
        this.domainEvents.remove(event);
    }

    /**
     * 获取所有领域事件（只读）
     *
     * @return 领域事件列表
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清空领域事件
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /**
     * 检查是否有领域事件
     *
     * @return 如果有领域事件返回true，否则返回false
     */
    public boolean hasDomainEvents() {
        return !this.domainEvents.isEmpty();
    }

    /**
     * 获取领域事件数量
     *
     * @return 领域事件数量
     */
    public int getDomainEventCount() {
        return this.domainEvents.size();
    }

    /**
     * 聚合根创建前的回调
     * 子类可以重写此方法来添加特定的创建逻辑
     */
    @Override
    public void prePersist() {
        super.prePersist();
        // 可以在这里添加聚合根特定的创建逻辑
    }

    /**
     * 聚合根更新前的回调
     * 子类可以重写此方法来添加特定的更新逻辑
     */
    @Override
    public void preUpdate() {
        super.preUpdate();
        // 可以在这里添加聚合根特定的更新逻辑
    }
}
