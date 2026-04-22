package com.gallop.fastddd.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存模块配置属性
 *
 * @author gallop
 */
@Data
@ConfigurationProperties(prefix = "fastddd.cache")
public class CacheProperties {

    private boolean enabled = true;

    /** 是否缓存 null 值（防止缓存穿透） */
    private boolean allowNullValues = true;

    private Local local = new Local();

    private Redis redis = new Redis();

    /** 按 cache name 覆盖默认配置 */
    private Map<String, PerCacheConfig> caches = new HashMap<>();

    @Data
    public static class Local {
        private boolean enabled = true;
        private long maxSize = 10000;
        private Duration expireAfterWrite = Duration.ofMinutes(5);
        /** 访问后过期时间，null 表示不启用 */
        private Duration expireAfterAccess;
    }

    @Data
    public static class Redis {
        private boolean enabled = true;
        private Duration expireAfterWrite = Duration.ofMinutes(30);
        private String keyPrefix = "fastddd:";
        private String syncTopic = "fastddd:cache:sync";
    }

    @Data
    public static class PerCacheConfig {
        private LocalOverride local;
        private RedisOverride redis;
    }

    @Data
    public static class LocalOverride {
        private Long maxSize;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
    }

    @Data
    public static class RedisOverride {
        private Duration expireAfterWrite;
    }
}
