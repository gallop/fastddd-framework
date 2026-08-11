package com.gallop.fastddd.security.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHttpPropertiesTest {

    @Test
    void defaultPermitAllIncludesVerificationCodeSend() {
        SecurityHttpProperties properties = new SecurityHttpProperties();

        assertTrue(properties.getPermitAllPaths().contains("/api/auth/code/send"));
    }

    @Test
    void defaultPermitAllExposesOnlySafeActuatorEndpoints() {
        SecurityHttpProperties properties = new SecurityHttpProperties();

        assertTrue(properties.getPermitAllPaths().contains("/actuator/health"));
        assertTrue(properties.getPermitAllPaths().contains("/actuator/info"));
        assertFalse(properties.getPermitAllPaths().contains("/actuator/**"));
    }
}
