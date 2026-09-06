# FastDDD Framework API Documentation

欢迎使用 FastDDD 框架 API 文档！

## 框架简介

FastDDD 是一个基于 Spring Boot 的快速开发框架，遵循领域驱动设计（DDD）原则，提供了丰富的功能模块和最佳实践。

## 主要特性

### 🚀 核心功能
- **CRUD 操作**: 提供通用的增删改查操作，支持复杂查询条件
- **缓存管理**: 集成 Redis 缓存，支持多级缓存策略
- **事件驱动**: 基于 Spring Event 的事件驱动架构
- **配置管理**: 统一的配置管理和动态配置更新

### 📊 监控与文档
- **SQL 监控**: 基于 P6Spy 的 SQL 执行监控和性能分析
- **指标收集**: 集成 Micrometer 和 Prometheus 指标收集
- **API 文档**: 基于 OpenAPI 3.0 和 Knife4j 的 API 文档生成

### 🔒 安全与认证
- **统一认证**: 支持 JWT、OAuth2 等多种认证方式
- **权限控制**: 基于角色的访问控制（RBAC）
- **数据安全**: 敏感数据加密和脱敏处理

## API 分组说明

### Default Group
包含所有 API 接口的完整文档

### FastDDD Framework
框架核心 API，包括：
- 通用 CRUD 接口
- 缓存管理接口
- 配置管理接口
- 监控指标接口

### Business API
业务相关 API，包括：
- 业务逻辑接口
- 应用服务接口
- 领域服务接口

## 快速开始

### 1. 添加依赖
```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
    <version>1.0.0-jdk21-SNAPSHOT</version>
</dependency>
```

### 2. 启用自动配置
```java
@SpringBootApplication
@EnableFastDDD
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 配置文件
```yaml
fastddd:
  web:
    openapi:
      enabled: true
    knife4j:
      enabled: true
```

## 联系我们

- **Email**: support@fastddd.com
- **源码仓库**: 公开地址将在 Community 仓库创建后随发布说明公布

---

*Copyright © 2026 FastDDD Contributors · Licensed under Apache-2.0.*
