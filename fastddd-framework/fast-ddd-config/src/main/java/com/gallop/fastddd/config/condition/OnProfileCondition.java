package com.gallop.fastddd.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * Condition implementation that matches based on active Spring profiles.
 * This condition evaluates to true if any of the specified profiles are active.
 * 
 * @author gallop
 * @since 2024-12-19
 */
public class OnProfileCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(
                ConditionalOnProfile.class.getName());
        
        if (attributes == null) {
            return true;
        }
        
        String[] profiles = (String[]) attributes.getFirst("value");
        if (profiles == null || profiles.length == 0) {
            return true;
        }
        
        Set<String> activeProfiles = Set.of(context.getEnvironment().getActiveProfiles());
        
        return Arrays.stream(profiles)
                .filter(StringUtils::hasText)
                .anyMatch(activeProfiles::contains);
    }
}