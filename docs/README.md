# FastDDD Framework Community 文档索引

本目录只以 Framework Community 的运行时、公共 API、升级和发布证据为现行事实源。Tooling、Studio Pro 与跨产品商业规划不属于本仓公共文档事实。

## 现行文档

| 目标 | 入口 |
|---|---|
| 快速接入与模块选择 | [`guides/QUICK_START.md`](guides/QUICK_START.md)、[`guides/USAGE_GUIDE.md`](guides/USAGE_GUIDE.md) |
| 公共 API 与 SPI | [`api/API_REFERENCE.md`](api/API_REFERENCE.md)、[`guides/SPI_REFERENCE.md`](guides/SPI_REFERENCE.md) |
| DDD 架构 | [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md)、[`architecture/DDD_LAYERING_BEST_PRACTICES.md`](architecture/DDD_LAYERING_BEST_PRACTICES.md) |
| 安全、缓存、搜索与事件 | [`security/INTEGRATION_GUIDE.md`](security/INTEGRATION_GUIDE.md)、[`security/AUDIT_LOG_TABLE_MIGRATION.md`](security/AUDIT_LOG_TABLE_MIGRATION.md)、[`guides/CACHE_GUIDE.md`](guides/CACHE_GUIDE.md)、[`guides/SEARCH_GUIDE.md`](guides/SEARCH_GUIDE.md)、[`devops/OUTBOX_OPS_GUIDE.md`](devops/OUTBOX_OPS_GUIDE.md) |
| 兼容与升级 | [`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md)、[`UPGRADE_GUIDE.md`](UPGRADE_GUIDE.md)、[`ID_MIGRATION_GUIDE.md`](ID_MIGRATION_GUIDE.md) |
| 发布门禁 | [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md)、[`OSS_001_READINESS_RECORD.md`](OSS_001_READINESS_RECORD.md)、[`QA_001_CONSUMER_MATRIX.md`](QA_001_CONSUMER_MATRIX.md)、[`RC1_RELEASE_EVIDENCE.md`](RC1_RELEASE_EVIDENCE.md)（历史 RC 记录，附 ECO-006D1 纠偏注记）、[`QA_002_VALIDATION_RECORD.md`](QA_002_VALIDATION_RECORD.md)（历史验证记录） |
| Agent Access（Experimental） | [`../agent-access/README.md`](../agent-access/README.md) |
| Community/Studio 产品边界 | [`PRODUCT_BOUNDARIES.md`](PRODUCT_BOUNDARIES.md) |

## 归属边界

- Tooling API、代码生成、模板、FastDoc、Maven 插件和脚手架文档以 `fastddd-tooling` 为事实源；
- Studio 的 AI 设计、可视化建表、统一生成器、部署与商业规划以私有 `fastddd-studio-pro` 为事实源；
- Studio AI/统一生成器/部署材料、完整商业 ADR、消费者私有清单和拆仓前综合整改副本已在 ECO-004 从本仓移除，可从 Git 历史或私有 Studio 事实源追溯；
- 新增文档必须明确属于 Framework Community，否则不得进入本仓。

## 历史材料规则

拆仓前审计、整改计划和已经被 ADR/执行记录替代的材料属于 `historical / superseded`。历史材料可以用于追溯背景，不能覆盖现行 POM、公共 API、ADR 和发布检查清单。

> Canonical owner：FastDDD Framework Community。最后更新：2026-09-07（ECO-006D1 补发布门禁文档入口）。
