# FastDDD Cache Module Usage Guide

**English** | [简体中文](CACHE_GUIDE.zh-CN.md)

> Two-level caching (Caffeine L1 + Redis L2) — zero configuration out of the box, integrate a new project in 5 minutes.

## 1. Add the Dependency

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Optional dependencies** (runtime):
- Redis: add `spring-boot-starter-data-redis` and configure `spring.data.redis.*`
- Without Redis → automatically degrades to a pure local cache (Caffeine only)

## 2. Zero-Configuration Startup

The module works without any configuration. Default values:

| Property | Default | Description |
|--------|--------|------|
| `fastddd.cache.enabled` | `true` | Master switch for the module |
| `fastddd.cache.allow-null-values` | `true` | Cache nulls to prevent cache penetration |
| `fastddd.cache.local.max-size` | `10000` | Maximum number of L1 entries |
| `fastddd.cache.local.expire-after-write` | `5m` | L1 expiry after write |
| `fastddd.cache.redis.expire-after-write` | `30m` | L2 expiry after write |
| `fastddd.cache.redis.key-prefix` | `fastddd:` | Redis key prefix |

## 3. Usage

### 3.1 Spring Cache Annotations (Recommended)

The simplest approach — just annotate your methods:

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    // 查询时缓存 — key 为 id 参数的字符串形式
    @Cacheable(cacheNames = "users", key = "#id")
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    // 更新时刷新缓存
    @CachePut(cacheNames = "users", key = "#user.id")
    public User update(User user) {
        userMapper.updateById(user);
        return user;
    }

    // 删除时清除缓存
    @CacheEvict(cacheNames = "users", key = "#id")
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    // 清除整个缓存区域
    @CacheEvict(cacheNames = "users", allEntries = true)
    public void clearCache() {}
}
```

**Return type safety**: objects returned by `@Cacheable` methods keep the exact type of the original method (the framework automatically stores type metadata in Redis), so the `LinkedHashMap` problem never occurs.

### 3.2 Programmatic API (CacheService)

For scenarios that need fine-grained control over caching logic:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CacheService cacheService;
    private final OrderRepository orderRepository;

    // 基本 get/put
    public Order getOrder(Long orderId) {
        Order order = cacheService.get("orders", "order:" + orderId, Order.class);
        if (order != null) {
            return order;
        }
        order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            cacheService.put("orders", "order:" + orderId, order);
        }
        return order;
    }

    // getOrLoad — 带防击穿的自动加载（推荐）
    public Order getOrderSafe(Long orderId) {
        return cacheService.getOrLoad("orders", "order:" + orderId, Order.class,
            () -> orderRepository.findById(orderId).orElse(null));
    }

    // 批量操作
    public Map<String, User> batchGetUsers(List<String> userIds) {
        return cacheService.multiGet("users", userIds, User.class);
    }

    public void batchPutUsers(Map<String, User> users) {
        cacheService.multiPut("users", users);
    }

    // 清除
    public void invalidateOrder(Long orderId) {
        cacheService.evict("orders", "order:" + orderId);
    }

    public void clearAllOrders() {
        cacheService.clear("orders");
    }
}
```

## 4. Configuration Reference

### 4.1 Full Configuration

```yaml
fastddd:
  cache:
    enabled: true                    # 模块总开关
    allow-null-values: true          # 缓存 null 值（防止缓存穿透）
    local:
      enabled: true                  # L1 开关
      max-size: 10000                # 最大条目数
      expire-after-write: 5m         # 写入后过期时间
      expire-after-access:           # 访问后过期（可选，默认不启用）
    redis:
      enabled: true                  # L2 开关
      key-prefix: "fastddd:"         # Redis key 前缀
      expire-after-write: 30m        # 默认 Redis TTL
      sync-topic: "fastddd:cache:sync"  # Pub/Sub 同步 topic
    caches:                          # 按 cache name 覆盖配置
      users:
        local:
          max-size: 5000
          expire-after-write: 2m
        redis:
          expire-after-write: 10m
      orders:
        local:
          max-size: 20000
          expire-after-write: 10m
        redis:
          expire-after-write: 1h
```

