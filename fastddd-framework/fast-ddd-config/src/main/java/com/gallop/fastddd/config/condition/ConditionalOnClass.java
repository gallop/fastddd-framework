package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that matches when all specified classes are present on the classpath.
 * This annotation is useful for conditionally enabling beans based on the availability of certain libraries.
 * 
 * <p>Example usage:
 * <pre>
 * // Bean will be created only if Redis client is available
 * &#64;Component
 * &#64;ConditionalOnClass("redis.clients.jedis.Jedis")
 * public class RedisService {
 * }
 * 
 * // Bean will be created only if both classes are available
 * &#64;Component
 * &#64;ConditionalOnClass({"org.springframework.data.redis.core.RedisTemplate", "redis.clients.jedis.Jedis"})
 * public class RedisConfiguration {
 * }
 * </pre>
 * 
 * @author gallop
 * @since 2024-12-19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {
    
    /**
     * The fully qualified class names that must be present on the classpath.
     * All specified classes must be present for the condition to match.
     * 
     * @return the class names to check
     */
    String[] value();
}