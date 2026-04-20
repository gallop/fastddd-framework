package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that matches when a specified system property exists
 * and optionally has a specific value.
 * 
 * <p>Example usage:
 * <pre>
 * // Bean will be created only if 'debug' system property exists
 * &#64;Component
 * &#64;ConditionalOnSystemProperty(name = "debug")
 * public class DebugService {
 * }
 * 
 * // Bean will be created only if 'env' system property equals 'production'
 * &#64;Component
 * &#64;ConditionalOnSystemProperty(name = "env", havingValue = "production")
 * public class ProductionService {
 * }
 * </pre>
 * 
 * @author gallop
 * @since 2024-12-19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnSystemPropertyCondition.class)
public @interface ConditionalOnSystemProperty {
    
    /**
     * The name of the system property to check.
     * 
     * @return the system property name
     */
    String name();
    
    /**
     * The expected value of the system property.
     * If not specified, the condition will match if the property exists regardless of its value.
     * 
     * @return the expected property value
     */
    String havingValue() default "";
}