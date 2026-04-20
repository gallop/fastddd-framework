package com.gallop.fastddd.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gallop.fastddd.common.domain.DomainEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent 适配器
 *
 * 将通用的 DomainEvent 适配为 Spring 的 ApplicationEvent，用于 Spring 事件发布机制。
 * 这是 Spring 特定的事件基础设施，不直接暴露给业务层使用。
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
public class SpringDomainEventAdapter extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 被适配的领域事件
     */
    private final DomainEvent domainEvent;

    /**
     * 构造函数
     *
     * @param domainEvent 领域事件
     */
    public SpringDomainEventAdapter(DomainEvent domainEvent) {
        super(domainEvent);
        this.domainEvent = domainEvent;
    }

    /**
     * 获取被适配的领域事件
     *
     * @return 领域事件
     */
    @JsonIgnore
    public DomainEvent getDomainEvent() {
        return domainEvent;
    }

    /**
     * 忽略原始 source（避免序列化问题）
     */
    @JsonIgnore
    @Override
    public Object getSource() {
        return super.getSource();
    }

    @Override
    public String toString() {
        return "SpringDomainEventAdapter{" +
                "domainEvent=" + domainEvent +
                '}';
    }
}
