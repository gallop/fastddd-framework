package com.gallop.fastddd.event.bridge;

import com.gallop.fastddd.common.domain.DomainEvent;

import java.util.Collection;

/**
 * 领域事件桥接器接口
 *
 * 此接口已过时。自 1.1.0 版本起，FastDDD 统一使用 {@link DomainEvent}（位于 fast-ddd-common 模块）。
 * 请直接使用 {@link com.gallop.fastddd.event.EventPublisher} 发布领域事件。
 *
 * <p><b>迁移指南：</b></p>
 * <pre>
 * {@code
 * // 旧代码（使用 DomainEventBridge）
 * @Autowired
 * private DomainEventBridge eventBridge;
 *
 * eventBridge.publishDomainEvent(event);
 *
 * // 新代码（直接使用 EventPublisher）
 * @Autowired
 * private EventPublisher eventPublisher;
 *
 * eventPublisher.publishEvent(event);
 * }
 * </pre>
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 * @deprecated 请直接使用 {@link com.gallop.fastddd.event.EventPublisher} 和 {@link DomainEvent}
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public interface DomainEventBridge {

    /**
     * 发布单个领域事件
     *
     * @param event 要发布的领域事件
     * @throws IllegalArgumentException 如果 event 为 null
     * @deprecated 请使用 {@link com.gallop.fastddd.event.EventPublisher#publishEvent(DomainEvent)}
     */
    @Deprecated
    void publishDomainEvent(DomainEvent event);

    /**
     * 批量发布领域事件
     *
     * @param events 要发布的领域事件集合
     * @throws IllegalArgumentException 如果 events 为 null 或包含 null 元素
     * @deprecated 请使用 {@link com.gallop.fastddd.event.EventPublisher#publishEvents(Collection)}
     */
    @Deprecated
    void publishDomainEvents(Collection<? extends DomainEvent> events);

    /**
     * 异步发布单个领域事件
     *
     * @param event 要发布的领域事件
     * @throws IllegalArgumentException 如果 event 为 null
     * @deprecated 请使用 {@link com.gallop.fastddd.event.EventPublisher#publishEventAsync(DomainEvent)}
     */
    @Deprecated
    void publishDomainEventAsync(DomainEvent event);

    /**
     * 异步批量发布领域事件
     *
     * @param events 要发布的领域事件集合
     * @throws IllegalArgumentException 如果 events 为 null 或包含 null 元素
     * @deprecated 请使用 {@link com.gallop.fastddd.event.EventPublisher#publishEventsAsync(Collection)}
     */
    @Deprecated
    void publishDomainEventsAsync(Collection<? extends DomainEvent> events);
}
