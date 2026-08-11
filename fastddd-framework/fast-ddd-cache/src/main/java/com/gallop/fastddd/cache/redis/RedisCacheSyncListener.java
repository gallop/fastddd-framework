package com.gallop.fastddd.cache.redis;

import com.gallop.fastddd.cache.CacheRegistry;
import com.gallop.fastddd.cache.CacheSyncPublisher;
import com.gallop.fastddd.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;

/**
 * Redis Pub/Sub 监听器，用于分布式 L1 缓存失效同步
 *
 * @author gallop
 */
@Slf4j
public class RedisCacheSyncListener implements CacheSyncPublisher, MessageListener {

    private final String nodeId = UUID.randomUUID().toString();
    private final CacheRegistry cacheRegistry;
    private final StringRedisTemplate redisTemplate;
    private final String syncTopic;

    public RedisCacheSyncListener(CacheRegistry cacheRegistry,
                                  StringRedisTemplate redisTemplate,
                                  String syncTopic,
                                  RedisMessageListenerContainer container) {
        this.cacheRegistry = cacheRegistry;
        this.redisTemplate = redisTemplate;
        this.syncTopic = syncTopic;
        container.addMessageListener(this, new ChannelTopic(syncTopic));
        log.info("Cache sync listener started: nodeId={}, topic={}", nodeId, syncTopic);
    }

    public void publishEvict(String cacheName, String key) {
        publish(cacheName, key, false);
    }

    public void publishClear(String cacheName) {
        publish(cacheName, null, true);
    }

    private void publish(String cacheName, String key, boolean clearAll) {
        String message = JsonUtils.toJson(new SyncMessage(cacheName, key, clearAll, nodeId));
        redisTemplate.convertAndSend(syncTopic, message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SyncMessage msg = JsonUtils.fromJson(new String(message.getBody()), SyncMessage.class);
            if (nodeId.equals(msg.nodeId())) {
                return;
            }
            if (msg.clearAll()) {
                cacheRegistry.getIfPresent(msg.cacheName()).ifPresent(cache -> cache.evictAllLocal());
            } else {
                cacheRegistry.getIfPresent(msg.cacheName()).ifPresent(cache -> cache.evictLocal(msg.key()));
            }
        } catch (Exception e) {
            log.warn("Failed to process cache sync message: {}", e.getMessage());
        }
    }

    record SyncMessage(String cacheName, String key, boolean clearAll, String nodeId) {
    }
}
