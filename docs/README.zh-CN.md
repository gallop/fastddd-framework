# FastDDD Framework Community 文档索引

[English](README.md) | **简体中文**

本目录只以 Framework Community 的运行时、公共 API、升级和发布证据为现行事实源。Tooling、Studio Pro 与跨产品商业规划不属于本仓公共文档事实。

## 现行文档

| 目标 | 入口 |
|---|---|
| 快速接入与模块选择 | [`guides/QUICK_START.zh-CN.md`](guides/QUICK_START.zh-CN.md)、[`guides/USAGE_GUIDE.zh-CN.md`](guides/USAGE_GUIDE.zh-CN.md) |
| 公共 API 与 SPI | [`api/API_REFERENCE.zh-CN.md`](api/API_REFERENCE.zh-CN.md)、[`guides/SPI_REFERENCE.zh-CN.md`](guides/SPI_REFERENCE.zh-CN.md) |
| DDD 架构 | [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md)、[`architecture/DDD_LAYERING_BEST_PRACTICES.md`](architecture/DDD_LAYERING_BEST_PRACTICES.md) |
| 安全、缓存、搜索与事件 | [`security/INTEGRATION_GUIDE.md`](security/INTEGRATION_GUIDE.md)、[`security/AUDIT_LOG_TABLE_MIGRATION.md`](security/AUDIT_LOG_TABLE_MIGRATION.md)、[`guides/CACHE_GUIDE.zh-CN.md`](guides/CACHE_GUIDE.zh-CN.md)、[`guides/SEARCH_GUIDE.zh-CN.md`](guides/SEARCH_GUIDE.zh-CN.md)、[`devops/OUTBOX_OPS_GUIDE.md`](devops/OUTBOX_OPS_GUIDE.md) |
| 兼容与升级 | [`COMPATIBILITY_MATRIX.zh-CN.md`](COMPATIBILITY_MATRIX.zh-CN.md)、[`UPGRADE_GUIDE.zh-CN.md`](UPGRADE_GUIDE.zh-CN.md)、[`ID_MIGRATION_GUIDE.zh-CN.md`](ID_MIGRATION_GUIDE.zh-CN.md) |
| 身份集成（飞书）——规划中 | [`identity/README.md`](identity/README.md)——可选身份模块设计文档（v0.2 为当前方案，v0.1 与评审记录为历史）；能力尚未实现、尚未发布；2026-09-24 自 Enterprise Content OS 仓库迁入本目录为唯一维护位置 |
| 发布门禁 | 发布门禁与验证记录（RELEASE_CHECKLIST、TECHNICAL_FREEZE_CHECKLIST、OSS_001、QA_001/002、RC1_RELEASE_EVIDENCE）已于 2026-09-12 移至仓外私有档案 `fastddd-docs-internal`，不随开源仓发布 |
| Agent Access（Experimental） | [`../agent-access/README.md`](../agent-access/README.md) |
| Community/Studio 产品边界 | [`PRODUCT_BOUNDARIES.md`](PRODUCT_BOUNDARIES.md) |

## 归属边界

- Tooling API、代码生成、模板、FastDoc、Maven 插件和脚手架文档以 `fastddd-tooling` 为事实源；
- Studio 的 AI 设计、可视化建表、统一生成器、部署与商业规划以私有 `fastddd-studio-pro` 为事实源；
- Studio AI/统一生成器/部署材料、完整商业 ADR、消费者私有清单和拆仓前综合整改副本已在 ECO-004 从本仓移除，可从 Git 历史或私有 Studio 事实源追溯；
- 新增文档必须明确属于 Framework Community，否则不得进入本仓。

## 历史材料规则

拆仓前审计、整改计划和已经被 ADR/执行记录替代的材料属于 `historical / superseded`。历史材料可以用于追溯背景，不能覆盖现行 POM、公共 API、ADR 和发布检查清单。

> Canonical owner：FastDDD Framework Community。最后更新：2026-09-12（发布门禁材料移至仓外私有档案）。
