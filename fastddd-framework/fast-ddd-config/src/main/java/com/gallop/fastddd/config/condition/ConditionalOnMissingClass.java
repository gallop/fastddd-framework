package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that matches when all specified classes are NOT present on the classpath.
 * This annotation is useful for conditionally enabling fallback beans when certain libraries are not available.
 * 
 * <p>Example usage:
 * <pre>
 * // Bean will be created only if Redis client is NOT available
 * &#64;Component
 * &#64;ConditionalOnMissingClass("redis.clients.jedis.Jedis")
 * public class InMemoryCacheService {
 * }
 * 
 * // Bean will be created only if both classes are NOT available
 * &#64;Component
 * &#64;ConditionalOnMissingClass({"org.springframework.data.redis.core.RedisTemplate", "redis.clients.jedis.Jedis"})
 * public class DefaultCacheConfiguration {
 * }
 * </pre>
 * 
 * @author gallop
 * @since 2024-12-19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnMissingClassCondition.class)
public @interface ConditionalOnMissingClass {
    
    /**
     * The fully qualified class names that must NOT be present on the classpath.
     * All specified classes must be absent for the condition to match.
     * 
     * @return the class names to check for absence
     */
    String[] value();
}