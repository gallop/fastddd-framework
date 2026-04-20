package com.gallop.fastddd.event.bridge;

import com.gallop.fastddd.common.domain.DomainEvent;
import com.gallop.fastddd.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Collection;

/**
 * 领域事件桥接器默认实现
 *
 * 此类已过时。自 1.1.0 版本起，FastDDD 统一使用 {@link DomainEvent}（位于 fast-ddd-common 模块）。
 * 此实现现在直接委托给 {@link EventPublisher}，不再需要任何桥接逻辑。
 *
 * <p><b>迁移建议：</b></p>
 * 请移除对 {@code DomainEventBridge} 的依赖，直接注入并使用 {@code EventPublisher}。
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 * @deprecated 请直接使用 {@link com.gallop.fastddd.event.EventPublisher}
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Component
public class DefaultDomainEventBridge implements DomainEventBridge {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDomainEventBridge.class);

    private final EventPublisher eventPublisher;

    /**
     * 构造函数
     *
     * @param eventPublisher 事件发布器
     */
    public DefaultDomainEventBridge(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        logger.warn("DomainEventBridge is deprecated since 1.1.0. Please use EventPublisher directly.");
    }

    @Override
    @Deprecated
    public void publishDomainEvent(DomainEvent event) {
        logger.debug("DomainEventBridge.publishDomainEvent() is deprecated. Delegating to EventPublisher.");
        eventPublisher.publishEvent(event);
    }

    @Override
    @Deprecated
    public void publishDomainEvents(@NonNull Collection<? extends DomainEvent> events) {
        logger.debug("DomainEventBridge.publishDomainEvents() is deprecated. Delegating to EventPublisher.");
        eventPublisher.publishEvents(events);
    }

    @Override
    @Deprecated
    public void publishDomainEventAsync(DomainEvent event) {
        logger.debug("DomainEventBridge.publishDomainEventAsync() is deprecated. Delegating to EventPublisher.");
        eventPublisher.publishEventAsync(event);
    }

    @Override
    @Deprecated
    public void publishDomainEventsAsync(@NonNull Collection<? extends DomainEvent> events) {
        logger.debug("DomainEventBridge.publishDomainEventsAsync() is deprecated. Delegating to EventPublisher.");
        eventPublisher.publishEventsAsync(events);
    }
}
