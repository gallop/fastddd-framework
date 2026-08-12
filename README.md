# FastDDD Framework

框架层包含 DDD 开发的核心基础设施模块，提供从底层工具到 Web 层的完整技术栈。

## 模块列表

| 模块 | 说明 |
|------|------|
| [fast-ddd-common](fast-ddd-common/) | 基础工具类、领域原语、统一异常体系、枚举定义 |
| [fast-ddd-config](fast-ddd-config/) | 配置管理、条件加载注解（@ConditionalOnDev 等） |
| [fast-ddd-model](fast-ddd-model/) | 数据模型基类（BasePO、BaseEntity、AggregateRoot） |
| [fast-ddd-crud](fast-ddd-crud/) | MyBatis Plus 增强、IService/BaseServiceImpl、通用 Repository |
| [fast-ddd-web](fast-ddd-web/) | Web 层基础设施、BaseController、ApiResponse 统一响应、全局异常处理 |
| [fast-ddd-application](fast-ddd-application/) | 应用层 CQRS 基础设施：Command、Query、Handler 抽象 |
| [fast-ddd-cache](fast-ddd-cache/) | 多级缓存（本地 + Redis），注解驱动的缓存策略 |
| [fast-ddd-event](fast-ddd-event/) | 领域事件、事件发布/订阅、Outbox 模式、RocketMQ 集成 |
| [fast-ddd-search](fast-ddd-search/) | Elasticsearch 集成、全文检索能力 |
| [fast-ddd-monitor](fast-ddd-monitor/) | 监控、SQL 性能追踪、Actuator 集成 |
| [fast-ddd-starter](fast-ddd-starter/) | All-in-One Spring Boot Starter，引入即可使用全部框架能力 |
| [fast-ddd-security-starter](fast-ddd-security-starter/) | 安全 SPI 架构：JWT 认证、RBAC 鉴权、审计日志 |
| [fast-ddd-ai-spring-boot-starter](fast-ddd-ai-spring-boot-starter/) | AI/LLM 基础设施，Spring AI 集成 |
| [fast-ddd-analyzer-spring-boot-starter](fast-ddd-analyzer-spring-boot-starter/) | 智能表结构分析、数据库元数据解析 |

## 依赖关系

```
common ← config ← model ← crud ← web ← starter
                                  ← application ← event
                                  ← cache
                                  ← search
                                  ← monitor
                                  ← security-starter
                                  ← ai-starter
                                  ← analyzer-starter
```

## 使用方式

在项目中引入 All-in-One Starter：

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

或按需引入单个模块（如仅需要 Web 层）：

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-web</artifactId>
    <version>${project.version}</version>
</dependency>
```
