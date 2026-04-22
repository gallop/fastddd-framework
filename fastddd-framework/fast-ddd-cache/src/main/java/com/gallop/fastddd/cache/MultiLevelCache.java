package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.local.CaffeineLocalCache;
import com.gallop.fastddd.cache.redis.RedisCache;
import com.gallop.fastddd.cache.redis.RedisCacheSyncListener;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 两级缓存核心实现
 *
 * @author gallop
 */
@Slf4j
public class MultiLevelCache {

    private final String cacheName;
    private final CaffeineLocalCache localCache;
    private final RedisCache redisCache;
    private final RedisCacheSyncListener syncListener;
    private final boolean allowNullValues;
    private final ConcurrentHashMap<String, Object> loadingLocks = new ConcurrentHashMap<>();

    public MultiLevelCache(String cacheName,
                           CaffeineLocalCache localCache,
                           RedisCache redisCache,
                           RedisCacheSyncListener syncListener,
                           boolean allowNullValues) {
        this.cacheName = cacheName;
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.syncListener = syncListener;
        this.allowNullValues = allowNullValues;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        CacheEntry<T> entry = getEntry(key, type);
        return entry.hit ? entry.value : null;
    }

    /**
     * 无类型参数的 get，使用 Redis 存储的 __type 元数据自动恢复类型。
     * 用于 Spring Cache (@Cacheable) 等无法提前获知类型的场景。
     */
    @SuppressWarnings("unchecked")
    public <T> T getAutoType(String key) {
        CacheEntry<T> entry = getEntryAutoType(key);
        return entry.hit ? entry.value : null;
    }

    @SuppressWarnings("unchecked")
    public <T> CacheEntry<T> getEntry(String key, Class<T> type) {
        if (localCache != null) {
            Object value = localCache.get(key);
            if (value != null) {
                return CacheEntry.hit((T) unwrapNull(value));
            }
        }
        if (redisCache != null) {
            Object value = redisCache.get(cacheName, key, type);
            if (value != null) {
                T unwrapped = (T) unwrapNull(value);
                backfillLocal(key, unwrapped);
                return CacheEntry.hit(unwrapped);
            }
        }
        return CacheEntry.miss();
    }

    /**
     * 使用存储的类型元数据自动反序列化（不依赖调用方传 Class）。
     */
    @SuppressWarnings("unchecked")
    public <T> CacheEntry<T> getEntryAutoType(String key) {
        if (localCache != null) {
            Object value = localCache.get(key);
            if (value != null) {
                return CacheEntry.hit((T) unwrapNull(value));
            }
        }
        if (redisCache != null) {
            Object value = redisCache.get(cacheName, key);
            if (value != null) {
                T unwrapped = (T) unwrapNull(value);
                backfillLocal(key, unwrapped);
                return CacheEntry.hit(unwrapped);
            }
        }
        return CacheEntry.miss();
    }

    public void put(String key, Object value) {
        Object stored = value != null ? value : (allowNullValues ? NullValue.INSTANCE : null);
        if (stored == null) {
            return;
        }
        if (localCache != null) {
            localCache.put(key, stored);
        }
        if (redisCache != null) {
            redisCache.put(cacheName, key, stored);
        }
    }

    public void evict(String key) {
        if (localCache != null) {
            localCache.evict(key);
        }
        if (redisCache != null) {
            redisCache.evict(cacheName, key);
        }
        if (syncListener != null) {
            syncListener.publishEvict(cacheName, key);
        }
    }

    public void evictLocal(String key) {
        if (localCache != null) {
            localCache.evict(key);
        }
    }

    public void evictAllLocal() {
        if (localCache != null) {
            localCache.clear();
        }
    }

    /**
     * 带同步的 getOrLoad，防止缓存 stampede。
     */
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader) {
        CacheEntry<T> entry = getEntry(key, type);
        if (entry.hit) {
            return entry.value;
        }
        Object lock = loadingLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                entry = getEntry(key, type);
                if (entry.hit) {
                    return entry.value;
                }
                T value = loader.get();
                put(key, value);
                return value;
            }
        } finally {
            loadingLocks.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        Set<String> missed = new LinkedHashSet<>();

        // L1 batch
        if (localCache != null) {
            Map<String, Object> local = localCache.getAll(new HashSet<>(keys));
            for (String key : keys) {
                Object val = local.get(key);
                if (val != null) {
                    result.put(key, unwrapNull(val));
                } else {
                    missed.add(key);
                }
            }
        } else {
            missed.addAll(keys);
        }

        if (missed.isEmpty() || redisCache == null) {
            return result;
        }

        // L2 batch — 传播类型参数
        Map<String, Object> redis = redisCache.multiGet(cacheName, missed, type);
        for (String key : missed) {
            Object val = redis.get(key);
            if (val != null) {
                result.put(key, unwrapNull(val));
                backfillLocal(key, val);
            }
        }
        return result;
    }

    public void multiPut(Map<String, ?> entries) {
        if (localCache != null) {
            localCache.putAll(entries);
        }
        if (redisCache != null) {
            redisCache.multiPut(cacheName, entries);
        }
    }

    public void clear() {
        if (localCache != null) {
            localCache.clear();
        }
        if (redisCache != null) {
            redisCache.clear(cacheName);
        }
        if (syncListener != null) {
            syncListener.publishClear(cacheName);
        }
    }

    public String getCacheName() {
        return cacheName;
    }

    @SuppressWarnings("unchecked")
    private <T> T unwrapNull(Object value) {
        if (value instanceof NullValue) {
            return null;
        }
        return (T) value;
    }

    private void backfillLocal(String key, Object value) {
        if (localCache != null) {
            localCache.put(key, value);
        }
    }

    /**
     * 缓存查询结果，携带 hit 标记区分命中与未命中
     */
    public static class CacheEntry<T> {
        public final T value;
        public final boolean hit;

        private CacheEntry(T value, boolean hit) {
            this.value = value;
            this.hit = hit;
        }

        public static <T> CacheEntry<T> hit(T value) {
            return new CacheEntry<>(value, true);
        }

        @SuppressWarnings("unchecked")
        public static <T> CacheEntry<T> miss() {
            return (CacheEntry<T>) MISS;
        }

        private static final CacheEntry<?> MISS = new CacheEntry<>(null, false);
    }
}
