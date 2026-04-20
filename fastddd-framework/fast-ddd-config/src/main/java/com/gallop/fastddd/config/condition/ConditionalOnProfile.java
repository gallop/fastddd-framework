package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that matches when any of the specified Spring profiles are active.
 * This annotation can be used on classes or methods to conditionally enable beans
 * based on the active Spring profiles.
 * 
 * <p>Example usage:
 * <pre>
 * &#64;Component
 * &#64;ConditionalOnProfile("dev")
 * public class DevOnlyService {
 *     // This bean will only be created when 'dev' profile is active
 * }
 * 
 * &#64;Configuration
 * &#64;ConditionalOnProfile({"test", "integration"})
 * public class TestConfiguration {
 *     // This configuration will be active when either 'test' or 'integration' profile is active
 * }
 * </pre>
 * 
 * @author gallop
 * @since 2024-12-19
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnProfileCondition.class)
public @interface ConditionalOnProfile {
    
    /**
     * The profiles that should be active for the condition to match.
     * If multiple profiles are specified, the condition matches if any of them are active.
     * 
     * @return the profiles to match against
     */
    String[] value();
}