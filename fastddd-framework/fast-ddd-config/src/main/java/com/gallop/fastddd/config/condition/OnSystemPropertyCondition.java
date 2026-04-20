package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Condition implementation that matches based on system properties.
 * This condition evaluates to true if the specified system property exists
 * and optionally matches a specific value.
 * 
 * @author gallop
 * @since 2024-12-19
 */
public class OnSystemPropertyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(
                ConditionalOnSystemProperty.class.getName());
        
        if (attributes == null) {
            return true;
        }
        
        String name = (String) attributes.getFirst("name");
        if (!StringUtils.hasText(name)) {
            return true;
        }
        
        String systemPropertyValue = System.getProperty(name);
        
        // If property doesn't exist, condition fails
        if (systemPropertyValue == null) {
            return false;
        }
        
        String havingValue = (String) attributes.getFirst("havingValue");
        
        // If no specific value is required, just check existence
        if (!StringUtils.hasText(havingValue)) {
            return true;
        }
        
        // Check if the property value matches the expected value
        return havingValue.equals(systemPropertyValue);
    }
}