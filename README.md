# FastDDD Framework Community

![Version](https://img.shields.io/badge/version-1.0.0-blue) ![JDK](https://img.shields.io/badge/JDK-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen) ![License](https://img.shields.io/badge/license-Apache--2.0-green)

[English](README.md) | [简体中文](README.zh-CN.md)

FastDDD Framework Community is a DDD runtime foundation for Java 21 and Spring Boot 3.5. It provides the common domain model, application-layer contracts, web and unified response handling, security, domain events with Outbox, cache, search, monitoring, and optional AI integration. It does not include the commercial control-plane capabilities of FastDDD Studio Pro.

> **Status: 1.0.0 GA** (2026-09-10, tag `v1.0.0`), Maven coordinates `io.github.gallop.fastddd`. Twelve modules are covered by the 1.0 stable-API commitment; `fast-ddd-ai-spring-boot-starter` and `fast-ddd-analyzer-spring-boot-starter` remain Incubating.

## Modules

| Module | Role | 1.0 stability |
|---|---|---|
| `fast-ddd-common` | Common types, exceptions, and domain foundations | 1.0 stable |
| `fast-ddd-config` | Configuration and conditional wiring | 1.0 stable |
| `fast-ddd-model` | Domain model and persistence base classes | 1.0 stable |
| `fast-ddd-crud` | Repository, Mapper, and CRUD infrastructure | 1.0 stable |
| `fast-ddd-application` | Command, Query, and Handler contracts | 1.0 stable |
| `fast-ddd-web` | Web, unified response, and exception handling | 1.0 stable |
| `fast-ddd-cache` | Cache abstraction and Redis integration | 1.0 stable |
| `fast-ddd-event` | Domain events, messaging, and Outbox | 1.0 stable |
| `fast-ddd-search` | Search-engine integration | 1.0 stable |
| `fast-ddd-monitor` | Metrics and SQL monitoring | 1.0 stable |
| `fast-ddd-security-starter` | JWT, authentication, authorization, and security extension points | 1.0 stable |
| `fast-ddd-ai-spring-boot-starter` | Generic Spring AI integration (Incubating) | Incubating |
| `fast-ddd-analyzer-spring-boot-starter` | Data-structure analysis (Incubating) | Incubating |
| `fast-ddd-starter` | All-in-One Starter | 1.0 stable |

> **Stability label semantics**: "1.0 stable" marks modules inside the GA 1.0 stable-API commitment, per the 1.0 API freeze review and GA gate conclusions. "Incubating" marks experimental capabilities, which keep the freedom to change between versions; breaking changes will be announced in release notes.

Module sources live in [`fastddd-framework/`](fastddd-framework/); the single version entry point is [`fastddd-bom`](fastddd-bom/). Experimental Agent Access lives in [`agent-access/`](agent-access/) with its own compatibility commitment and does not gate Framework 1.0.

## Requirements

- **JDK 21** — hard requirement; the build enforces `requireJavaVersion[21,)`
- **Maven 3.9+** — the wrapper pins 3.9.5
- **Spring Boot 3.5.16** — managed through the BOM

## Quick start

> **Artifact resolution**: FastDDD artifacts resolve from your configured Maven repositories under the coordinates `io.github.gallop.fastddd` (Java package names remain `com.gallop.fastddd`). To install from source, build the framework repo first, then the tooling repo (the plugin depends on `fastddd-bom`):
>
> ```bash
> cd fastddd-framework && ./mvnw -B -Drevision=1.0.0 clean install -DskipTests
> cd ../fastddd-tooling  && ./mvnw -B -Drevision=1.0.0 clean install -DskipTests
> ```

**New project — scaffold it with the companion tooling plugin** (RICH model, PostgreSQL, two bounded contexts):

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:init-project \
  -Dinit.groupId=com.example \
  -Dinit.artifactId=shop \
  -Dinit.package=com.example.shop \
  -Dinit.modelType=RICH \
  -Dinit.databaseType=postgresql \
  -Dinit.enableEvent=true \
  -Dinit.enableSecurity=true \
  -Dinit.contexts=order,product
```

**Existing project — import the BOM first, then declare modules without versions:**

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

Guides: [Quick start](docs/guides/QUICK_START.md) · [New business system development guide](docs/guides/NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md) · [Upgrade guide](docs/upgrade/README.md) · [Compatibility matrix](docs/COMPATIBILITY_MATRIX.md)

## Build

```bash
./mvnw clean verify
```

Versioned (GA) artifacts build with `-Drevision=1.0.0`; the reactor default stays `-SNAPSHOT`.

## Documentation

- Docs entry: [`docs/README.md`](docs/README.md)
- Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)

## Ecosystem

- **fastddd-tooling** — companion repository: project scaffolding (`init-project`, `generate-code`), code/doc generation engines, templates, Maven plugins. <!-- public repo URL to be added at open-source time -->
- **FastDDD Skills** — Claude skills encoding FastDDD best practices: architecture design → project init → development patterns → automated dev loop. Distributed separately. <!-- distribution URL to be added -->

## License

FastDDD Framework Community is licensed under the [Apache License 2.0](LICENSE); third-party attribution is in [NOTICE](NOTICE). FastDDD Studio Pro is a separate proprietary product and is not granted rights under this license by depending on Community artifacts.
