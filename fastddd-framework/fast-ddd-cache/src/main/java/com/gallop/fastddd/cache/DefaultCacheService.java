package com.gallop.fastddd.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CacheService 默认实现，委托 CacheRegistry 获取共享缓存实例
 *
 * @author gallop
 */
public class DefaultCacheService implements CacheService {

    private final CacheRegistry cacheRegistry;

    public DefaultCacheService(CacheRegistry cacheRegistry) {
        this.cacheRegistry = cacheRegistry;
    }

    @Override
    public <T> T get(String cacheName, String key, Class<T> type) {
        return cacheRegistry.getOrCreate(cacheName).get(key, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, TypeReference<T> typeRef) {
        // Redis 存储了 __type 元数据，getAutoType 可自动恢复正确类型。
        // L1 (Caffeine) 存储原始 Java 对象，天然保持类型。
        Object value = cacheRegistry.getOrCreate(cacheName).getAutoType(key);
        return (T) value;
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        cacheRegistry.getOrCreate(cacheName).put(key, value);
    }

    @Override
    public void evict(String cacheName, String key) {
        cacheRegistry.getOrCreate(cacheName).evict(key);
    }

    @Override
    public <T> T getOrLoad(String cacheName, String key, Class<T> type, Supplier<T> loader) {
        return cacheRegistry.getOrCreate(cacheName).getOrLoad(key, type, loader);
    }

    @Override
    public <T> Map<String, T> multiGet(String cacheName, Collection<String> keys, Class<T> type) {
        return cacheRegistry.getOrCreate(cacheName).multiGet(keys, type);
    }

    @Override
    public void multiPut(String cacheName, Map<String, ?> entries) {
        cacheRegistry.getOrCreate(cacheName).multiPut(entries);
    }

    @Override
    public void clear(String cacheName) {
        cacheRegistry.getOrCreate(cacheName).clear();
    }
}
