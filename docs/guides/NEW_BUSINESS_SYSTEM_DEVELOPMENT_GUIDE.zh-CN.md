# FastDDD 新业务系统开发规范

[English](NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md) | **简体中文**

> 面向新建业务系统与 AI 编程助手的最小开发规范。既有系统升级请使用
> [业务系统升级入口](../upgrade/README.zh-CN.md)，不要混用两套流程。
>
> 当前基线：**JDK 21、Spring Boot 3.5.16、FastDDD `io.github.gallop.fastddd:1.0.0`**
> （内部 v1.0 正式发布，2026-09-10，tag `v1.0.0`，经内部 release 仓部署）。

## 1. 必读与按需参考

新项目先阅读本文；开发中按问题检索 [Framework 完整参考](../FASTDDD_REFERENCE.zh-CN.md)。仅在实际使用时追加：

| 能力 | 文档 |
|---|---|
| Security/JWT/权限 SPI | [安全集成指南](../security/INTEGRATION_GUIDE.md) |
| 缓存 | [缓存指南](CACHE_GUIDE.zh-CN.md) |
| 事件/Outbox/RocketMQ | [Outbox 运维指南](../devops/OUTBOX_OPS_GUIDE.md) |
| CQRS/充血模型 | [CQRS 指南](CQRS_GUIDE.zh-CN.md) |
| 搜索 | [搜索指南](SEARCH_GUIDE.zh-CN.md) |

## 2. 基线与依赖

业务项目必须 import BOM；只引入实际使用的模块，所有 `fast-ddd-*` 依赖不写版本。Tooling 的 Maven 插件是可选构建工具，Studio Pro 不是业务运行时依赖。

```xml
<properties>
  <java.version>21</java.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.gallop.fastddd</groupId>
      <artifactId>fastddd-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
  </dependency>
</dependencies>
```

不要覆盖 Spring Boot、Spring、Jackson、MyBatis-Plus、Micrometer 等 BOM 已管理的版本。`1.0.0` 制品须从已配置的内部 release 仓解析，或以固定 `-Drevision=1.0.0` 从 Framework 根目录安装；不得改用 SNAPSHOT 或 `-jdk21-rc1` 冒充正式版。

## 3. 代码与分层规则

- 简单配置/后台 CRUD：使用 `PO → Mapper → Service → Controller`；实体以 `BasePO<ID>` 为基类。
- 复杂核心业务：使用 `domain → application → infrastructure → interfaces`；领域对象不直接依赖 Web、Mapper 或数据库配置。
- `ID` 必须贯穿 `BasePO<ID>`、`IService<T, ID>`、`BaseServiceImpl<M, T, ID>` 与 Controller；BIGINT 默认选 `Long`，不得使用原始泛型或强转掩盖类型错误。
- 普通后台 CRUD 优先 `BaseAdminCrudController<S,T,ID>`；只读资源使用 `BaseReadOnlyController<S,T,ID>`；资金、审批、跨聚合写操作等敏感 API 显式定义端点和权限，不继承通用写端点。
- API 的请求/响应使用 DTO，不直接把 PO 作为对外契约；异常使用 `BusinessException`/`SystemException`，统一响应使用 `ApiResponse`。
- 一个表是否继承 `VersionedPO` 取决于是否真实存在 `version` 列；不得为了“通用”给所有表加乐观锁字段。

## 4. 配置与安全

- 受 Git 跟踪的 `application*.yml` 只允许环境变量占位符，例如 `${JWT_SECRET}`；提交无敏感值的 `.env.example`，实际 `.env` 必须被忽略。
- 密码、JWT、LLM Key、RSA 私钥不得进入 Git、日志、文档、测试断言或生成命令。HS512 的 `JWT_SECRET` 至少 64 个 UTF-8 字节。
- YAML 顶层键必须唯一；Boot 3.5 会拒绝重复键。
- 启用 Security 时，最小化 `permit-all-paths`；不要使用宽泛的 `/api/auth/**`。所有写端点须明确鉴权责任。
- Redis、MQ、搜索、LLM 等均为可选能力；未启用时不得保留无效连接配置，也不得把本地替代服务当成生产验收。

## 5. 新项目最小验证

```bash
./mvnw -B clean compile
./mvnw -B clean test
./mvnw -B clean package -DskipTests
```

交付前至少证明：依赖树只有一个 FastDDD 版本；从实际启动模块加载本地忽略配置；端口已监听且 `/actuator/health` 返回 `UP`；数据库 CRUD、认证授权及项目实际启用的缓存/事件/消息/搜索/第三方服务关键路径通过。集成测试使用隔离环境和显式 profile，不能通过跳过失败测试宣称完成。

## 6. 给 Claude Code 的工作约束

```text
新建 FastDDD 业务系统时，先读 NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md；
仅按问题检索 FASTDDD_REFERENCE.md 和相应专题文档。使用 JDK 21、Boot 3.5.16、
fastddd-bom:1.0.0。所有 fast-ddd-* 不写版本；不引入 Tooling/Studio 运行时依赖；
不写入真实凭据。完成代码后执行编译、测试、启动健康检查和实际启用能力的回归，review 后以 Conventional Commit + 中文说明提交。
```