### 4.2 Per-Cache-Name Customization

The `caches` property lets you set independent L1/L2 parameters for individual cache regions:

```yaml
fastddd:
  cache:
    local:
      max-size: 10000
      expire-after-write: 5m
    redis:
      expire-after-write: 30m
    caches:
      # 用户缓存 — 短 TTL，小容量
      users:
        local:
          max-size: 5000
          expire-after-write: 2m
        redis:
          expire-after-write: 10m
      # 配置缓存 — 长 TTL，极少变更
      sys-config:
        local:
          max-size: 1000
          expire-after-write: 30m
        redis:
          expire-after-write: 2h
```

Cache names not configured under `caches` use the global defaults.

## 5. Runtime Scenarios

| Scenario | Configuration | Behavior |
|------|------|------|
| **Full two-level** | Redis configured | L1 → L2 → loader; L2 hits backfill L1; Pub/Sub syncs L1 invalidation |
| **Local only** | No Redis dependency/config | Caffeine L1 only — suitable for monolithic applications or development environments |
| **Cache disabled** | `fastddd.cache.enabled=false` | No beans are loaded; @Cacheable has no effect |

## 6. Cache Architecture

```
┌─────────────┐    miss    ┌─────────────┐    miss    ┌──────────┐
│  L1 Caffeine │ ────────→ │  L2 Redis   │ ────────→ │  Loader  │
│  (进程内~1ms) │ ←──────── │  (~10ms)    │           │ (数据源)  │
└─────────────┘  backfill  └─────────────┘           └──────────┘
      ↑                          ↑
      │    Redis Pub/Sub         │
      └──── L1 失效同步 ←────────┘
```

**Write flow**: `put(key, value)` → write L1 + write L2 (with type metadata)

**Read flow**: `get(key)` → L1 hit? → return : L2 hit? → backfill L1 + return : return null

**Invalidation flow**: `evict(key)` → delete L1 + delete L2 + Pub/Sub notifies other nodes to delete L1

**Type safety**: Redis stores a `{"__type":"className","__value":{...}}` wrapper structure; the correct type is restored automatically on deserialization.

## 7. Null Value Handling

With the default `allow-null-values: true`, the framework uses an internal `NullValue` marker to cache null results:

```java
// 防止缓存穿透 — 查不到也会缓存
@Cacheable(cacheNames = "users", key = "#id")
public User getById(Long id) {
    return userMapper.selectById(id);  // 返回 null 也会被缓存
}
```

To disable caching of null values:
```yaml
fastddd:
  cache:
    allow-null-values: false
```

## 8. Stampede Protection

`CacheService.getOrLoad()` has built-in per-key synchronized double-checking:

```java
// 多线程并发查询同一 key，只有一个线程执行 loader
cacheService.getOrLoad("users", "user:123", User.class,
    () -> userMapper.selectById(123));
```

## 9. Batch Operations

```java
// 批量读取 — L1 批量查 → 未命中 key 走 L2
Map<String, User> users = cacheService.multiGet("users",
    List.of("user:1", "user:2", "user:3"), User.class);

// 批量写入
Map<String, User> batch = Map.of("user:1", user1, "user:2", user2);
cacheService.multiPut("users", batch);
```

## 10. Mixing Spring Cache Annotations with CacheService

Both share the same `MultiLevelCache` instance (via `CacheRegistry`) and operate on the same data:

```java
// 注解方式写入
@CachePut(cacheNames = "users", key = "#user.id")
public User save(User user) { ... }

// 编程方式读取 — 读到注解写入的数据
User user = cacheService.get("users", String.valueOf(userId), User.class);
```

## 11. Redis Key Format

```
{keyPrefix}{cacheName}::{key}
```

Examples:
- `fastddd:users::123`
- `fastddd:orders::order:456`

Redis value format (with type metadata):
```json
{"__type":"com.example.User","__value":{"id":123,"name":"张三","email":"zhangsan@example.com"}}
```
