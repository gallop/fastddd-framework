# Changelog

**English** | [简体中文](CHANGELOG.zh-CN.md)

This file records all notable changes to FastDDD, following the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format;
versioning follows [Semantic Versioning](https://semver.org/).

For the full version baselines and compatibility lines see [docs/COMPATIBILITY_MATRIX.md](docs/COMPATIBILITY_MATRIX.md);
for business-system upgrade steps see [docs/UPGRADE_GUIDE.md](docs/UPGRADE_GUIDE.md).

---

## [1.0.1] - 2026-10-05

### Fixed

- **Audit success semantics for failed business requests** (`fast-ddd-security-starter`, behavior change — read before upgrading): the `@AuditLog` aspect now judges the audited method's return envelope after a normal return. A returned `ApiResponse` — directly or as the body of a one-layer `ResponseEntity` wrapper — with `code != 200` (per `ApiResponse#isSuccess()`'s hard 200 check; `null` code counts as failure) is recorded as `success = 0`, and the envelope message is carried into `error_message` when non-blank. Previously the aspect set `success = true` unconditionally after a normal return, so controllers that catch business exceptions and translate them into failure envelopes (HTTP 200/400 returned normally, no exception thrown) were **always audited as successes** — most visibly, failed logins on the framework's built-in `/api/auth/login` endpoint.
  - Judgment boundary: only the two explicit envelope shapes above are judged. Non-envelope returns (raw DTOs, `String`), `ResponseEntity` bodies that are `null` or not an `ApiResponse`, and async return types whose eventual result is not settled at aspect time keep the legacy `success = true`.
  - The guard never affects the audited request path: any failure inside the guard (including envelope subclasses overriding `isFailure()`/`getMessage()` to throw) falls back to legacy `success = true` with a debug log; and when `fast-ddd-web` is absent from the consumer classpath (optional dependency) the guard stays dormant.
  - **Rollback switch**: set `fastddd.security.audit.result-judging.enabled=false` to restore 1.0.0 always-success semantics (default `true`). The key is matched by `@Value` with no relaxed binding — use the exact kebab-case literal above; camelCase variants silently fall back to the default.

## [1.0.0] - 2026-09-13

First official release baseline: **JDK 21 + Spring Boot 3.5.16**.
This version was driven by UPG-000 through UPG-010 of `JDK21_SPRING_BOOT_3_5_UPGRADE_PLAN.md`,
as a deterministic upgrade from the JDK 17 / Boot 3.4.5 development snapshot.

> **Re-issue note (2026-09-13, Owner decision).** The internal delivery of 1.0.0 from 2026-09-10
> was withdrawn and re-issued in the same version slot: pre-verification confirmed the old artifacts
> had no consumers (both pilot projects consume `com.gallop.fastddd:1.0.0-jdk21-rc1`, not this GA),
> were never deployed, and were never delivered externally. The old tag objects, target commits and
> artifact bytes are archived with SHA-256 fingerprints under
> `.fastddd-releases/internal-v1.0.0-r2/evidence/withdrawn-internal-v1.0.0/`; the old internal
> release repo is isolated and marked withdrawn. The "Re-issued additions" subsection below lists
> what the re-issued bits add over the withdrawn bits — documentation and registration changes
> only, **no runtime code changes**.

### Re-issued additions (2026-09-13, vs the withdrawn 2026-09-10 bits)

- **Added — documentation internationalization (L2)**: the root `README.md` became the English master with a new `README.zh-CN.md` mirror; P0/P1 batch — 17 reference documents received `*.zh-CN.md` counterparts (guides, API reference, compatibility matrix, upgrade documentation).
- **Changed — governance archive relocation**: 30 internal governance / evolution documents moved out of the repository to the out-of-repo private archive `fastddd-docs-internal`; references in retained documents fixed (docs index, compatibility matrix, upgrade entry, ID-migration guide, Jackson configuration, ADR-007). The repository now carries Community-safe content only.
- **Changed — release-status documentation** synchronized for the GA 1.0.0 coordinates: migration guide and version literals finalized.
- **Changed — version registrations unified on `1.0.0`** (Owner decision 2026-09-13): the unpublished `1.0.0-jdk21` version plan is abandoned; the root and `fastddd-bom` default `revision` return to `1.0.0-SNAPSHOT` and the consumer-tests IT defaults follow. Historical rc1 records unchanged.
- **Fixed — English canon** translation finalization plus adversarial-review remediation across the documentation set.
- **Fixed — `Outbox` MySQL test javadoc**: removed a machine-local JDK path (desensitization).

### Added

