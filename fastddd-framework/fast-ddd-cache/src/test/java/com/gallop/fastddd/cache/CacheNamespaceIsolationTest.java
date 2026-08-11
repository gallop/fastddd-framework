package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.local.CaffeineLocalCache;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 缓存命名空间隔离单测（CACHE-001 验收）。
 *
 * <p>刻画并固化：不同 cacheName 即使使用相同 key，在 L1（Caffeine）层也不串值、不误清理。
 * 修复前所有 cacheName 共享一个 {@code CaffeineLocalCache} 单例，{@code users::1} 与
 * {@code orders::1} 在 L1 中都存为 key {@code "1"}——后写覆盖先写、evict/clear 跨 namespace 误杀。
 * 修复后每个 cacheName 独占一个 {@link CaffeineLocalCache} 实例（对象级隔离）。
 *
 * <p>本测试无 Redis（纯 L1），{@link CacheRegistry} 同步发布器传 null，验证缺 Redis 时无类加载泄漏。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class CacheNamespaceIsolationTest {

    /** 构造一个 L1-only 的 CacheRegistry（无 Redis、无同步发布器）。 */
    private static CacheRegistry l1OnlyRegistry() {
        CacheProperties props = new CacheProperties();
        props.setAllowNullValues(true);
        props.getLocal().setEnabled(true);
        props.getLocal().setMaxSize(100);
        // defaultLocalCache 作 L1 启用标记；createCache 会为每个 cacheName 新建独立实例
        return new CacheRegistry(props, new CaffeineLocalCache(props.getLocal()), null, null);
    }

    @Test
    void sameKeyDifferentCacheName_shallNotCrossContaminate() {
        CacheRegistry registry = l1OnlyRegistry();
        MultiLevelCache users = registry.getOrCreate("users");
        MultiLevelCache orders = registry.getOrCreate("orders");

        users.put("1", "alice");
        orders.put("1", 42);

        // 不同 cacheName 相同 key：各自返回自己的值，不串
        assertThat(users.<String>get("1", String.class))
                .as("users::1 应仍是 alice（未被 orders::1 覆盖）").isEqualTo("alice");
        assertThat(orders.<Integer>get("1", Integer.class))
                .as("orders::1 应是 42（未被 users::1 覆盖）").isEqualTo(42);
    }

    @Test
    void evictOneCacheName_shallNotEvictSameKeyInOtherCacheName() {
        CacheRegistry registry = l1OnlyRegistry();
        MultiLevelCache users = registry.getOrCreate("users");
        MultiLevelCache orders = registry.getOrCreate("orders");

        users.put("1", "alice");
        orders.put("1", 42);

        orders.evict("1");  // 仅失效 orders::1

        assertThat(users.<String>get("1", String.class))
                .as("evict(orders,1) 不应误杀 users::1").isEqualTo("alice");
        assertThat(orders.<Integer>get("1", Integer.class))
                .as("orders::1 应已失效").isNull();
    }

    @Test
    void clearOneCacheName_shallNotClearOtherCacheName() {
        CacheRegistry registry = l1OnlyRegistry();
        MultiLevelCache users = registry.getOrCreate("users");
        MultiLevelCache orders = registry.getOrCreate("orders");

        users.put("1", "alice");
        users.put("2", "bob");
        orders.put("1", 42);
        orders.put("2", 99);

        orders.clear();  // 仅清空 orders 的 L1

        assertThat(users.<String>get("1", String.class)).as("users::1 不应被 orders.clear() 清除").isEqualTo("alice");
        assertThat(users.<String>get("2", String.class)).as("users::2 不应被 orders.clear() 清除").isEqualTo("bob");
        assertThat(orders.<Integer>get("1", Integer.class)).as("orders::1 应被 clear() 清除").isNull();
        assertThat(orders.<Integer>get("2", Integer.class)).as("orders::2 应被 clear() 清除").isNull();
    }

    @Test
    void differentValueTypes_shallNotCrossCast() {
        CacheRegistry registry = l1OnlyRegistry();
        MultiLevelCache a = registry.getOrCreate("cache-a");
        MultiLevelCache b = registry.getOrCreate("cache-b");

        a.put("shared", 100L);
        b.put("shared", "string-value");

        // 类型隔离：各自类型正确，不会因共享 L1 把 Long 当 String
        assertThat(a.<Long>get("shared", Long.class)).isEqualTo(100L);
        assertThat(b.<String>get("shared", String.class)).isEqualTo("string-value");
    }

    @Test
    void concurrentCreate_sameCacheName_shallYieldSingleInstance() throws InterruptedException {
        // 并发 getOrCreate 同一 cacheName 应只创建一个 MultiLevelCache（命名空间稳定）
        CacheRegistry registry = l1OnlyRegistry();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger distinct = new AtomicInteger(0);
        Set<MultiLevelCache> seen = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                MultiLevelCache c = registry.getOrCreate("concurrent");
                if (seen.add(c)) {
                    distinct.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(distinct.get())
                .as("并发 getOrCreate 同一 cacheName 应只产生 1 个实例").isEqualTo(1);
    }
}
