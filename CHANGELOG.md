# Changelog

**English** | [简体中文](CHANGELOG.zh-CN.md)

This file records all notable changes to FastDDD, following the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format;
versioning follows [Semantic Versioning](https://semver.org/).

For the full version baselines and compatibility lines see [docs/COMPATIBILITY_MATRIX.md](docs/COMPATIBILITY_MATRIX.md);
for business-system upgrade steps see [docs/UPGRADE_GUIDE.md](docs/UPGRADE_GUIDE.md).

---

## [1.1.2] - 2026-10-26

### Added

- **AI behavior-view prompt contract (v3c keys)** (`fast-ddd-ai-spring-boot-starter`): the analyze system prompt now demonstrates the four behavior-face keys in its JSON schema sample — `commands` (`name`/`aggregateName`/`description`), `events` (`name`/`aggregateName`/`description`), `policies` (`triggerEvent`/`reactionCommand`/`description`) and `behaviors` (scenarios with `name`/`kind` `SEQUENCE`|`PROCESS` and `steps`: `from`/`to`/`label`/`messageKind` for sequence messages, label-only steps for process flows); the refine prompt retention sentence now covers all four keys (preserved verbatim unless the user instruction explicitly changes them); the skeleton prompt lists them as optional checklist item 7 (emit only when identifiable from the PRD). Additive only: `content_schema` stays `v1`, no key removed, canonical key order appends the four keys after the existing six.

### Compatibility

- BOM artifact set unchanged (18 artifacts, `FrameworkBomAllowlistTest`-guarded). Zero migration; dependency baseline unchanged (Java 21 / Spring Boot 3.5.16). The Studio consumption chain (typed columns, behavior view with sequence/process sub-views, PlantUML export) lands with the Studio-side release train.

## [1.1.1] - 2026-10-25

### Fixed

- **Audit-log masking hardening** (`fast-ddd-security-starter`): `AuditLogAspect.maskSensitiveJson` now matches sensitive keys by suffix family (`[A-Za-z0-9_]*(?:[pP]assword|[sS]ecret|[tT]oken)`) — compound keys such as `oldPassword`/`newPassword`/`clientSecret` that the previous five exact keys missed are covered — and masks escaped values as a whole (no early truncation on `\"`, which previously leaked tail plaintext and split the product JSON). Protects framework direct consumers writing audit params; Studio-side was already fully sealed (`logParams=false` + `WRITE_ONLY` payloads).

### Added

- **AI context-map prompt contract (v3b keys)** (`fast-ddd-ai-spring-boot-starter`): the analyze system prompt now demonstrates `contextRelationships` (`from`/`to`/`pattern`/`description`) between bounded contexts and `aggregates[].contexts` in its JSON schema sample; the refine prompt carries an explicit retention sentence (these keys are preserved verbatim unless the user instruction explicitly changes them); the skeleton prompt lists `contextRelationships` in its output checklist. Additive only: `boundedContexts` stays a flat string list, `content_schema` stays `v1`, no key is removed. The Studio consumption chain (typed columns, ER-map view, PlantUML grouping) lands with the Studio-side release train.

### Compatibility

- BOM artifact set unchanged (18 artifacts, `FrameworkBomAllowlistTest`-guarded). Zero migration; dependency baseline unchanged (Java 21 / Spring Boot 3.5.16).

## [1.1.0] - 2026-10-24

### Added

- **Optional identity capability — Feishu integration** (four new modules managed by `fastddd-bom`: `fast-ddd-identity-core`, `fast-ddd-identity-reference-pg`, `fast-ddd-identity-spring-boot-starter`, `fast-ddd-identity-feishu`): login (authorization-code flow with state/browser binding), worker tokens, directory mirror & incremental sync, and an audit actor bridge. Wholly gated by `fastddd.identity.enabled` (default **off**) — a consumer that imports no `fast-ddd-identity-*` dependency sees zero behavior change relative to 1.0.0 (compatibility note in [docs/COMPATIBILITY_MATRIX.md](docs/COMPATIBILITY_MATRIX.md)).
- **Security-starter identity bridge** (`fast-ddd-security-starter`): optional `AuditActorProvider`, `LocalAuthConfigurationGuard`, and audit-log aspect/entity extension points — active only when the identity modules are present.
- **AI field-design prompt contract: `defaultValue`** (`fast-ddd-ai-spring-boot-starter`): `ddd-fields-user.txt` now demonstrates and mandates a `defaultValue` key for fields carrying a business default (omitted otherwise — never `null`), completing the Studio consumption chain (projection → entity `default_value` → DDL `DEFAULT` in PostgreSQL/Oracle/MySQL/H2).
- **Consumer-tests coverage**: `identity-*` and `security-*` invoker scenarios (directory, directory-sync, directory-feishu, feishu-login, host-security, session-postgres, worker, disabled; local-auth-off, minimal, nodb, custom-config, with-database) plus `bom-revision-isolation`.
- **TCK harness artifacts** (`fast-ddd-identity-core`): a `test-jar` classifier is produced in source-build reactors for TCK dogfood; it is intentionally **not** part of the published GA artifact set (288-file candidate form preserved).

### Compatibility

- The BOM manages 18 artifacts (14 core + 4 identity); `FrameworkBomAllowlistTest` guards the set.
- Core runtime lineage: 13 modules are byte-identical to v1.0.2 in `src/main`; `fast-ddd-security-starter` carries only the 5-file identity bridge face (W2-era, covered by the M2 gates). Pre-1.1.0 consumers see no core behavior change.

### Migration

- Identity adopters: the reference PG adapter requires host-owned schema migrations V1–V3 (+ V4 directory increment) and, optionally, `V1__sys_audit_log_actor_columns`; see `docs/identity/` (W4-d release notes, migration guide, config samples, recovery statement).

## [1.0.2] - 2026-10-12

### Added

- **TableMetadata semantic expansion (additive-nullable)** (`fast-ddd-common`): three new nullable `String` columns — `aggregateName`, `dddRole`, `relationsSummary` — with no initializers, so pre-1.0.2 JSON payloads deserialize with the three columns left `null` (locked by `TableMetadataJsonCompatibilityTest`). Downstream codegen templates consume them as semantic annotations and must keep `<#if x?? && x>` / `${x!''}` guarded forms — unguarded interpolation throws on pre-1.0.2 jars. Content is additive at semver-minor volume carried on the patch slot per the single-1.0.2-from-trunk release decision — a disclosed deviation from the matrix's "PATCH = bug fixes only" wording.

### Fixed

- **`ddd-refine-user.txt` prompt contract** (`fast-ddd-ai-spring-boot-starter`): the "(skeleton, no field details)" heading and the blanket "no need to output the fields array" rule conflicted with the actual full-payload input and with consumers that require hand-edited field definitions to be preserved in the output JSON (Studio's manual-changes appendix). Wording now states the general rule and explicitly carves out hand-edited fields.

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
