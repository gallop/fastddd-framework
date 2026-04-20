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
 * Unit tests for {@link OnMissingClassCondition}.
 * 
 * @author gallop
 * @since 2024-12-19
 */
class OnMissingClassConditionTest {

    @Mock
    private ConditionContext conditionContext;

    @Mock
    private AnnotatedTypeMetadata annotatedTypeMetadata;

    private OnMissingClassCondition condition;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        condition = new OnMissingClassCondition();
    }

    @Test
    void testMatches_WhenNoAnnotationAttributes_ShouldReturnTrue() {
        // Given
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
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
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
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
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenExistingClassSpecified_ShouldReturnFalse() {
        // Given - using a class that definitely exists in the JDK
        String[] classNames = {"java.lang.String"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenNonExistingClassSpecified_ShouldReturnTrue() {
        // Given - using a class that definitely doesn't exist
        String[] classNames = {"com.nonexistent.NonExistentClass"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenMultipleNonExistingClassesSpecified_ShouldReturnTrue() {
        // Given - using classes that definitely don't exist
        String[] classNames = {"com.nonexistent.Class1", "com.nonexistent.Class2", "com.nonexistent.Class3"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
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
        String[] classNames = {"com.nonexistent.NonExistentClass", "java.lang.String"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(getClass().getClassLoader());

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenEmptyStringInClassNames_ShouldIgnoreAndReturnTrue() {
        // Given - array with empty string and non-existing class
        String[] classNames = {"", "com.nonexistent.NonExistentClass"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
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
        String[] classNames = {"com.nonexistent.NonExistentClass"};
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("value", classNames);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnMissingClass.class.getName()))
                .thenReturn(attributes);
        when(conditionContext.getClassLoader()).thenReturn(null);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }
}