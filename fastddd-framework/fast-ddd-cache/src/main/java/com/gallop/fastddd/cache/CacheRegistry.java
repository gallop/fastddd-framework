package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.local.CaffeineLocalCache;
import com.gallop.fastddd.cache.redis.RedisCache;
import com.gallop.fastddd.cache.redis.RedisCacheSyncListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享缓存实例注册表。
 * CacheService 和 CacheManager 通过此类获取同一 MultiLevelCache 实例。
 *
 * @author gallop
 */
@Slf4j
public class CacheRegistry {

    private final ConcurrentHashMap<String, MultiLevelCache> caches = new ConcurrentHashMap<>();

    private final CacheProperties properties;
    private final @Nullable CaffeineLocalCache defaultLocalCache;
    private final @Nullable RedisCache redisCache;
    private final ObjectProvider<RedisCacheSyncListener> syncListenerProvider;
    private volatile @Nullable RedisCacheSyncListener resolvedSyncListener;

    public CacheRegistry(CacheProperties properties,
                         @Nullable CaffeineLocalCache localCache,
                         @Nullable RedisCache redisCache,
                         ObjectProvider<RedisCacheSyncListener> syncListenerProvider) {
        this.properties = properties;
        this.defaultLocalCache = localCache;
        this.redisCache = redisCache;
        this.syncListenerProvider = syncListenerProvider;
        log.info("CacheRegistry initialized: local={}, redis={}",
                localCache != null, redisCache != null);
    }

    private RedisCacheSyncListener getSyncListener() {
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

        CaffeineLocalCache localCache = defaultLocalCache;
        if (localCache != null && perConfig != null && perConfig.getLocal() != null) {
            CacheProperties.Local merged = mergeLocalConfig(properties.getLocal(), perConfig.getLocal());
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
