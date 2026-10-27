# FastDDD Framework Community

![Version](https://img.shields.io/badge/version-1.1.3-blue) ![JDK](https://img.shields.io/badge/JDK-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen) ![License](https://img.shields.io/badge/license-Apache--2.0-green)

[English](README.md) | **简体中文**

FastDDD Framework Community 是面向 Java 21 与 Spring Boot 3.5 的 DDD 运行时基础框架。它提供通用领域模型、应用层契约、Web 与统一响应、安全认证、领域事件（含 Outbox）、缓存、搜索、监控和可选 AI 集成，不包含 FastDDD Studio Pro 的商业控制面能力。

> **当前状态：1.1.3**（2026-10-27，tag `v1.1.3`）——1.1.0 GA 线上的 patch：`fast-ddd-ai-spring-boot-starter` 结构化访谈提示词契约（v3d 双键+`ddd-interview` 模板对）。Maven 坐标 `io.github.gallop.fastddd`。1.0 稳定 API 承诺的 12 模块零变化；可选身份能力（四个 `fast-ddd-identity-*` 模块，默认关闭）纳入本线；`fast-ddd-ai-spring-boot-starter` 与 `fast-ddd-analyzer-spring-boot-starter` 保持 Incubating。

## 模块

| 模块 | 定位 | 1.0 稳定性标注 |
|---|---|---|
| `fast-ddd-common` | 通用类型、异常与领域基础能力 | 1.0 稳定 |
| `fast-ddd-config` | 配置与条件装配 | 1.0 稳定 |
| `fast-ddd-model` | 领域模型和持久化基类 | 1.0 稳定 |
| `fast-ddd-crud` | Repository、Mapper 与 CRUD 基础设施 | 1.0 稳定 |
| `fast-ddd-application` | Command、Query 与 Handler 契约 | 1.0 稳定 |
| `fast-ddd-web` | Web、统一响应和异常处理 | 1.0 稳定 |
| `fast-ddd-cache` | 缓存抽象与 Redis 集成 | 1.0 稳定 |
| `fast-ddd-event` | 领域事件、消息与 Outbox | 1.0 稳定 |
| `fast-ddd-search` | 搜索引擎集成 | 1.0 稳定 |
| `fast-ddd-monitor` | 指标与 SQL 监控 | 1.0 稳定 |
| `fast-ddd-security-starter` | JWT、认证、鉴权与安全扩展点 | 1.0 稳定 |
| `fast-ddd-ai-spring-boot-starter` | Spring AI 通用接入（Incubating） | Incubating——不纳入 1.0 API 冻结承诺 |
| `fast-ddd-analyzer-spring-boot-starter` | 数据结构分析（Incubating） | Incubating——不纳入 1.0 API 冻结承诺 |
| `fast-ddd-starter` | All-in-One Starter | 1.0 稳定 |

> **稳定性标注语义**：「1.0 稳定」表示该模块已随 GA 进入 1.0 稳定 API 承诺，以 1.0 API 冻结评审与 GA 门禁的正式结论为准；「Incubating」表示实验能力，保留随版本调整的自由，重大不兼容变更将在发布说明中公告。

模块源码位于 [`fastddd-framework/`](fastddd-framework/)，统一版本入口为 [`fastddd-bom`](fastddd-bom/)。Experimental Agent Access 位于 [`agent-access/`](agent-access/)，拥有独立兼容承诺，不阻塞 Framework 1.0。

## 环境要求

- **JDK 21** —— 硬性要求，构建门禁强制 `requireJavaVersion[21,)`
- **Maven 3.9+** —— wrapper 已 pin 3.9.5
- **Spring Boot 3.5.16** —— 由 BOM 统一管理

## 快速开始

> **制品解析**：FastDDD 制品按坐标 `io.github.gallop.fastddd` 从你配置的 Maven 仓库解析（Java 包名保持 `com.gallop.fastddd` 不变）。源码安装为两步：先 framework 仓，后 tooling 仓（插件依赖 `fastddd-bom`）：
>
> ```bash
> cd fastddd-framework && ./mvnw -B -Drevision=1.1.3 clean install -DskipTests
> cd ../fastddd-tooling  && ./mvnw -B -Drevision=1.1.3 clean install -DskipTests
> ```

**新项目——用配套工具链插件一键生成脚手架**（充血模型 + PostgreSQL + 两个限界上下文）：

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.1.3:init-project \
  -Dinit.groupId=com.example \
  -Dinit.artifactId=shop \
  -Dinit.package=com.example.shop \
  -Dinit.modelType=RICH \
  -Dinit.databaseType=postgresql \
  -Dinit.enableEvent=true \
  -Dinit.enableSecurity=true \
  -Dinit.contexts=order,product
```

**已有项目——先导入 BOM，再按需声明模块（不带版本号）：**

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

指南：[快速开始](docs/guides/QUICK_START.zh-CN.md) · [新业务系统开发规范](docs/guides/NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.zh-CN.md) · [升级指南](docs/upgrade/README.zh-CN.md) · [兼容矩阵](docs/COMPATIBILITY_MATRIX.zh-CN.md)

## 构建

```bash
./mvnw clean verify
```

发布（GA）版本构建使用 `-Drevision=1.1.3`；reactor 默认保持 `-SNAPSHOT`。

## 文档

- 文档入口：[`docs/README.zh-CN.md`](docs/README.zh-CN.md)
- 贡献说明：[`CONTRIBUTING.md`](CONTRIBUTING.md)
- 版本变更：[`CHANGELOG.zh-CN.md`](CHANGELOG.zh-CN.md)

## 生态

- **fastddd-tooling** —— 配套仓库：项目脚手架（`init-project`、`generate-code`）、代码/文档生成引擎、模板、Maven 插件。 <!-- 开源时补充公开仓库地址 -->
- **FastDDD Skills** —— 沉淀 FastDDD 最佳实践的 Claude 技能集：架构设计 → 项目初始化 → 开发模式 → 自动化开发循环。单独分发。 <!-- 分发地址待开源后补充 -->

## 许可证

FastDDD Framework Community 使用 [Apache License 2.0](LICENSE)。第三方归属说明见 [NOTICE](NOTICE)。FastDDD Studio Pro 是独立的专有产品，不因依赖 Community 制品而获得本许可证授权。
