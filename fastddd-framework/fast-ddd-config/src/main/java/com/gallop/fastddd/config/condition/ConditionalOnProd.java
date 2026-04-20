package com.gallop.fastddd.config.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation that matches when the 'prod' Spring profile is active.
 * This is equivalent to using {@code @ConditionalOnProfile("prod")}.
 * 
 * <p>Example usage:
 * <pre>
 * &#64;Component
 * &#64;ConditionalOnProd
 * public class ProdOnlyService {
 *     // This bean will only be created when 'prod' profile is active
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
@ConditionalOnProfile("prod")
public @interface ConditionalOnProd {
}