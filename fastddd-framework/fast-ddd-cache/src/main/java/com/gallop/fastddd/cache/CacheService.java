package com.gallop.fastddd.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 缓存服务编程 API
 *
 * @author gallop
 */
public interface CacheService {

    <T> T get(String cacheName, String key, Class<T> type);

    <T> T get(String cacheName, String key, TypeReference<T> typeRef);

    void put(String cacheName, String key, Object value);

    void evict(String cacheName, String key);

    <T> T getOrLoad(String cacheName, String key, Class<T> type, Supplier<T> loader);

    <T> Map<String, T> multiGet(String cacheName, Collection<String> keys, Class<T> type);

    void multiPut(String cacheName, Map<String, ?> entries);

    void clear(String cacheName);
}
