package com.gallop.fastddd.cache.redis;

import com.gallop.fastddd.cache.CacheProperties;
import com.gallop.fastddd.cache.NullValue;
import com.gallop.fastddd.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.*;

/**
 * Redis 分布式缓存
 * <p>
 * 写入时存储带类型元数据的包装结构：{"__type":"com.example.User","__value":{...}}
 * 读取时自动从元数据恢复正确类型，解决 @Cacheable / TypeReference 场景下的类型丢失问题。
 *
 * @author gallop
 */
@Slf4j
public class RedisCache {

    private static final String NULL_VALUE_MARKER = "\"INSTANCE\"";
    private static final String TYPE_FIELD = "__type";
    private static final String VALUE_FIELD = "__value";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration expireAfterWrite;

    public RedisCache(StringRedisTemplate redisTemplate, CacheProperties.Redis config) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = config.getKeyPrefix();
        this.expireAfterWrite = config.getExpireAfterWrite();
    }

    public RedisCache(StringRedisTemplate redisTemplate, String keyPrefix, Duration expireAfterWrite) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.expireAfterWrite = expireAfterWrite;
    }

    private String buildKey(String cacheName, String key) {
        return keyPrefix + cacheName + "::" + key;
    }

    /**
     * 从 Redis 读取值，优先使用调用方提供的类型，否则从存储的 __type 元数据恢复。
     *
     * @return 缓存值，如果缓存了 null 则返回 NullValue.INSTANCE，如果未命中返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(buildKey(cacheName, key));
        if (json == null) {
            return null;
        }
        if (isNullValueMarker(json)) {
            return (T) NullValue.INSTANCE;
        }
        return deserializeWithTypeInfo(json, type);
    }

    /**
     * 从 Redis 读取值，不传类型时使用存储的 __type 元数据恢复。
     */
    @SuppressWarnings("unchecked")
    public Object get(String cacheName, String key) {
        String json = redisTemplate.opsForValue().get(buildKey(cacheName, key));
        if (json == null) {
            return null;
        }
        if (isNullValueMarker(json)) {
            return NullValue.INSTANCE;
        }
        return deserializeWithTypeInfo(json, null);
    }

    public void put(String cacheName, String key, Object value) {
        String redisKey = buildKey(cacheName, key);
        if (value instanceof NullValue) {
            setWithTtl(redisKey, NULL_VALUE_MARKER);
            return;
        }
        String json = serializeWithType(value);
        setWithTtl(redisKey, json);
    }

    /**
     * 带自定义 TTL 的写入。
     */
    public void put(String cacheName, String key, Object value, Duration ttl) {
        String redisKey = buildKey(cacheName, key);
        if (value instanceof NullValue) {
            setWithTtl(redisKey, NULL_VALUE_MARKER, ttl);
            return;
        }
        String json = serializeWithType(value);
        setWithTtl(redisKey, json, ttl);
    }

    public void evict(String cacheName, String key) {
        redisTemplate.delete(buildKey(cacheName, key));
    }

    public void clear(String cacheName) {
        String pattern = keyPrefix + cacheName + "::*";
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, Object> multiGet(String cacheName, Collection<String> keys, Class<T> type) {
        List<String> redisKeys = keys.stream()
                .map(k -> buildKey(cacheName, k))
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(redisKeys);
        if (values == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        int i = 0;
        for (String key : keys) {
            String json = values.get(i++);
            if (json != null) {
                if (isNullValueMarker(json)) {
                    result.put(key, NullValue.INSTANCE);
                } else {
                    result.put(key, deserializeWithTypeInfo(json, type));
                }
            }
        }
        return result;
    }

    public void multiPut(String cacheName, Map<String, ?> entries) {
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            put(cacheName, entry.getKey(), entry.getValue());
        }
    }

    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    // ---- internal ----

    /**
     * 序列化时写入 {"__type":"className","__value":{...}} 包装结构。
     */
    private String serializeWithType(Object value) {
        ObjectNode wrapper = JsonUtils.getObjectMapper().createObjectNode();
        wrapper.put(TYPE_FIELD, value.getClass().getName());
        wrapper.set(VALUE_FIELD, JsonUtils.getObjectMapper().valueToTree(value));
        return wrapper.toString();
    }

    /**
     * 反序列化：优先使用调用方类型；若未提供则从 __type 元数据加载类型。
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeWithTypeInfo(String json, Class<T> preferredType) {
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(json);
            if (node.has(TYPE_FIELD) && node.has(VALUE_FIELD)) {
                Class<?> resolvedType = preferredType;
                if (resolvedType == null || resolvedType == Object.class) {
                    String className = node.get(TYPE_FIELD).asText();
                    resolvedType = Class.forName(className);
                }
                JsonNode valueNode = node.get(VALUE_FIELD);
                return (T) JsonUtils.getObjectMapper().treeToValue(valueNode, resolvedType);
            }
            // 兼容旧格式（无包装）
            if (preferredType != null && preferredType != Object.class) {
                return JsonUtils.fromJson(json, preferredType);
            }
            return (T) JsonUtils.fromJsonToMap(json);
        } catch (ClassNotFoundException e) {
            log.warn("Type class not found for cached value, falling back: {}", e.getMessage());
            if (preferredType != null) {
                return JsonUtils.fromJson(json, preferredType);
            }
            return (T) JsonUtils.fromJsonToMap(json);
        } catch (Exception e) {
            log.warn("Failed to deserialize with type info, falling back: {}", e.getMessage());
            if (preferredType != null) {
                return JsonUtils.fromJson(json, preferredType);
            }
            return (T) JsonUtils.fromJsonToMap(json);
        }
    }

    private void setWithTtl(String redisKey, String value) {
        if (expireAfterWrite != null) {
            redisTemplate.opsForValue().set(redisKey, value, expireAfterWrite);
        } else {
            redisTemplate.opsForValue().set(redisKey, value);
        }
    }

    private void setWithTtl(String redisKey, String value, Duration ttl) {
        Duration effectiveTtl = ttl != null ? ttl : expireAfterWrite;
        if (effectiveTtl != null) {
            redisTemplate.opsForValue().set(redisKey, value, effectiveTtl);
        } else {
            redisTemplate.opsForValue().set(redisKey, value);
        }
    }

    private boolean isNullValueMarker(String json) {
        return NULL_VALUE_MARKER.equals(json);
    }
}
