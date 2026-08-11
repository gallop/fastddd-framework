package com.gallop.fastddd.cache;

/**
 * 缓存失效同步发布器（核心接口，无 Redis 依赖）。
 *
 * <p>CACHE-001：原 {@code com.gallop.fastddd.cache.redis.RedisCacheSyncListener} 直接被
 * {@link CacheRegistry} / {@link MultiLevelCache} 以具体类型引用，而该类 {@code implements
 * org.springframework.data.redis.connection.MessageListener}（Redis optional 类）。当 Redis
 * 不在 classpath 时，{@code cacheRegistry} bean 装配解析其 {@code ObjectProvider} 参数会强制
 * 加载 {@code RedisCacheSyncListener} → 触发 {@code MessageListener} 的 {@code NoClassDefFoundError}。
 *
 * <p>本接口置于核心包（不依赖 Redis）：核心层只引用 {@link CacheSyncPublisher}；
 * Redis 实现仅在 Redis 在 classpath 时（{@code @ConditionalOnClass} 门禁内）注册为 bean。
 * 缺 Redis 时无 bean，{@code ObjectProvider#getIfAvailable()} 返回 null，不触发任何 Redis 类加载。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public interface CacheSyncPublisher {

    /** 发布单 key 失效通知（跨节点清本地 L1）。 */
    void publishEvict(String cacheName, String key);

    /** 发布整 cacheName 清空通知（跨节点清本地 L1）。 */
    void publishClear(String cacheName);
}
