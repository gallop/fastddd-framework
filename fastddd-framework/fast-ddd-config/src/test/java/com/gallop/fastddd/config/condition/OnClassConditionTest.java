package com.gallop.fastddd.config.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OnClassCondition}.
 * 
 * @author gallop
 * @since 2024-12-19
 */
class OnClassConditionTest {

    @Mock
    private ConditionContext conditionContext;

    @Mock
    private AnnotatedTypeMetadata annotatedTypeMetadata;

    private OnClassCondition condition;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        condition = new OnClassCondition();
    }

    @Test
    void testMatches_WhenNoAnnotationAttributes_ShouldReturnTrue() {
        // Given
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(null);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenEmptyClassNames_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", new String[]{});
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenNullClassNames_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", null);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenExistingClassSpecified_ShouldReturnTrue() {
        // Given - using a class that definitely exists in the JDK
        String[] classNames = {"java.lang.String"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenNonExistingClassSpecified_ShouldReturnFalse() {
        // Given - using a class that definitely doesn't exist
        String[] classNames = {"com.nonexistent.NonExistentClass"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenMultipleExistingClassesSpecified_ShouldReturnTrue() {
        // Given - using classes that definitely exist in the JDK
        String[] classNames = {"java.lang.String", "java.util.List", "java.lang.Object"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenMixedExistingAndNonExistingClasses_ShouldReturnFalse() {
        // Given - mix of existing and non-existing classes
        String[] classNames = {"java.lang.String", "com.nonexistent.NonExistentClass"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenEmptyStringInClassNames_ShouldIgnoreAndReturnTrue() {
        // Given - array with empty string and existing class
        String[] classNames = {"", "java.lang.String"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenNullClassLoader_ShouldUseDefaultClassLoader() {
        // Given
        String[] classNames = {"java.lang.String"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(null);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }
}