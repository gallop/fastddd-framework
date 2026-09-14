# Business-System Upgrade Entry Point (for Claude Code)

**English** | [简体中文](README.zh-CN.md)

> **Purpose**: This is the single entry point for upgrading an existing business system to FastDDD 1.0. Do not traverse the whole `docs/` directory first, and do not treat the Framework's renovation records as an execution manual for business systems.
>
> **Current official version (two independent axes)**: Framework `io.github.gallop.fastddd:fastddd-bom:1.0.0` (JDK 21, Spring Boot 3.5.16, internal release)
> and Tooling `io.github.gallop.fastddd:fastddd-maven-plugin:1.0.2` (init-project plugin + templates, separate repository/axis). Never mix the two axes'
> versions into each other's coordinates; business-project dependencies use only the Framework axis.
> From 1.0.0 on, the Maven groupId migrated from `com.gallop.fastddd` to `io.github.gallop.fastddd` (Java package names unchanged);
> upgrades must read [UPGRADE_GUIDE §3 coordinate migration](../UPGRADE_GUIDE.md#3-fastddd-maven-coordinate-migration-100-ga)
> and the anti-mixing gates. Business systems must not be reverted to `1.0.0-jdk21-SNAPSHOT` or the old group coordinates.

## Read These 3 Documents First

Read in the following order to start upgrading an ordinary business system:

1. [Upgrade Guide](../UPGRADE_GUIDE.md): standard operations for POM, JDK, configuration, build, and startup.
2. [Compatibility Matrix](../COMPATIBILITY_MATRIX.md): confirm the single supported combination of JDK, Boot, and Framework versions and the BOM boundary.
3. This document: the execution constraints, acceptance gates, and Claude Code working hints below.

Do **not** read the following by default:

- The Framework's own pre-split renovation tasks and specific business-pilot records have been moved out of the Community's current documentation; they are not consumer upgrade manuals.
- `RC1_RELEASE_EVIDENCE.md`, `QA_*.md`, `OSS_*.md`: release evidence / audit records, not operational steps (moved to a private out-of-repository archive on 2026-09-12).
- `FASTDDD_REFERENCE.md`: the full API reference; consult it on demand only when code genuinely needs a specific API.

## Read Further by Feature (Do Not Load Indiscriminately)

| Capability used by the business system | Additional document to read |
|---|---|
| `BasePO`, `IService`, `BaseServiceImpl`, `BaseController` generic/ID compile errors | [ID Migration Guide](../ID_MIGRATION_GUIDE.md) (the internal migration checklist was moved to a private out-of-repository archive) |
| Security, JWT, permission SPI | [Security Integration Guide](../security/INTEGRATION_GUIDE.md) |
| Redis/Caffeine caching | [Cache Guide](../guides/CACHE_GUIDE.md) |
| Transactional events, Outbox, RocketMQ | [Outbox Ops Guide](../devops/OUTBOX_OPS_GUIDE.md) |
| CQRS | [CQRS Guide](../guides/CQRS_GUIDE.md) |
| Elasticsearch/OpenSearch | [Search Guide](../guides/SEARCH_GUIDE.md) |
| Many external dependencies, gray/UAT rollout, complex systems | This guide + the business project's own private gray/UAT plan |

## Claude Code Execution Constraints

Hand the following, together with the target business system's path, to Claude Code. It should first create an independent branch in the business repository before executing the upgrade; it must not modify the Framework, Tooling, or Studio repositories to dodge business-system compile errors.

```text
Please upgrade the current business system to FastDDD 1.0 (internal official release 1.0.0).

Required reading (in order):
1. <fastddd-framework>/docs/upgrade/README.md
2. <fastddd-framework>/docs/UPGRADE_GUIDE.md (§3 coordinate migration + anti-mixing gates are mandatory)
3. <fastddd-framework>/docs/COMPATIBILITY_MATRIX.md

Target versions: JDK 21, Spring Boot 3.5.16,
io.github.gallop.fastddd:fastddd-bom:1.0.0.

Working rules:
- Create a dedicated upgrade branch in the business repository; first record the current branch/commit, module structure, startup module, databases, and external dependencies.
- Import fastddd-bom; write no version on fast-ddd-* dependencies; do not introduce Tooling/Studio runtime dependencies, and do not override platform versions such as Spring, Jackson, or MyBatis.
- Coordinate migration follows the UPGRADE_GUIDE §3.3 minimal pattern: replace only the POM's groupId and version literals; do not touch Java sources or imports (package names com.gallop.fastddd.* are kept).
- Enable the §3.5 Maven Enforcer anti-mixing gates; the build must pass; do not fall back to SNAPSHOT or the old group coordinates com.gallop.fastddd.
- If this machine's local repository lacks the 1.0.0 artifacts, configure the internal release repository (file://) per UPGRADE_GUIDE §3.4 to resolve them; the artifacts are not on public channels.
- Load supplementary documents only when related code/configuration appears, following the "read further by feature" table in this entry point.
- Tracked configuration may keep only environment-variable placeholders; real passwords, JWT secrets, LLM keys, RSA private keys, etc. go only into ignored local .env files or environment variables — never into logs, documents, or commits.
- Complete in sequence: clean compile; clean test/verify; dependency-tree review; startup from the real startup module; port listening and /actuator/health=UP; regression of key business chains.
- Verify real databases, caches, MQ, search, LLM, etc. according to the capabilities the project actually enables; mark unenabled capabilities "not applicable" with justification — never fake a pass.
- On completion, review the code, fix issues, and make one Conventional Commit with a Chinese-language message; report changes, commands, results, uncovered items, and the rollback point.
```

## Minimal Definition of Done

- [ ] JDK 21 and Spring Boot 3.5.16 in effect; the BOM resolves to `io.github.gallop.fastddd:*:1.0.0`; the dependency tree and final packages contain no dual FastDDD coordinates (zero hits for the old group `com.gallop.fastddd`, except `fastddd-studio*`); Enforcer gates pass.
- [ ] Full compilation and default tests pass; failing tests must be fixed or explicitly marked as external-environment prerequisites, never silently skipped.
- [ ] Ignored development configuration is loaded from the real startup module; `/actuator/health` returns `UP`.
- [ ] The key paths the system actually enables — databases, authentication, caching, events/messaging, search, third-party services — have been regression-tested.
- [ ] A pre-upgrade rollback point, the upgrade commit, and verification records exist; no real credentials entered Git, logs, or documents.

## Document Hierarchy

`docs/` keeps the current design, integration, and upgrade documentation; internal governance and evolution-process documents were moved to the private out-of-repository archive `fastddd-docs-internal` on 2026-09-12 (not published with the open-source repository, traceable). New business-system upgrades enter only through this directory.
