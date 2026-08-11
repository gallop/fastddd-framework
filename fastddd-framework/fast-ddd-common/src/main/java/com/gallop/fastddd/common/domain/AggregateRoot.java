package com.gallop.fastddd.common.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类，提供 DDD 中聚合根的通用行为。
 *
 * <p>身份契约继承自 {@link DomainEntity}（经 {@link BaseEntity} 兼容门面）。聚合根的职责：
 * <ol>
 *   <li>维护聚合的一致性边界；</li>
 *   <li>管理领域事件（{@link #addDomainEvent} / {@link #getDomainEvents} / {@link #clearDomainEvents}）；</li>
 *   <li>提供聚合的唯一入口。</li>
 * </ol>
 *
 * <p>ID-002 移除了无调用方的 {@code removeDomainEvent}/{@code hasDomainEvents}/{@code getDomainEventCount}
 * 与持久化回调（{@code prePersist}/{@code preUpdate}）——后者本应是持久层职责。
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.1.0 - 使用统一的 common.domain.DomainEvent；ID-002 收敛身份契约
 */
public abstract class AggregateRoot<ID extends Serializable> extends BaseEntity<ID> {

    private static final long serialVersionUID = 1L;

    /**
     * 领域事件列表（使用统一的 common.domain.DomainEvent）。
     * <p>{@code transient}：事件不参与序列化，发布后即清除。
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 添加领域事件。
     *
     * @param event 领域事件（{@code null} 忽略）
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }

    /**
     * 获取所有领域事件（只读视图）。
     *
     * @return 领域事件列表
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清空领域事件。
     */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
