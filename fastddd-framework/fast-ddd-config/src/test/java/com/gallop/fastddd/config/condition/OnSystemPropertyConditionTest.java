package com.gallop.fastddd.config.condition;

import org.junit.jupiter.api.AfterEach;
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
 * Unit tests for {@link OnSystemPropertyCondition}.
 * 
 * @author gallop
 * @since 2024-12-19
 */
class OnSystemPropertyConditionTest {

    @Mock
    private ConditionContext conditionContext;

    @Mock
    private AnnotatedTypeMetadata annotatedTypeMetadata;

    private OnSystemPropertyCondition condition;
    
    private static final String TEST_PROPERTY = "test.property";
    private static final String TEST_VALUE = "test.value";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        condition = new OnSystemPropertyCondition();
        // Clear any existing test property
        System.clearProperty(TEST_PROPERTY);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up test properties
        System.clearProperty(TEST_PROPERTY);
    }

    @Test
    void testMatches_WhenNoAnnotationAttributes_ShouldReturnTrue() {
        // Given
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(null);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenEmptyPropertyName_ShouldReturnTrue() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", "");
        attributes.add("havingValue", "");
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenPropertyExists_ShouldReturnTrue() {
        // Given
        System.setProperty(TEST_PROPERTY, TEST_VALUE);
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", TEST_PROPERTY);
        attributes.add("havingValue", "");
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenPropertyDoesNotExist_ShouldReturnFalse() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", TEST_PROPERTY);
        attributes.add("havingValue", "");
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenPropertyExistsWithCorrectValue_ShouldReturnTrue() {
        // Given
        System.setProperty(TEST_PROPERTY, TEST_VALUE);
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", TEST_PROPERTY);
        attributes.add("havingValue", TEST_VALUE);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertTrue(result);
    }

    @Test
    void testMatches_WhenPropertyExistsWithIncorrectValue_ShouldReturnFalse() {
        // Given
        System.setProperty(TEST_PROPERTY, "different.value");
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", TEST_PROPERTY);
        attributes.add("havingValue", TEST_VALUE);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }

    @Test
    void testMatches_WhenPropertyDoesNotExistButValueRequired_ShouldReturnFalse() {
        // Given
        MultiValueMap<String, Object> attributes = new LinkedMultiValueMap<>();
        attributes.add("name", TEST_PROPERTY);
        attributes.add("havingValue", TEST_VALUE);
        when(annotatedTypeMetadata.getAllAnnotationAttributes(ConditionalOnSystemProperty.class.getName()))
                .thenReturn(attributes);

        // When
        boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

        // Then
        assertFalse(result);
    }
}