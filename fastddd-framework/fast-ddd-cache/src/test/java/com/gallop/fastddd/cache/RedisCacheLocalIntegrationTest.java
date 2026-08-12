package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.redis.RedisCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 本地 Redis 真实读写验证（QA-002）。
 *
 * <p>仅在 {@code -Dfastddd.test.local-redis=true} 时运行。默认连接
 * {@code 127.0.0.1:6379}，可用 {@code fastddd.test.redis.host}、
 * {@code fastddd.test.redis.port}、{@code fastddd.test.redis.password} 覆盖；测试只使用并在结束时清理
 * {@code fastddd:qa-local::} 命名空间。</p>
 */
@SpringBootTest(classes = RedisCacheLocalIntegrationTest.TestApp.class, properties = {
        "fastddd.cache.redis.enabled=true",
        "fastddd.cache.redis.key-prefix=fastddd:qa-local:"
})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.local-redis", matches = "true",
        disabledReason = "需本地 Redis（-Dfastddd.test.local-redis=true 显式开启）")
class RedisCacheLocalIntegrationTest {

    @Autowired
    RedisCache redisCache;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> System.getProperty("fastddd.test.redis.host", "127.0.0.1"));
        registry.add("spring.data.redis.port", () -> Integer.getInteger("fastddd.test.redis.port", 6379));
        String password = System.getProperty("fastddd.test.redis.password");
        if (password != null && !password.isBlank()) {
            registry.add("spring.data.redis.password", () -> password);
        }
    }

    @Test
    void putGetAndEvict_useLocalRedis() {
        String cacheName = "qa";
        String key = "local-read-write";
        redisCache.evict(cacheName, key);
        try {
            redisCache.put(cacheName, key, "verified", Duration.ofMinutes(1));
            assertEquals("verified", redisCache.get(cacheName, key, String.class));
            redisCache.evict(cacheName, key);
            assertNull(redisCache.get(cacheName, key, String.class));
        } finally {
            redisCache.evict(cacheName, key);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApp {
    }
}
