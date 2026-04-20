package com.gallop.fastddd.config.condition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the conditional system.
 * Tests the interaction between different conditional annotations and Spring context.
 * 
 * @author gallop
 * @since 2024-12-19
 */
class ConditionalSystemIntegrationTest {

    private AnnotationConfigApplicationContext context;
    private static final String TEST_PROPERTY = "test.integration.property";

    @BeforeEach
    void setUp() {
        // Clear any existing test properties
        System.clearProperty(TEST_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        // Clean up test properties
        System.clearProperty(TEST_PROPERTY);
    }

    @Test
    void testConditionalOnProfile_DevProfile() {
        // Given
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("dev");
        context.register(DevProfileTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("devService"));
        assertFalse(context.containsBean("prodService"));
    }

    @Test
    void testConditionalOnProfile_ProdProfile() {
        // Given
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("prod");
        context.register(ProdProfileTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("prodService"));
        assertFalse(context.containsBean("devService"));
    }

    @Test
    void testConditionalOnSystemProperty_PropertyExists() {
        // Given
        System.setProperty(TEST_PROPERTY, "test-value");
        context = new AnnotationConfigApplicationContext();
        context.register(SystemPropertyTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("systemPropertyService"));
    }

    @Test
    void testConditionalOnSystemProperty_PropertyDoesNotExist() {
        // Given - property is not set
        context = new AnnotationConfigApplicationContext();
        context.register(SystemPropertyTestConfiguration.class);
        context.refresh();

        // When & Then
        assertFalse(context.containsBean("systemPropertyService"));
    }

    @Test
    void testConditionalOnSystemProperty_WithSpecificValue() {
        // Given
        System.setProperty(TEST_PROPERTY, "expected-value");
        context = new AnnotationConfigApplicationContext();
        context.register(SystemPropertyWithValueTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("specificValueService"));
    }

    @Test
    void testConditionalOnSystemProperty_WithWrongValue() {
        // Given
        System.setProperty(TEST_PROPERTY, "wrong-value");
        context = new AnnotationConfigApplicationContext();
        context.register(SystemPropertyWithValueTestConfiguration.class);
        context.refresh();

        // When & Then
        assertFalse(context.containsBean("specificValueService"));
    }

    @Test
    void testConditionalOnClass_ExistingClass() {
        // Given
        context = new AnnotationConfigApplicationContext();
        context.register(ClassExistsTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("classExistsService"));
    }

    @Test
    void testConditionalOnMissingClass_NonExistingClass() {
        // Given
        context = new AnnotationConfigApplicationContext();
        context.register(ClassMissingTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("classMissingService"));
    }

    @Test
    void testMultipleConditions_AllMatch() {
        // Given
        System.setProperty(TEST_PROPERTY, "multi-test");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("dev");
        context.register(MultipleConditionsTestConfiguration.class);
        context.refresh();

        // When & Then
        assertTrue(context.containsBean("multiConditionService"));
    }

    @Test
    void testMultipleConditions_OneDoesNotMatch() {
        // Given - property set but wrong profile
        System.setProperty(TEST_PROPERTY, "multi-test");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("prod");
        context.register(MultipleConditionsTestConfiguration.class);
        context.refresh();

        // When & Then
        assertFalse(context.containsBean("multiConditionService"));
    }

    // Test configurations

    @Configuration
    static class DevProfileTestConfiguration {
        @Bean
        @ConditionalOnDev
        public String devService() {
            return "dev-service";
        }

        @Bean
        @ConditionalOnProd
        public String prodService() {
            return "prod-service";
        }
    }

    @Configuration
    static class ProdProfileTestConfiguration {
        @Bean
        @ConditionalOnDev
        public String devService() {
            return "dev-service";
        }

        @Bean
        @ConditionalOnProd
        public String prodService() {
            return "prod-service";
        }
    }

    @Configuration
    static class SystemPropertyTestConfiguration {
        @Bean
        @ConditionalOnSystemProperty(name = TEST_PROPERTY)
        public String systemPropertyService() {
            return "system-property-service";
        }
    }

    @Configuration
    static class SystemPropertyWithValueTestConfiguration {
        @Bean
        @ConditionalOnSystemProperty(name = TEST_PROPERTY, havingValue = "expected-value")
        public String specificValueService() {
            return "specific-value-service";
        }
    }

    @Configuration
    static class ClassExistsTestConfiguration {
        @Bean
        @ConditionalOnClass("java.lang.String")
        public String classExistsService() {
            return "class-exists-service";
        }
    }

    @Configuration
    static class ClassMissingTestConfiguration {
        @Bean
        @ConditionalOnMissingClass("com.nonexistent.NonExistentClass")
        public String classMissingService() {
            return "class-missing-service";
        }
    }

    @Configuration
    static class MultipleConditionsTestConfiguration {
        @Bean
        @ConditionalOnDev
        @ConditionalOnSystemProperty(name = TEST_PROPERTY, havingValue = "multi-test")
        @ConditionalOnClass("java.lang.String")
        public String multiConditionService() {
            return "multi-condition-service";
        }
    }
}