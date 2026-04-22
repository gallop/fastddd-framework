package com.gallop.fastddd.cache.local;

import com.gallop.fastddd.cache.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.Set;

/**
 * Caffeine 本地缓存实现
 *
 * @author gallop
 */
public class CaffeineLocalCache {

    private final Cache<String, Object> cache;

    public CaffeineLocalCache(CacheProperties.Local config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize());
        if (config.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(config.getExpireAfterWrite());
        }
        if (config.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(config.getExpireAfterAccess());
        }
        this.cache = builder.build();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) cache.getIfPresent(key);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getAll(Set<String> keys) {
        return (Map<String, T>) cache.getAllPresent(keys);
    }

    public void putAll(Map<String, ?> entries) {
        cache.putAll(entries);
    }
}
