# 业务系统升级入口（供 Claude Code 使用）

> **用途**：这是升级既有业务系统到 FastDDD 1.0 的唯一入口。不要先遍历整个 `docs/`，也不要把 Framework 的整改记录当作业务系统的执行手册。
>
> **当前候选版本**：`io.github.gallop.fastddd:fastddd-bom:1.0.0-jdk21-rc1`（JDK 21、Spring Boot 3.5.16）。它已是固定 RC 坐标；不得把业务系统改回 `1.0.0-jdk21-SNAPSHOT`。

## 先读这 3 份文档

按下列顺序阅读即可开始一个普通业务系统的升级：

1. [升级指南](../UPGRADE_GUIDE.md)：POM、JDK、配置、构建和启动的标准操作。
2. [兼容矩阵](../COMPATIBILITY_MATRIX.md)：确认 JDK、Boot、Framework 的唯一版本组合与 BOM 边界。
3. 本文：下面的执行约束、验收门禁与 Claude Code 工作提示。

**不要默认阅读**下列文档：

- Framework 自身拆仓前整改任务和具体业务试点记录已迁出 Community 现行文档，不是消费者升级手册。
- `RC1_RELEASE_EVIDENCE.md`、`QA_*.md`、`OSS_*.md`：发布证据/审计记录，不是操作步骤。
- `FASTDDD_REFERENCE.md`：完整 API 参考；仅在代码确实需要查询某个 API 时按需检索。

## 按功能再读（不要无差别加载）

| 业务系统使用的能力 | 追加阅读的文档 |
|---|---|
| `BasePO`、`IService`、`BaseServiceImpl`、`BaseController` 编译报泛型/ID 问题 | [ID 类型迁移清单](../architecture/ID_TYPE_MIGRATION_CHECKLIST.md) |
| Security、JWT、权限 SPI | [安全集成指南](../security/INTEGRATION_GUIDE.md) |
| Redis/Caffeine 缓存 | [缓存指南](../guides/CACHE_GUIDE.md) |
| 事务事件、Outbox、RocketMQ | [Outbox 运维指南](../devops/OUTBOX_OPS_GUIDE.md) |
| CQRS | [CQRS 指南](../guides/CQRS_GUIDE.md) |
| Elasticsearch/OpenSearch | [搜索指南](../guides/SEARCH_GUIDE.md) |
| 多外部依赖、灰度/UAT、复杂系统 | 本指南 + 业务项目自己的私有灰度/UAT 方案 |

## Claude Code 执行约束

将以下内容连同目标业务系统路径交给 Claude Code。它应先在业务仓库建立独立分支，再执行升级；不得修改 Framework、Tooling 或 Studio 仓库来规避业务系统编译错误。

```text
请将当前业务系统升级到 FastDDD 1.0 RC1。

必读文档（按顺序）：
1. <fastddd-framework>/docs/upgrade/README.md
2. <fastddd-framework>/docs/UPGRADE_GUIDE.md
3. <fastddd-framework>/docs/COMPATIBILITY_MATRIX.md

目标版本：JDK 21、Spring Boot 3.5.16、
io.github.gallop.fastddd:fastddd-bom:1.0.0-jdk21-rc1。

工作规则：
- 在业务仓库创建独立升级分支；先记录当前分支/提交、模块结构、启动模块、数据库与外部依赖。
- import fastddd-bom；所有 fast-ddd-* 依赖不写 version；不要引入 Tooling/Studio 运行时依赖，也不要覆盖 Spring、Jackson、MyBatis 等平台版本。
- 若该机器没有 RC1 制品，先从 Framework 的 RC1 来源以固定 revision 安装到 Maven 本地仓库，或从已配置的私服解析；不得退回 SNAPSHOT。
- 只在出现相关代码/配置时，按本入口“按功能再读”的表格加载补充文档。
- 受跟踪配置只能保留环境变量占位符；真实密码、JWT、LLM Key、RSA 私钥等仅放入被忽略的本地 .env 或环境变量，严禁输出到日志、文档和提交。
- 依次完成：clean compile、clean test/verify、依赖树审查、从实际启动模块启动、端口监听和 /actuator/health=UP、关键业务链路回归。
- 真实数据库、缓存、MQ、搜索、LLM 等按项目实际启用能力验证；没有的能力标记“不适用”并说明依据，不伪造通过。
- 完成后进行代码 review，修复问题，提交一条 Conventional Commit + 中文说明的提交；报告改动、命令、结果、未覆盖项、回滚点。
```

## 最小完成定义

- [ ] JDK 21 与 Spring Boot 3.5.16 生效，BOM 解析为 `1.0.0-jdk21-rc1`，依赖树不存在两套 FastDDD 版本。
- [ ] 全量编译与默认测试通过；失败测试必须修复或明确标为外部环境前置，不能静默跳过。
- [ ] 从真实启动模块加载被忽略的开发配置，`/actuator/health` 返回 `UP`。
- [ ] 已回归系统实际启用的数据库、认证、缓存、事件/消息、搜索、第三方服务等关键路径。
- [ ] 有升级前回滚点、升级提交和验证记录；无真实凭据进入 Git、日志或文档。

## 文档层级

`docs/` 保留完整的设计、审计和发布证据，便于追溯；新业务系统升级只从本目录进入。历史文档暂不移动，避免打断已有链接和外部引用。
