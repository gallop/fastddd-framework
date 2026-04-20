package com.gallop.fastddd.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 领域事件监听器注解
 * 
 * 用于标记处理领域事件的方法。这个注解提供了清晰的、领域特定的标记，
 * 提高了代码的可读性和意图表达。
 * 
 * 该注解是一个元注解，组合了 {@link EventListener} 和 {@link Component}，
 * 确保 Spring 的事件机制能够自动发现任何使用 {@code @DomainEventListener} 
 * 注解的方法作为监听器。
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Component
 * public class UserEventHandler {
 *     
 *     @DomainEventListener
 *     public void handleUserCreated(UserCreatedEvent event) {
 *         // 处理用户创建事件
 *     }
 * }
 * }
 * </pre>
 * 
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 * @see EventListener
 * @see Component
 * @see DomainEvent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EventListener
public @interface DomainEventListener {
    
    /**
     * 事件监听器的条件表达式（SpEL）
     * 
     * 可以用来指定更精确的事件过滤条件。
     * 例如："#event.eventType == 'USER_CREATED'"
     * 
     * @return SpEL 条件表达式
     */
    String condition() default "";
    
    /**
     * 事件监听器的 ID
     * 
     * 用于标识特定的监听器，在需要程序化管理监听器时使用。
     * 
     * @return 监听器 ID
     */
    String id() default "";
}