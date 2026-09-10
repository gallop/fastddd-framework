# FastDDD Framework Community

FastDDD Framework Community 是面向 Java 21 与 Spring Boot 3.5 的 DDD 运行时基础框架。它提供通用领域模型、应用层、Web、安全、事件、缓存、搜索、监控和可选 AI 集成，不包含 FastDDD Studio Pro 的商业控制面能力。

> 当前状态：1.0 发布准备期。公共 API、许可证、公开仓库地址和发布门禁全部关闭前，不应将 SNAPSHOT 视为正式版。

## 模块

| 模块 | 定位 | 1.0 稳定性标注 |
|---|---|---|
| `fast-ddd-common` | 通用类型、异常与领域基础能力 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-config` | 配置与条件装配 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-model` | 领域模型和持久化基类 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-crud` | Repository、Mapper 与 CRUD 基础设施 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-application` | Command、Query 与 Handler 契约 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-web` | Web、统一响应和异常处理 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-cache` | 缓存抽象与 Redis 集成 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-event` | 领域事件、消息与 Outbox | 拟纳入 1.0 稳定范围 |
| `fast-ddd-search` | 搜索引擎集成 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-monitor` | 指标与 SQL 监控 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-security-starter` | JWT、认证、鉴权与安全扩展点 | 拟纳入 1.0 稳定范围 |
| `fast-ddd-ai-spring-boot-starter` | Spring AI 通用接入（Incubating） | Incubating——不纳入 1.0 API 冻结承诺 |
| `fast-ddd-analyzer-spring-boot-starter` | 数据结构分析（Incubating） | Incubating——不纳入 1.0 API 冻结承诺 |
| `fast-ddd-starter` | All-in-One Starter | 拟纳入 1.0 稳定范围 |

> **稳定性标注语义**（ECO-006 D1，2026-09-06）：「拟纳入 1.0 稳定范围」表示该模块计划进入
> 1.0 稳定承诺，但在 API 冻结评审通过前**不代表任何模块的 API 已冻结**；「Incubating」表示
> 实验能力，保留随版本调整的自由，重大不兼容变更将在发布说明中公告。逐模块冻结状态以
> 1.0 冻结评审与 GA 门禁的正式结论为准。

模块源码位于 [`fastddd-framework/`](fastddd-framework/)，统一版本入口为 [`fastddd-bom`](fastddd-bom/)。Experimental Agent Access 位于 [`agent-access/`](agent-access/)，拥有独立兼容承诺，不阻塞 Framework 1.0。

## 使用

业务项目建议先导入 BOM，再按需声明模块：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.gallop.fastddd</groupId>
      <artifactId>fastddd-bom</artifactId>
      <version>${fastddd.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

```xml
<dependency>
  <groupId>io.github.gallop.fastddd</groupId>
  <artifactId>fast-ddd-starter</artifactId>
</dependency>
```

## 构建与文档

```bash
./mvnw clean verify
```

- 文档入口：[`docs/README.md`](docs/README.md)
- 贡献说明：[`CONTRIBUTING.md`](CONTRIBUTING.md)
- 版本变更：[`CHANGELOG.md`](CHANGELOG.md)

## 许可证

FastDDD Framework Community 使用 [Apache License 2.0](LICENSE)。第三方归属说明见 [NOTICE](NOTICE)。FastDDD Studio Pro 是独立的专有产品，不因依赖 Community 制品而获得本许可证授权。
