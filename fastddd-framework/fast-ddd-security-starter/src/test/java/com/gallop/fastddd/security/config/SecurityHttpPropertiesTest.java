package com.gallop.fastddd.security.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHttpPropertiesTest {

    @Test
    void defaultPermitAllIncludesVerificationCodeSend() {
        SecurityHttpProperties properties = new SecurityHttpProperties();

        assertTrue(properties.getPermitAllPaths().contains("/api/auth/code/send"));
    }
}
