package com.gallop.fastddd.event.outbox.transport;

import com.gallop.fastddd.event.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 本地 Outbox 传输（EVT-003，{@code fastddd.event.outbox.transport=local}，默认）。
 *
 * <p>将已落库的 {@link OutboxEvent} 经 Spring {@link ApplicationEventPublisher} 重新发布到当前 JVM，
 * 提供"事务性本地投递"语义：事件与业务事务原子落库（Outbox），由
 * {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher} 周期重放——满足 ADR-003
 * "不再只入库不投递"。
 *
 * <p><b>投递载荷 = {@link OutboxEvent} 信封</b>（非反序列化的领域事件子类）。框架
 * {@code DomainEvent} 为不可变（final 字段 + 全参构造、无 Jackson 默认构造/创建器），
 * 通用反序列化不可行；故默认传输投递信封对象，业务监听器以
 * {@code @EventListener void onOutbox(OutboxEvent e)} 消费，需要类型化时自行按
 * {@link OutboxEvent#getEventType()} 反序列化 {@link OutboxEvent#getEventData()}。
 * 类型化重放（经 Jackson 注解的领域事件）登记为 1.1+ 增强项（ADR-003 §5）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class LocalOutboxTransport implements OutboxTransport {

    private static final Logger log = LoggerFactory.getLogger(LocalOutboxTransport.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalOutboxTransport(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void send(OutboxEvent event) {
        applicationEventPublisher.publishEvent(event);
        log.debug("[LocalOutboxTransport] 本地重放 outbox 事件信封: eventId={}, type={}",
                event.getEventId(), event.getEventType());
    }
}
