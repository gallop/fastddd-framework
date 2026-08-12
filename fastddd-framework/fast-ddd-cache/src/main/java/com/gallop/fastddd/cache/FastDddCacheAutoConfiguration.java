package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.local.CaffeineLocalCache;
import com.gallop.fastddd.cache.redis.RedisCache;
import com.gallop.fastddd.cache.redis.RedisCacheSyncListener;
import com.gallop.fastddd.cache.spring.MultiLevelCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 缓存模块自动配置
 *
 * @author gallop
 */
@Slf4j
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching
@ConditionalOnProperty(prefix = "fastddd.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FastDddCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheRegistry cacheRegistry(CacheProperties properties,
                                       ObjectProvider<CaffeineLocalCache> localCacheProvider,
                                       ObjectProvider<RedisCache> redisCacheProvider,
                                       ObjectProvider<CacheSyncPublisher> syncListenerProvider) {
        return new CacheRegistry(properties,
                localCacheProvider.getIfAvailable(),
                redisCacheProvider.getIfAvailable(),
                syncListenerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(CacheRegistry cacheRegistry) {
        return new DefaultCacheService(cacheRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public MultiLevelCacheManager multiLevelCacheManager(CacheRegistry cacheRegistry) {
        return new MultiLevelCacheManager(cacheRegistry);
    }

    @Configuration
    @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Cache")
    @ConditionalOnProperty(prefix = "fastddd.cache.local", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class CaffeineCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CaffeineLocalCache caffeineLocalCache(CacheProperties properties) {
            log.info("Caffeine local cache enabled: maxSize={}, expireAfterWrite={}",
                    properties.getLocal().getMaxSize(), properties.getLocal().getExpireAfterWrite());
            return new CaffeineLocalCache(properties.getLocal());
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnProperty(prefix = "fastddd.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RedisCacheConfiguration {

        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean
        public RedisCache redisCache(StringRedisTemplate stringRedisTemplate, CacheProperties properties) {
            log.info("Redis cache enabled: keyPrefix={}, expireAfterWrite={}",
                    properties.getRedis().getKeyPrefix(), properties.getRedis().getExpireAfterWrite());
            return new RedisCache(stringRedisTemplate, properties.getRedis());
        }

        @Bean
        @ConditionalOnBean(RedisCache.class)
        @ConditionalOnMissingBean
        public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            return container;
        }

        @Bean
        @ConditionalOnBean({RedisCache.class, RedisMessageListenerContainer.class})
        @ConditionalOnMissingBean
        public RedisCacheSyncListener redisCacheSyncListener(CacheRegistry cacheRegistry,
                                                              StringRedisTemplate stringRedisTemplate,
                                                              CacheProperties properties,
                                                              RedisMessageListenerContainer container) {
            return new RedisCacheSyncListener(cacheRegistry, stringRedisTemplate,
                    properties.getRedis().getSyncTopic(), container);
        }
    }
}
