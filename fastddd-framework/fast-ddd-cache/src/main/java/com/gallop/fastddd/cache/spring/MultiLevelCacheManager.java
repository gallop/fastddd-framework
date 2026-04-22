package com.gallop.fastddd.cache.spring;

import com.gallop.fastddd.cache.CacheRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring CacheManager 实现，委托 CacheRegistry 获取共享缓存实例
 *
 * @author gallop
 */
public class MultiLevelCacheManager implements CacheManager {

    private final CacheRegistry cacheRegistry;
    private final ConcurrentHashMap<String, MultiLevelSpringCache> springCaches = new ConcurrentHashMap<>();

    public MultiLevelCacheManager(CacheRegistry cacheRegistry) {
        this.cacheRegistry = cacheRegistry;
    }

    @Override
    public Cache getCache(String name) {
        return springCaches.computeIfAbsent(name,
                n -> new MultiLevelSpringCache(cacheRegistry.getOrCreate(n)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(springCaches.keySet());
    }
}
