package com.gallop.fastddd.event;

import com.gallop.fastddd.common.domain.DomainEvent;
import org.springframework.lang.NonNull;

import java.util.Collection;

/**
 * 事件发布器接口
 *
 * 提供领域事件发布的核心功能，支持同步和异步事件发布。
 * 该接口是FastDDD框架事件驱动架构的核心组件。
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *
 *     @Autowired
 *     private EventPublisher eventPublisher;
 *
 *     public void createUser(User user) {
 *         // 业务逻辑
 *         userRepository.save(user);
 *
 *         // 发布领域事件
 *         UserCreatedEvent event = new UserCreatedEvent(user.getId(), user.getName());
 *         eventPublisher.publishEvent(event);
 *     }
 * }
 * }
 * </pre>
 *
 * @author FastDDD Framework Team
 * @version 1.1.0 - 使用统一的 common.domain.DomainEvent
 * @see DomainEvent
 * @see DomainEventListener
 */
public interface EventPublisher {
    
    /**
     * 发布单个领域事件（同步）
     * 
     * 事件将被同步发布到所有注册的监听器。
     * 如果任何监听器抛出异常，将会中断后续监听器的执行。
     * 
     * @param event 要发布的领域事件，不能为null
     * @throws IllegalArgumentException 如果event为null
     * @throws EventPublishException 如果事件发布过程中发生错误
     */
    void publishEvent(@NonNull DomainEvent event);
    
    /**
     * 批量发布领域事件（同步）
     * 
     * 按照集合中的顺序依次发布每个事件。
     * 如果某个事件发布失败，将中断后续事件的发布。
     * 
     * @param events 要发布的领域事件集合，不能为null或包含null元素
     * @throws IllegalArgumentException 如果events为null或包含null元素
     * @throws EventPublishException 如果任何事件发布过程中发生错误
     */
    void publishEvents(@NonNull Collection<? extends DomainEvent> events);
    
    /**
     * 异步发布单个领域事件
     * 
     * 事件将被异步发布到所有注册的监听器。
     * 该方法会立即返回，不等待监听器执行完成。
     * 
     * @param event 要发布的领域事件，不能为null
     * @throws IllegalArgumentException 如果event为null
     */
    void publishEventAsync(@NonNull DomainEvent event);
    
    /**
     * 异步批量发布领域事件
     * 
     * 所有事件将被异步发布到各自的监听器。
     * 该方法会立即返回，不等待监听器执行完成。
     * 
     * @param events 要发布的领域事件集合，不能为null或包含null元素
     * @throws IllegalArgumentException 如果events为null或包含null元素
     */
    void publishEventsAsync(@NonNull Collection<? extends DomainEvent> events);
    
    /**
     * 检查是否有监听器监听指定类型的事件
     * 
     * @param eventType 事件类型，不能为null
     * @return 如果有监听器监听该类型事件返回true，否则返回false
     * @throws IllegalArgumentException 如果eventType为null
     */
    boolean hasListeners(@NonNull Class<? extends DomainEvent> eventType);
    
    /**
     * 获取指定事件类型的监听器数量
     * 
     * @param eventType 事件类型，不能为null
     * @return 监听器数量
     * @throws IllegalArgumentException 如果eventType为null
     */
    int getListenerCount(@NonNull Class<? extends DomainEvent> eventType);
}