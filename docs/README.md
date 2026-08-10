# FastDDD 文档索引

本目录包含 FastDDD 框架的全部技术文档，按分类组织。

## 目录结构

```
docs/
├── architecture/    架构设计文档
├── guides/          使用指南与规范
├── security/        安全模块文档
├── ai/              AI 智能分析文档
├── devops/          运维部署文档
└── api/             API 参考文档
```

---

## 架构设计 [architecture/](architecture/)

| 文档 | 说明 |
|------|------|
| [双轨并行架构](architecture/ARCHITECTURE.md) | 贫血模型与充血模型双轨并存的 DDD 架构设计，支持渐进式 DDD 落地 |
| [DDD 分层最佳实践](architecture/DDD_LAYERING_BEST_PRACTICES.md) | DDD 四层职责划分、依赖规则、反模式识别与最佳实践 |
| [充血模型示例](architecture/RICH_MODEL_EXAMPLES.md) | 以订单聚合根为例，展示完整的充血模型实现（领域层/应用层/基础设施层） |
| [贫血→充血迁移指南](architecture/MIGRATION_GUIDE.md) | 从 PO+Service 贫血模型渐进迁移到 Entity+ApplicationService+CQRS 充血模型的实操指南 |
| [系统技术架构文档](architecture/TECHNICAL_ARCHITECTURE.md) | FastDDD 与 Spring 生态整合的完整技术架构说明 |

## 使用指南 [guides/](guides/)

| 文档 | 说明 |
|------|------|
| [快速开始](guides/QUICK_START.md) | 5 分钟快速上手，覆盖贫血模型和充血模型两种开发模式 |
| [框架使用指南](guides/USAGE_GUIDE.md) | 企业级 DDD 框架完整使用手册，包含依赖配置、模块说明、配置参考 |
| [CQRS 指南](guides/CQRS_GUIDE.md) | 基于 application 模块的 CQRS 实现：Command、Query、Handler 用法 |
| [编码规范](guides/CODING_STANDARDS.md) | 统一的 Java 编码规范和命名约定 |
| [Maven Profiles](guides/MAVEN_PROFILES.md) | Maven 构建配置文件说明（dev/test/prod） |
| [产品需求文档 (PRD)](guides/PRD.md) | FastDDD 产品定位与核心需求：90%+ 代码一致性、5 分钟单表 CRUD |
| [功能路线图](guides/FEATURE_ROADMAP.md) | 功能规划与优先级排序，目标是将单表 CRUD 开发从 2 天缩短至 5 分钟 |

## 安全模块 [security/](security/)

| 文档 | 说明 |
|------|------|
| [安全模块改造计划](security/RENOVATION_PLAN.md) | Security 模块四阶段改造方案：模板/实体对齐、SPI 架构、可扩展安全策略 |
| [安全模块改造总结](security/RENOVATION_SUMMARY.md) | 2026-04-19 完成的安全模块改造执行总结，涉及 security-starter、模板、前后端 |

## AI 智能分析 [ai/](ai/)

| 文档 | 说明 |
|------|------|
| [AI 智能化改造计划 v2.0](ai/INTELLIGENT_ANALYSIS_PLAN.md) | 一体化生成器 AI 智能化改造：规则引擎优先、LLM 辅助、聚合根识别 |
| [AI 分析任务清单](ai/ANALYSIS_TASK_LIST.md) | AI 智能分析功能的可执行任务拆解，含优先级、依赖关系和验收标准 |
| [建表功能任务](ai/TABLE_DESIGN_TASKS.md) | 可视化建表功能的开发任务文档 |

## 运维部署 [devops/](devops/)

| 文档 | 说明 |
|------|------|
| [Docker 部署指南](devops/DOCKER.md) | 开发与生产环境的 Docker 部署方案 |
| [SSE 最佳实践](devops/SSE_BEST_PRACTICES.md) | Spring Boot + Vue 3 的 SSE 实时通信最佳实践，涵盖流式输出与断线重连 |
| [Jackson 配置规范](devops/JACKSON_CONFIG.md) | 基于 生产环境 踩坑经验总结的 Jackson 配置开发规范 |
| [Jackson 配置排查清单](devops/JACKSON_CHECKLIST.md) | Jackson 配置问题的快速排查清单 |

## API 参考 [api/](api/)

| 文档 | 说明 |
|------|------|
| [框架 API 参考](api/API_REFERENCE.md) | FastDDD 各层（应用层/领域层/基础设施层/接口层/事件层）的 API 参考 |
| [一体化生成器评审](api/UNIFIED_GENERATOR_REVIEW.md) | 一体化代码生成器的代码评审报告，分析框架模块与生成器的集成情况 |

## 版本治理与发布

| 文档 | 说明 |
|------|------|
| [框架完整参考](FASTDDD_REFERENCE.md) | FastDDD 完整 API 参考，可复制到业务项目 CLAUDE.md 使用 |
| [SPI 参考手册](guides/SPI_REFERENCE.md) | 所有 SPI 接口的定义、替换方式、默认行为与示例 |
| [兼容矩阵](COMPATIBILITY_MATRIX.md) | 各版本线运行时基线、维护状态与变更边界（PATCH/MINOR/MAJOR 约定） |
| [升级指南](UPGRADE_GUIDE.md) | 业务系统从旧基线升级到 JDK 21 / Boot 3.5.16 的步骤（对接 UPG-011） |
| [发布检查清单](RELEASE_CHECKLIST.md) | 1.0.0 及后续 GA 版本的发布门禁项 |
| [变更日志](../CHANGELOG.md) | 全部显著变更（Keep a Changelog 格式） |
| [JDK21/Boot3.5 升级方案](architecture/JDK21_SPRING_BOOT_3_5_UPGRADE_PLAN.md) | 升级总方案与 UPG-000～012 执行记录 |

---

## 项目记录

| 文档 | 说明 |
|------|------|
| [项目改造记录](MIGRATION_NOTES.md) | 从旧仓库迁移到三层架构的完整过程：变更对照、改动统计、文档整理、已知技术债 |

---

> 文档最后更新：2026-08-10
