# FastDDD Framework Community Documentation Index

**English** | [简体中文](README.zh-CN.md)

This directory is the single source of truth for the Framework Community's runtime, public API, upgrade, and release evidence. Tooling, Studio Pro, and cross-product commercial planning are not part of this repository's public documentation.

## Current Documentation

| Purpose | Entry |
|---|---|
| Quick start & module selection | [`guides/QUICK_START.md`](guides/QUICK_START.md), [`guides/USAGE_GUIDE.md`](guides/USAGE_GUIDE.md) |
| Public API & SPI | [`api/API_REFERENCE.md`](api/API_REFERENCE.md), [`guides/SPI_REFERENCE.md`](guides/SPI_REFERENCE.md) |
| DDD architecture | [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md), [`architecture/DDD_LAYERING_BEST_PRACTICES.md`](architecture/DDD_LAYERING_BEST_PRACTICES.md) |
| Security, caching, search & events | [`security/INTEGRATION_GUIDE.md`](security/INTEGRATION_GUIDE.md), [`security/AUDIT_LOG_TABLE_MIGRATION.md`](security/AUDIT_LOG_TABLE_MIGRATION.md), [`guides/CACHE_GUIDE.md`](guides/CACHE_GUIDE.md), [`guides/SEARCH_GUIDE.md`](guides/SEARCH_GUIDE.md), [`devops/OUTBOX_OPS_GUIDE.md`](devops/OUTBOX_OPS_GUIDE.md) |
| Compatibility & upgrades | [`COMPATIBILITY_MATRIX.md`](COMPATIBILITY_MATRIX.md), [`UPGRADE_GUIDE.md`](UPGRADE_GUIDE.md), [`ID_MIGRATION_GUIDE.md`](ID_MIGRATION_GUIDE.md) |
| Identity integration (Feishu) — planned | [`identity/README.md`](identity/README.md) — design docs for the optional identity modules (v0.2 current, v0.1 + review record historical); capability not implemented or released yet, sole maintenance location migrated from the Enterprise Content OS repo on 2026-09-24 |
| Release gates | Release-gate and verification records (RELEASE_CHECKLIST, TECHNICAL_FREEZE_CHECKLIST, OSS_001, QA_001/002, RC1_RELEASE_EVIDENCE) were moved to the private out-of-repository archive `fastddd-docs-internal` on 2026-09-12 and are not published with the open-source repository |
| Agent Access (Experimental) | [`../agent-access/README.md`](../agent-access/README.md) |
| Community/Studio product boundaries | [`PRODUCT_BOUNDARIES.md`](PRODUCT_BOUNDARIES.md) |

## Ownership Boundaries

- Tooling API, code generation, templates, FastDoc, Maven plugin, and scaffolding documentation take the `fastddd-tooling` repository as their source of truth;
- Studio's AI design, visual table design, unified generator, deployment, and commercial planning take the private `fastddd-studio-pro` repository as their source of truth;
- Studio AI / unified-generator / deployment material, full commercial ADRs, consumer-private checklists, and pre-split renovation copies were removed from this repository in ECO-004 and remain traceable through Git history or the private Studio source of truth;
- New documents must clearly belong to the Framework Community; otherwise they must not enter this repository.

## Historical Material Policy

Pre-split audits, renovation plans, and material already superseded by ADRs or execution records count as `historical / superseded`. Historical material may be used to trace background, but must not override the current POMs, public API, ADRs, or release checklists.

> Canonical owner: FastDDD Framework Community. Last updated: 2026-09-12 (release-gate material moved to a private out-of-repository archive).
