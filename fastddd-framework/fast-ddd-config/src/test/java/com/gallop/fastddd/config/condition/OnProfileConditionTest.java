package com.gallop.fastddd.config.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OnProfileCondition}.
 * 
 * @author gallop
 * @since 2024-12-19
 */
class OnProfileConditionTest {

    @Mock
    private ConditionContext conditionContext;

    @Mock
    private AnnotatedTypeMetadata annotatedTypeMetadata;

    @Mock
    private Environment environment;

    private OnProfileCondition condition;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        condition = new OnProfileCondition();
        when(conditionContext.getEnvironment()).thenReturn(environment);
    }

    @Test
    void testMatches_WhenNoAnnotationAttributes_ShouldReturnTrue() {
        // Given
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(null);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenEmptyProfiles_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenProfileIsActive_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{"dev"});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test"});

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenProfileIsNotActive_ShouldReturnFalse() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{"prod"});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test"});

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenMultipleProfilesAndOneIsActive_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{"prod", "dev", "staging"});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenMultipleProfilesAndNoneIsActive_ShouldReturnFalse() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{"prod", "staging"});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test"});

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenEmptyStringProfile_ShouldIgnoreEmptyProfile() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{"", "dev"});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnProfile.class.getName()))
                .thenReturn(attributes);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }
}