# FastDDD 条件控制系统使用指南

## 概述

FastDDD 条件控制系统提供了一套强大的条件注解，用于根据不同的环境条件动态控制 Bean 的创建和配置。这些注解基于 Spring 的条件机制实现，提供了比 Spring Boot 原生条件注解更灵活的控制能力。

## 核心注解

### 1. Profile 相关注解

#### @ConditionalOnProfile
根据 Spring Profile 的活跃状态条件性地启用 Bean。

```java
@Component
@ConditionalOnProfile("dev")
public class DevOnlyService {
    // 仅在 dev profile 活跃时创建
}

@Configuration
@ConditionalOnProfile({"test", "integration"})
public class TestConfiguration {
    // 在 test 或 integration profile 活跃时创建
}
```

#### @ConditionalOnDev
便利注解，等同于 `@ConditionalOnProfile("dev")`。

```java
@Service
@ConditionalOnDev
public class DebugService {
    // 仅在开发环境中可用
}
```

#### @ConditionalOnProd
便利注解，等同于 `@ConditionalOnProfile("prod")`。

```java
@Component
@ConditionalOnProd
public class ProductionMetricsCollector {
    // 仅在生产环境中启用
}
```

### 2. 系统属性相关注解

#### @ConditionalOnSystemProperty
根据系统属性的存在及其值条件性地启用 Bean。

```java
// 检查系统属性是否存在
@Component
@ConditionalOnSystemProperty(name = "debug.enabled")
public class DebugComponent {
    // 当系统属性 debug.enabled 存在时创建
}

// 检查系统属性的具体值
@Configuration
@ConditionalOnSystemProperty(name = "app.mode", havingValue = "standalone")
public class StandaloneConfiguration {
    // 当 app.mode=standalone 时创建
}

// 检查系统属性值不匹配时创建
@Service
@ConditionalOnSystemProperty(name = "cache.type", havingValue = "redis", matchIfMissing = false)
public class RedisCacheService {
    // 当 cache.type=redis 时创建，如果属性不存在则不创建
}
```

### 3. 类存在性相关注解

#### @ConditionalOnClass
根据类在类路径中的存在性条件性地启用 Bean。

```java
@Configuration
@ConditionalOnClass("redis.clients.jedis.Jedis")
public class RedisConfiguration {
    // 当 Jedis 类存在于类路径中时创建
}

@Component
@ConditionalOnClass({"org.springframework.data.redis.core.RedisTemplate", "redis.clients.jedis.Jedis"})
public class RedisService {
    // 当所有指定的类都存在时创建
}
```

#### @ConditionalOnMissingClass
根据类在类路径中的缺失情况条件性地启用 Bean。

```java
@Component
@ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
public class SimpleJsonProcessor {
    // 当 Jackson 不存在时使用简单的 JSON 处理器
}

@Configuration
@ConditionalOnMissingClass({"org.springframework.data.redis.core.RedisTemplate"})
public class InMemoryCacheConfiguration {
    // 当 Redis 相关类不存在时使用内存缓存
}
```

## 使用场景

### 1. 环境特定配置

```java
@Configuration
public class DatabaseConfiguration {
    
    @Bean
    @ConditionalOnDev
    public DataSource devDataSource() {
        // 开发环境数据源配置
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }
    
    @Bean
    @ConditionalOnProd
    public DataSource prodDataSource() {
        // 生产环境数据源配置
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://prod-db:3306/app");
        return new HikariDataSource(config);
    }
}
```

### 2. 功能开关控制

```java
@Service
@ConditionalOnSystemProperty(name = "feature.advanced.search", havingValue = "true")
public class AdvancedSearchService {
    // 高级搜索功能，通过系统属性控制
}

@Component
@ConditionalOnSystemProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringService {
    // 监控服务，默认启用，可通过系统属性禁用
}
```

### 3. 依赖适配

```java
@Configuration
public class CacheConfiguration {
    
    @Bean
    @ConditionalOnClass("org.springframework.data.redis.core.RedisTemplate")
    public CacheManager redisCacheManager() {
        // 当 Redis 可用时使用 Redis 缓存
        return new RedisCacheManager(redisConnectionFactory());
    }
    
    @Bean
    @ConditionalOnMissingClass("org.springframework.data.redis.core.RedisTemplate")
    public CacheManager simpleCacheManager() {
        // 当 Redis 不可用时使用简单缓存
        return new ConcurrentMapCacheManager();
    }
}
```

### 4. 复合条件

```java
@Component
@ConditionalOnProfile("prod")
@ConditionalOnSystemProperty(name = "metrics.export.enabled", havingValue = "true")
@ConditionalOnClass("io.micrometer.core.instrument.MeterRegistry")
public class MetricsExporter {
    // 仅在生产环境、启用指标导出且 Micrometer 可用时创建
}
```

## 最佳实践

### 1. 注解组合使用
- 可以在同一个类上使用多个条件注解，它们之间是 AND 关系
- 合理组合不同类型的条件注解以实现精确控制

### 2. 性能考虑
- 条件评估在应用启动时进行，不会影响运行时性能
- 类存在性检查使用类加载器，避免实际加载类

### 3. 调试技巧
- 启用 Spring Boot 的条件报告：`--debug` 或 `logging.level.org.springframework.boot.autoconfigure=DEBUG`
- 使用 `@ConditionalOnSystemProperty` 时，可通过 JVM 参数 `-Dproperty=value` 设置属性

### 4. 测试策略
- 为不同条件场景编写集成测试
- 使用 `@ActiveProfiles` 测试 Profile 相关条件
- 使用 `@TestPropertySource` 测试系统属性相关条件

## 注意事项

1. **条件评估时机**：条件在 Bean 定义阶段评估，而非 Bean 创建时
2. **类路径检查**：使用完整的类名进行检查，包括包路径
3. **系统属性优先级**：JVM 系统属性优先于环境变量
4. **Profile 匹配**：支持表达式，如 `!prod` 表示非生产环境

## 扩展开发

如需自定义条件，可以实现 `org.springframework.context.annotation.Condition` 接口：

```java
public class CustomCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 自定义条件逻辑
        return true;
    }
}

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(CustomCondition.class)
public @interface ConditionalOnCustom {
    // 自定义注解属性
}
```

通过这套条件控制系统，您可以构建更加灵活和适应性强的应用程序，实现真正的环境感知和动态配置。