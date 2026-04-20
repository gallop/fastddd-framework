package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Condition implementation that matches based on the presence or absence of classes on the classpath.
 * This condition evaluates to true if all specified classes are present on the classpath.
 * 
 * @author gallop
 * @since 2024-12-19
 */
public class OnClassCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(
                ConditionalOnClass.class.getName());
        
        if (attributes == null) {
            return true;
        }
        
        String[] classNames = (String[]) attributes.getFirst("value");
        if (classNames == null || classNames.length == 0) {
            return true;
        }
        
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassUtils.getDefaultClassLoader();
        }
        
        // Check if all specified classes are present
        for (String className : classNames) {
            if (!StringUtils.hasText(className)) {
                continue;
            }
            
            if (!ClassUtils.isPresent(className, classLoader)) {
                return false;
            }
        }
        
        return true;
    }
}