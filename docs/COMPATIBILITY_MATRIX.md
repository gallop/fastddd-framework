# 兼容矩阵（Compatibility Matrix）

> 本文档定义 FastDDD 各版本线的运行时基线、维护状态与变更边界，是业务系统选择/升级版本的依据。
> 升级步骤见 [UPGRADE_GUIDE.md](UPGRADE_GUIDE.md)；发布历史见 [../CHANGELOG.md](../CHANGELOG.md)。

## 版本线基线

| FastDDD | Java（构建+运行） | Spring Boot | Maven | 状态 |
|---------|-------------------|-------------|-------|------|
| 升级前开发快照 | 17 | 3.4.5 | 3.9.x | 🔒 仅 Git tag `baseline/jdk17-boot3.4.5` 归档，**不维护** |
| **1.0.x** | **21** | **3.5.x**（当前 3.5.16） | 3.9.x（wrapper pin 3.9.5） | ✅ **当前稳定线**（首个 GA 见 RELEASE_CHECKLIST） |
| 1.1.x | 21 | 3.5.x | 3.9.x | 📋 计划（兼容性增强：japicmp/Revapi、BOM 单一来源、模板切 BOM） |
| 2.0.x | 待定 | 4.x | 待定 | 🔭 未来规划（Spring Boot 4 / Spring Framework 7 / Jackson 3 迁移） |

## 关键依赖矩阵（1.0.x 线，经 `dependency:tree` 实证）

| 依赖 | 版本 | 来源 |
|------|------|------|
| Spring Framework | 6.2.19 | Spring Boot BOM |
| Spring AI | 1.1.8 | 显式 BOM import（Boot 3.5.x 对应 1.1 维护线） |
| MyBatis-Plus（starter/jsqlparser/generator） | 3.5.12 | 显式 dependencyManagement |
| MyBatis-Spring | 3.0.3 | 显式 dependencyManagement |
| Druid | 1.2.28（`druid-spring-boot-3-starter`） | 显式 dependencyManagement |
| MySQL 驱动 | 9.7.0（`com.mysql:mysql-connector-j`） | Spring Boot BOM |
| Micrometer（core/observation/registry-prometheus） | 1.15.12 | Spring Boot BOM |
| Jackson | 2.21.x | Spring Boot BOM |
| SpringDoc OpenAPI | 2.8.17 | 显式 dependencyManagement |
| Knife4j | 4.5.0 | 显式 dependencyManagement |
| SLF4J | 2.0.18 | Spring Boot BOM |
| Lombok | 1.18.46 | Spring Boot BOM |

> 业务项目 **不要覆盖** 上表版本（尤其 Spring Boot、Spring、Jackson）——它们经 FastDDD 与上游 BOM 联调验证，
> 覆盖可能引入版本分裂。如需统一管理，`import` [`fastddd-bom`](../README.md) 即可。

## 变更边界（语义化版本约定）

为避免"把破坏性变更当作普通 minor 升级"，FastDDD 对各版本线变更边界作如下承诺：

| 变更类型 | 所属版本线 | 含义 |
|----------|------------|------|
| **PATCH（x.y.Z）** | 同一 minor 线 | 仅 bug 修复、依赖补丁升级（如 Boot 3.5.16 → 3.5.17）、文档。**不引入任何 API 破坏**。 |
| **MINOR（x.Y.0）** | 同一 major 线 | 新增功能、新增模块、依赖 minor 升级。**保持向后二进制兼容**（1.0.1 起由 japicmp/Revapi 校验）。 |
| **MAJOR（X.0.0）** | 跨 major 线 | 允许破坏性变更（如 Spring Boot 4 / Spring 7 / Jackson 3 迁移）。**不作为普通 minor 推送**，需配套专门升级指南。 |

### 不允许的做法

- ❌ 在 PATCH/MINOR 中升级 **Java 主版本**（Java 21 → 22+）。
- ❌ 在 PATCH/MINOR 中升级 **Spring Boot 主/次版本**（3.5.x → 3.6.x / 4.x）。
- ❌ 在 1.0.x 线引入 Spring Boot 4 / Spring Framework 7 / Jackson 3 改动。
- ❌ 在 PATCH/MINOR 中删除或更改公开 API 签名（1.0.1 起由二进制兼容校验强制）。

### 允许的做法

- ✅ PATCH：依赖补丁升级、bug 修复、性能优化、文档。
- ✅ MINOR：新增模块/功能、新增可选依赖、依赖 minor 升级（经联调验证）。

## 构建与运行要求

| 项 | 要求 |
|----|------|
| 构建 JDK | **21+**（硬前置；`<release>21`，产物 major version 65） |
| 运行 JRE | **21+**（与构建一致） |
| Maven | **3.9.0+**（Enforcer 强制；wrapper pin 3.9.5） |
| Enforcer 门禁 | `requireJavaVersion[21,)` · `requireMavenVersion[3.9.0,4.0.0)` · `requirePluginVersions` · `banDuplicatePomDependencyVersions` |

> ⚠️ **JDK 21 硬前置**：若机器默认 `JAVA_HOME` 指向 JDK 17，Maven 会报"不支持的发行版本 21"直接 BUILD FAILURE。
> 构建前须显式切换至 JDK 21（CI runner 同理）。
