# FastDDD Cache 模块使用指南

> 两级缓存 (Caffeine L1 + Redis L2)，零配置开箱即用，5 分钟接入新项目。

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**可选依赖**（运行时）：
- Redis: 引入 `spring-boot-starter-data-redis` + 配置 `spring.data.redis.*`
- 无 Redis → 自动降级为纯本地缓存（仅 Caffeine）

## 2. 零配置启动

不做任何配置即可使用，默认值：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `fastddd.cache.enabled` | `true` | 模块总开关 |
| `fastddd.cache.allow-null-values` | `true` | 缓存 null 防穿透 |
| `fastddd.cache.local.max-size` | `10000` | L1 最大条目数 |
| `fastddd.cache.local.expire-after-write` | `5m` | L1 写入后过期 |
| `fastddd.cache.redis.expire-after-write` | `30m` | L2 写入后过期 |
| `fastddd.cache.redis.key-prefix` | `fastddd:` | Redis key 前缀 |

## 3. 使用方式

### 3.1 Spring Cache 注解（推荐）

最简单的用法，直接在方法上加注解：

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

**返回类型安全**：`@Cacheable` 方法返回的对象类型与原始方法一致（框架自动在 Redis 中存储类型元数据），不会出现 `LinkedHashMap` 问题。

### 3.2 编程 API（CacheService）

适合需要精细控制缓存逻辑的场景：

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

## 4. 配置详解

### 4.1 完整配置

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

### 4.2 按 cache name 定制

通过 `caches` 配置项可以为不同的缓存区域设置独立的 L1/L2 参数：

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

未在 `caches` 中配置的 cache name 使用全局默认值。

## 5. 运行场景

| 场景 | 配置 | 行为 |
|------|------|------|
| **完整两级** | 有 Redis 配置 | L1 → L2 → loader，L2 命中回填 L1，Pub/Sub 同步 L1 失效 |
| **纯本地** | 无 Redis 依赖/配置 | 仅 Caffeine L1，适合单体应用或开发环境 |
| **禁用缓存** | `fastddd.cache.enabled=false` | 不加载任何 Bean，@Cacheable 不生效 |

## 6. 缓存架构

```
┌─────────────┐    miss    ┌─────────────┐    miss    ┌──────────┐
│  L1 Caffeine │ ────────→ │  L2 Redis   │ ────────→ │  Loader  │
│  (进程内~1ms) │ ←──────── │  (~10ms)    │           │ (数据源)  │
└─────────────┘  backfill  └─────────────┘           └──────────┘
      ↑                          ↑
      │    Redis Pub/Sub         │
      └──── L1 失效同步 ←────────┘
```

**写入流程**：`put(key, value)` → 写 L1 + 写 L2（带类型元数据）

**读取流程**：`get(key)` → L1 命中? → 返回 : L2 命中? → 回填 L1 + 返回 : 返回 null

**失效流程**：`evict(key)` → 删 L1 + 删 L2 + Pub/Sub 通知其他节点删 L1

**类型安全**：Redis 中存储 `{"__type":"className","__value":{...}}` 包装结构，反序列化时自动恢复正确类型。

## 7. null 值处理

默认启用 `allow-null-values: true`，框架使用内部 `NullValue` 标记缓存 null 结果：

```java
// 防止缓存穿透 — 查不到也会缓存
@Cacheable(cacheNames = "users", key = "#id")
public User getById(Long id) {
    return userMapper.selectById(id);  // 返回 null 也会被缓存
}
```

如不希望缓存 null 值：
```yaml
fastddd:
  cache:
    allow-null-values: false
```

## 8. 防击穿（Stampede Protection）

`CacheService.getOrLoad()` 内置 per-key synchronized 双重检查：

```java
// 多线程并发查询同一 key，只有一个线程执行 loader
cacheService.getOrLoad("users", "user:123", User.class,
    () -> userMapper.selectById(123));
```

## 9. 批量操作

```java
// 批量读取 — L1 批量查 → 未命中 key 走 L2
Map<String, User> users = cacheService.multiGet("users",
    List.of("user:1", "user:2", "user:3"), User.class);

// 批量写入
Map<String, User> batch = Map.of("user:1", user1, "user:2", user2);
cacheService.multiPut("users", batch);
```

## 10. Spring Cache 注解 + CacheService 混用

两者共享同一个 `MultiLevelCache` 实例（通过 `CacheRegistry`），操作同一份数据：

```java
// 注解方式写入
@CachePut(cacheNames = "users", key = "#user.id")
public User save(User user) { ... }

// 编程方式读取 — 读到注解写入的数据
User user = cacheService.get("users", String.valueOf(userId), User.class);
```

## 11. Redis Key 格式

```
{keyPrefix}{cacheName}::{key}
```

示例：
- `fastddd:users::123`
- `fastddd:orders::order:456`

Redis 值格式（带类型元数据）：
```json
{"__type":"com.example.User","__value":{"id":123,"name":"张三","email":"zhangsan@example.com"}}
```
