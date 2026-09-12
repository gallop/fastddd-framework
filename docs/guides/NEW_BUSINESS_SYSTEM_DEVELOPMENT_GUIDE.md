# FastDDD New Business System Development Guide

**English** | [简体中文](NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.zh-CN.md)

> A minimal development specification for new business systems and AI coding assistants. To upgrade
> an existing system, use the [Business System Upgrade entry point](../upgrade/README.md) instead;
> do not mix the two workflows.
>
> Current baseline: **JDK 21, Spring Boot 3.5.16, FastDDD `io.github.gallop.fastddd:1.0.0`**
> (internal v1.0 GA, 2026-09-10, tag `v1.0.0`, deployed via the internal release repository).

## 1. Must-Read and On-Demand References

New projects should read this document first; during development, look things up in the
[complete Framework reference](../FASTDDD_REFERENCE.md) as questions arise. Add the rest only when actually used:

| Capability | Document |
|---|---|
| Security/JWT/permission SPI | [Security Integration Guide](../security/INTEGRATION_GUIDE.md) |
| Caching | [Cache Guide](CACHE_GUIDE.md) |
| Events/Outbox/RocketMQ | [Outbox Operations Guide](../devops/OUTBOX_OPS_GUIDE.md) |
| CQRS/rich model | [CQRS Guide](CQRS_GUIDE.md) |
| Search | [Search Guide](SEARCH_GUIDE.md) |

## 2. Baseline and Dependencies

Business projects must import the BOM; include only the modules actually used, and never write versions on `fast-ddd-*` dependencies. The Tooling Maven plugins are optional build tools, and Studio Pro is not a business runtime dependency.

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

Do not override the versions the BOM already manages for Spring Boot, Spring, Jackson, MyBatis-Plus, Micrometer, and the like. The `1.0.0` artifacts must be resolved from the configured internal release repository, or installed from the Framework root with a fixed `-Drevision=1.0.0`; do not pass off a SNAPSHOT or `-jdk21-rc1` as the official release.

## 3. Code and Layering Rules

- Simple configuration / back-office CRUD: use `PO → Mapper → Service → Controller`; entities extend `BasePO<ID>` as their base class.
- Complex core business: use `domain → application → infrastructure → interfaces`; domain objects must not depend directly on Web, Mapper, or database configuration.
- The `ID` type must flow consistently through `BasePO<ID>`, `IService<T, ID>`, `BaseServiceImpl<M, T, ID>`, and the Controller; default to `Long` for BIGINT, and never use raw generics or casts to paper over type errors.
- Prefer `BaseAdminCrudController<S,T,ID>` for ordinary back-office CRUD; use `BaseReadOnlyController<S,T,ID>` for read-only resources; sensitive APIs — funds, approvals, cross-aggregate writes, and the like — must define endpoints and permissions explicitly instead of inheriting the generic write endpoints.
- Use DTOs for API requests/responses; never expose a PO directly as the external contract; use `BusinessException`/`SystemException` for exceptions and `ApiResponse` as the unified response.
- Whether a table extends `VersionedPO` depends on whether a `version` column actually exists; do not add optimistic lock columns to every table just for "generality".

## 4. Configuration and Security

- `application*.yml` files tracked by Git may contain only environment variable placeholders, such as `${JWT_SECRET}`; commit a `.env.example` without sensitive values, and the real `.env` must be ignored.
- Passwords, JWTs, LLM keys, and RSA private keys must never enter Git, logs, documents, test assertions, or generator commands. An HS512 `JWT_SECRET` must be at least 64 UTF-8 bytes.
- Top-level YAML keys must be unique; Boot 3.5 rejects duplicate keys.
- When Security is enabled, keep `permit-all-paths` to a minimum; do not use a broad pattern like `/api/auth/**`. Every write endpoint must have a clearly assigned authorization responsibility.
- Redis, MQ, search, LLM, and similar are optional capabilities; when one is not enabled, do not leave dead connection configuration behind, and do not treat a local stand-in service as a production acceptance test.

## 5. Minimal Verification for New Projects

```bash
./mvnw -B clean compile
./mvnw -B clean test
./mvnw -B clean package -DskipTests
```

Before delivery, at minimum demonstrate that: the dependency tree contains exactly one FastDDD version; locally ignored configuration is loaded from the module that actually starts; the port is listening and `/actuator/health` returns `UP`; and the critical paths pass for database CRUD, authentication and authorization, plus whichever of caching/events/messaging/search/third-party services the project actually enables. Integration tests must use an isolated environment and an explicit profile; skipping failing tests does not count as completion.

## 6. Working Constraints for Claude Code

```text
新建 FastDDD 业务系统时，先读 NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md；
仅按问题检索 FASTDDD_REFERENCE.md 和相应专题文档。使用 JDK 21、Boot 3.5.16、
fastddd-bom:1.0.0。所有 fast-ddd-* 不写版本；不引入 Tooling/Studio 运行时依赖；
不写入真实凭据。完成代码后执行编译、测试、启动健康检查和实际启用能力的回归，review 后以 Conventional Commit + 中文说明提交。
```
