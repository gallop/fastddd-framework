package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.local.CaffeineLocalCache;
import com.gallop.fastddd.cache.redis.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享缓存实例注册表。
 * CacheService 和 CacheManager 通过此类获取同一 MultiLevelCache 实例。
 *
 * <p>CACHE-001：L1（Caffeine）按 cacheName 命名空间隔离——每个 cacheName 独占一个
 * {@link CaffeineLocalCache} 实例（对象级隔离），不同 cacheName 相同 key 不串值、evict/clear
 * 不跨 namespace 误杀。{@code defaultLocalCache} 单例仅作"L1 已启用"标记与全局配置来源，
 * 不再被各 cacheName 共享存储。同步发布器依赖抽象为 {@link CacheSyncPublisher}（核心接口），
 * 解除对 Redis 实现类的硬引用，避免 Redis 缺席时类加载泄漏。
 *
 * @author gallop
 */
@Slf4j
public class CacheRegistry {

    private final ConcurrentHashMap<String, MultiLevelCache> caches = new ConcurrentHashMap<>();

    private final CacheProperties properties;
    private final @Nullable CaffeineLocalCache defaultLocalCache;
    private final @Nullable RedisCache redisCache;
    private final @Nullable ObjectProvider<CacheSyncPublisher> syncListenerProvider;
    private volatile @Nullable CacheSyncPublisher resolvedSyncListener;

    public CacheRegistry(CacheProperties properties,
                         @Nullable CaffeineLocalCache localCache,
                         @Nullable RedisCache redisCache,
                         @Nullable ObjectProvider<CacheSyncPublisher> syncListenerProvider) {
        this.properties = properties;
        this.defaultLocalCache = localCache;
        this.redisCache = redisCache;
        this.syncListenerProvider = syncListenerProvider;
        log.info("CacheRegistry initialized: local={}, redis={}",
                localCache != null, redisCache != null);
    }

    private CacheSyncPublisher getSyncListener() {
        if (syncListenerProvider == null) {
            return null;
        }
        if (resolvedSyncListener == null) {
            resolvedSyncListener = syncListenerProvider.getIfAvailable();
        }
        return resolvedSyncListener;
    }

    public MultiLevelCache getOrCreate(String cacheName) {
        return caches.computeIfAbsent(cacheName, this::createCache);
    }

    private MultiLevelCache createCache(String cacheName) {
        CacheProperties.PerCacheConfig perConfig = properties.getCaches().get(cacheName);

        // CACHE-001：每个 cacheName 独占一个 CaffeineLocalCache 实例（对象级命名空间隔离），
        // 避免 users/orders 相同 key 在共享 L1 中互相覆盖或被 evict/clear 误杀。
        // defaultLocalCache 仅作 L1 启用标记 + 全局配置来源（其内部缓存不再被共享使用）。
        CaffeineLocalCache localCache = null;
        if (defaultLocalCache != null) {
            CacheProperties.Local merged = (perConfig != null && perConfig.getLocal() != null)
                    ? mergeLocalConfig(properties.getLocal(), perConfig.getLocal())
                    : properties.getLocal();
            localCache = new CaffeineLocalCache(merged);
        }

        RedisCache effectiveRedisCache = redisCache;
        if (redisCache != null && perConfig != null && perConfig.getRedis() != null) {
            CacheProperties.RedisOverride override = perConfig.getRedis();
            if (override.getExpireAfterWrite() != null) {
                effectiveRedisCache = new RedisCache(
                        redisCache.getRedisTemplate(),
                        redisCache.getKeyPrefix(),
                        override.getExpireAfterWrite()
                );
            }
        }

        return new MultiLevelCache(cacheName, localCache, effectiveRedisCache,
                getSyncListener(), properties.isAllowNullValues());
    }

    private CacheProperties.Local mergeLocalConfig(CacheProperties.Local defaults,
                                                    CacheProperties.LocalOverride override) {
        CacheProperties.Local merged = new CacheProperties.Local();
        merged.setEnabled(defaults.isEnabled());
        merged.setMaxSize(override.getMaxSize() != null ? override.getMaxSize() : defaults.getMaxSize());
        merged.setExpireAfterWrite(
                override.getExpireAfterWrite() != null ? override.getExpireAfterWrite() : defaults.getExpireAfterWrite());
        merged.setExpireAfterAccess(
                override.getExpireAfterAccess() != null ? override.getExpireAfterAccess() : defaults.getExpireAfterAccess());
        return merged;
    }

    public Optional<MultiLevelCache> getIfPresent(String cacheName) {
        return Optional.ofNullable(caches.get(cacheName));
    }

    public CacheProperties getProperties() {
        return properties;
    }
}
