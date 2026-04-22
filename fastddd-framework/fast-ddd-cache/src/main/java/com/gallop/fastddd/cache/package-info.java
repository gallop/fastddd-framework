/**
 * FastDDD 缓存模块 — 两级缓存（Caffeine + Redis）
 *
 * <p>提供编程 API ({@link com.gallop.fastddd.cache.CacheService}) 和
 * Spring Cache 注解 ({@code @Cacheable}/{@code @CacheEvict}/{@code @CachePut}) 支持。</p>
 *
 * <ul>
 *   <li>L1: Caffeine 本地缓存（进程内，~1ms）</li>
 *   <li>L2: Redis 分布式缓存（跨进程，~10ms）</li>
 *   <li>Redis Pub/Sub L1 失效同步</li>
 *   <li>null 值缓存防穿透</li>
 * </ul>
 *
 * @author gallop
 * @since 2025-08-31
 */
package com.gallop.fastddd.cache;