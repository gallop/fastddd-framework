package com.gallop.fastddd.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FastDddLegacyRootValidator} 单测：旧根 {@code fast-ddd.*} 启动期明确失败。
 * 对应验收 CFG-001②。
 */
class FastDddLegacyRootValidatorTest {

    private final FastDddLegacyRootValidator validator = new FastDddLegacyRootValidator();

    @Test
    void shouldFailFast_whenLegacyHyphenatedRootPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("fast-ddd.security.jwt.secret", "should-be-ignored");
        env.setProperty("fastddd.security.jwt.enabled", "true");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> validator.postProcessEnvironment(env, null));

        assertTrue(thrown.getMessage().contains("fast-ddd.security.jwt.secret"),
                "错误信息应列出旧根键");
        assertTrue(thrown.getMessage().contains("fastddd.*"),
                "错误信息应给出正确根指引");
    }

    @Test
    void shouldPass_whenOnlyCorrectRootPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("fastddd.security.jwt.secret", "valid");
        env.setProperty("fastddd.cache.enabled", "true");
        env.setProperty("rocketmq.publisher.defaultTopic", "domain-events");

        assertDoesNotThrow(() -> validator.postProcessEnvironment(env, null));
    }

    @Test
    void shouldNotMatchArtifactIdLikeNames() {
        // fast-ddd-common 等 artifactId 风格的字符串不是属性键，不应误判；
        // 仅 fast-ddd. (带点) 前缀触发。
        MockEnvironment env = new MockEnvironment();
        env.setProperty("fastddd.security.cors.enabled", "true");
        env.setProperty("some.other.fast-ddd-common.suffix", "x");

        assertDoesNotThrow(() -> validator.postProcessEnvironment(env, null));
    }
}