- **CI-friendly version governance**: the `${revision}` property is the single version entry of the reactor, driving all 27 module versions uniformly; combined with `flatten-maven-plugin` (`resolveCiFriendliesOnly`), `${revision}` is resolved to a literal in published POMs — satisfying both "versions enter the reactor through one property only" and "published POM versions are already resolved".
- **`fastddd-bom` unified dependency-contract module**: business projects simply `import` this BOM to get consistent versions of all FastDDD modules and the recommended third-party dependencies, without declaring versions themselves. The BOM is a standalone POM; its published artifact is self-contained (no environment-property leakage).
- **Maven Enforcer build gates**: `requireJavaVersion[21,)`, `requireMavenVersion[3.9.0,4.0.0)`, `requirePluginVersions` (all plugin versions explicit), `banDuplicatePomDependencyVersions`.
- **JDK 21 compile baseline**: root pom `java.version=21` + `maven.compiler.release`; artifacts are class major version 65.
- **Spring Boot 3.5.16 + deterministic dependency alignment**: Spring AI 1.1.8, SpringDoc 2.8.17, Knife4j 4.5.0, MyBatis-Plus 3.5.12, MyBatis-Spring 3.0.4, Druid `druid-spring-boot-3-starter` 1.2.28, mysql-connector-j 9.7.0, Micrometer 1.15.12 (eliminating legacy version splits), Jackson 2.21.x (Boot-managed).
- **PMD 7**: `maven-pmd-plugin` upgraded to 3.24.0 (PMD 7.3.0), supporting `targetJdk=21`.
- **Maven Wrapper**: `./mvnw` pins Maven 3.9.5, unifying CI and local Maven versions.
- **Docker JDK 21**: build stage `maven:3.9.9-eclipse-temurin-21`, runtime stage `eclipse-temurin:21-jre-alpine`.
- **Version-governance documentation**: added `docs/COMPATIBILITY_MATRIX.md`, `docs/UPGRADE_GUIDE.md`, `docs/RELEASE_CHECKLIST.md`.

### Changed

- Spring Boot 3.4.5 → **3.5.16**; JDK 17 → **JDK 21** (compile and runtime).
- Druid coordinate migration: `druid-spring-boot-starter` (Boot 2 coordinate) → **`druid-spring-boot-3-starter`** (4 references).
- MySQL driver migration: `mysql:mysql-connector-java` → **`com.mysql:mysql-connector-j`** 9.7.0 (6 references).
- Removed hand-managed version properties (mysql-connector / micrometer / lombok / freemarker / junit / testcontainers), delegating uniformly to Spring Boot BOM management and eliminating version splits.
- `application-dev.yml`: fixed a duplicated top-level `fastddd:` key (Boot 3.5 SnakeYAML strict parsing rejects duplicate keys).

### Fixed

- **B-01**: `PerformanceIntegrationTest.testBatchInsertPerformance` failed because "logical delete → re-insert of the same username" hit the `username` UNIQUE constraint; switched to a physical `DELETE` via an injected `JdbcTemplate`. Full gate: 356 tests / 0 failures.
- Pre-existing `<relativePath>` error in `fast-ddd-examples/pom.xml` (`../../pom.xml` → `../pom.xml`).
- Cleaned up two invalid `<includeStackTrace>` attributes in `fast-ddd-web/logback-spring.xml` that were ignored by logstash-encoder.

### Known Limitations / carried over (not blocking 1.0.0)

- **CI runner not deployed**: the remote is an internal Gitea instance; `.github/workflows/*.yml` are declarative files with no runner executing them; the JDK 21 / wrapper / `./mvnw` / PMD 7 changes form an "effective once CI is ready" baseline, to be verified after a runner is deployed.
- **`checkstyle.xml` rules file missing**: `configLocation=checkstyle.xml` but the repository has no such file; the code-quality step runs with `continue-on-error` tolerance; adding the rules file is a separate task.
- **External-infrastructure runtime smoke tests pending**: Redis, Elasticsearch, RocketMQ distributed chains, and Docker build/startup health checks had no corresponding runtimes on the dev machine — only compile/context-level verification; smoke tests to be added once the deployment environment or Docker is ready.
- **`SecurityIntegrationTest` class-level `@Disabled`**: self-referencing `@SpringBootTest(classes=itself)` context-loading problem; pre-existing test debt (not introduced by this upgrade, not added to pass the gate).
- **Mockito self-attach warning**: inline mock self-attachment under JDK 21 (all green, coverage normal); coexistence of an explicit mockito agent with the JaCoCo argLine is deferred to the JDK 25+ migration.
- **BOM and root pom dependency lists duplicated**: to reduce reactor-resolution risk this time, the root pom's `<dependencyManagement>` stayed unchanged and the BOM mirrors the list independently. Registered as known technical debt; from 1.0.1 the root pom converges by importing the BOM (with separate reactor-resolution verification).

### 1.0.1 roadmap (registered must-do items)

- **Binary-compatibility checks (japicmp / Revapi)**: 1.0.0 has no GA baseline jar, so japicmp has no baseline to compare against; after the first GA baseline is established, every release from 1.0.1 on gets an API-compatibility check.
- **Root pom delegates to the BOM**: the root pom's `<dependencyManagement>` switches to `import` `fastddd-bom`, achieving a single source of dependency versions.
- **Generation templates switch to the BOM**: archetype-resources / FreeMarker generation templates import `fastddd-bom` by default, giving generated business projects the unified dependency contract out of the box.

---

## Pre-upgrade development snapshot (archived, unmaintained)

| Item | Value |
|----|----|
| Java | 17 |
| Spring Boot | 3.4.5 |
| FastDDD version string | `1.0.0-SNAPSHOT` |
| Status | archived as Git tag `baseline/jdk17-boot3.4.5` only, no longer maintained |

> This snapshot carries no external compatibility-line commitment. All business systems in development should upgrade to the JDK 21 / Boot 3.5.16 baseline together with 1.0.0
> (see [docs/UPGRADE_GUIDE.md](docs/UPGRADE_GUIDE.md) and UPG-011).
