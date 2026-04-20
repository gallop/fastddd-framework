package com.gallop.fastddd.config.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation that matches when the 'dev' Spring profile is active.
 * This is equivalent to using {@code @ConditionalOnProfile("dev")}.
 * 
 * <p>Example usage:
 * <pre>
 * &#64;Component
 * &#64;ConditionalOnDev
 * public class DevOnlyService {
 *     // This bean will only be created when 'dev' profile is active
 * }
 * </pre>
 * 
 * @author gallop
 * @since 2024-12-19
 * @see ConditionalOnProfile
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProfile("dev")
public @interface ConditionalOnDev {
}