package com.gallop.fastddd.consumer.cache;

import com.gallop.fastddd.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CACHE-001 验收消费者：fast-ddd-cache 在【无 Redis】的 classpath 下可启动，且 L1 命名空间隔离生效。
 *
 * <p>历史（FRM-002）为预置 shell（{@code assertTrue(true)}），并因 Redis 类泄漏
 * （{@code RedisCacheSyncListener implements MessageListener} 被核心层硬引用）启动失败。
 * CACHE-001 抽离 {@code CacheSyncPublisher} 核心接口解除泄漏 → 消费者可启动；同时 L1 按 cacheName
 * 隔离 → 相同 key 跨 cacheName 不串值。
 *
 * <p>验收：①无 Redis 启动成功（context loads + CacheService 装配）；②users/orders 相同 key 不串值。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = CacheConsumerTest.App.class)
class CacheConsumerTest {

    @Autowired
    CacheService cacheService;

    @SpringBootApplication
    static class App {
    }

    @Test
    void contextLoadsWithoutRedis() {
        // ① 无 Redis 启动成功（Redis 类泄漏已修）+ CacheService 装配
        assertNotNull(cacheService, "CacheService 应在无 Redis 时装配");
    }

    @Test
    void l1NamespaceIsolation_sameKeyNoCrossContamination() {
        // ② L1 命名空间隔离：不同 cacheName 相同 key 不串值
        cacheService.put("users", "1", "alice");
        cacheService.put("orders", "1", 42);

        assertEquals("alice", cacheService.get("users", "1", String.class),
                "users::1 应是 alice（未被 orders::1 覆盖）");
        assertEquals(42, cacheService.get("orders", "1", Integer.class),
                "orders::1 应是 42（未被 users::1 覆盖）");

        // evict 隔离
        cacheService.evict("orders", "1");
        assertEquals("alice", cacheService.get("users", "1", String.class),
                "evict(orders,1) 不应误杀 users::1");
        assertNull(cacheService.get("orders", "1", Integer.class),
                "orders::1 应已失效");
    }
}
