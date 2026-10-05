# Compatibility Matrix

**English** | [简体中文](COMPATIBILITY_MATRIX.zh-CN.md)

> This document defines the runtime baseline, maintenance status, and change boundaries of each FastDDD version line; it is the basis on which business systems choose or upgrade versions.
> For upgrade steps see [UPGRADE_GUIDE.md](UPGRADE_GUIDE.md); for release history see [../CHANGELOG.md](../CHANGELOG.md).

## Version-line Baselines

| FastDDD | Java (build + runtime) | Spring Boot | Maven | Status |
|---------|-------------------|-------------|-------|------|
| Pre-upgrade development snapshot | 17 | 3.4.5 | 3.9.x | 🔒 archived as Git tag `baseline/jdk17-boot3.4.5` only, **unmaintained** |
| **1.0.x** | **21** | **3.5.x** (currently 3.5.16) | 3.9.x (wrapper pin 3.9.5) | ✅ **current stable line** (internal v1.0 released: `1.0.0`, tag `v1.0.0`, 2026-09-10; public-GA gate material moved to a private out-of-repository archive) |
| 1.1.x | 21 | 3.5.x | 3.9.x | 📋 planned (compatibility hardening: japicmp/Revapi, BOM single source, templates switching to the BOM) |
| 2.0.x | TBD | 4.x | TBD | 🔭 future plan (Spring Boot 4 / Spring Framework 7 / Jackson 3 migration) |

## Key Dependency Matrix (1.0.x line, verified via `dependency:tree`)

| Dependency | Version | Source |
|------|------|------|
| Spring Framework | 6.2.19 | Spring Boot BOM |
| Spring AI | 1.1.8 | explicit BOM import (the 1.1 maintenance line matching Boot 3.5.x) |
| MyBatis-Plus (starter/jsqlparser/generator) | 3.5.12 | explicit dependencyManagement |
| MyBatis-Spring | 3.0.4 | explicit dependencyManagement |
| Druid | 1.2.28 (`druid-spring-boot-3-starter`) | explicit dependencyManagement |
| MySQL driver | 9.7.0 (`com.mysql:mysql-connector-j`) | Spring Boot BOM |
| Micrometer (core/observation/registry-prometheus) | 1.15.12 | Spring Boot BOM |
| Jackson | 2.21.x | Spring Boot BOM |
| SpringDoc OpenAPI | 2.8.17 | explicit dependencyManagement |
| Knife4j | 4.5.0 | explicit dependencyManagement |
| SLF4J | 2.0.18 | Spring Boot BOM |
| Lombok | 1.18.46 | Spring Boot BOM |

> Business projects must **not override** the versions above (especially Spring Boot, Spring, Jackson) — they are verified through joint testing of FastDDD against the upstream BOMs; overriding may introduce version splits. To manage versions centrally, simply `import` [`fastddd-bom`](../README.md).

## Cross-product Compatibility Matrix (Studio → Tooling → Framework)

> This section is the human-readable mirror of the **version combinations across FastDDD's three products (Studio / Tooling / Framework)**.
> **Machine-readable specification source**: `fastddd-compatibility-matrix.yaml` shipped in the Tooling Community artifact `fastddd-tooling-api`. The source link will be added once the public repository URL is settled; internal SCM is not exposed in Community documentation.

The three products have been physically split into separate repositories; the current candidates still share the same `${revision}` to first validate the lock-step release order.
ADR-005 §3.2 established that **independent per-product versioning is a post-split matter**. VER-001 carries this via a **machine-readable matrix + build-time verification**: adding a differentiated tuple expresses an independent release cadence, and unregistered incompatible combinations are rejected at build time by the version-governance test — the remote is an internal Gitea without a CI runner, so the workflow files are declarative only and will enforce the same rule once a runner is deployed.

### Valid version combinations (compatible-tuples)

| Studio | Tooling | Framework | Status |
|--------|---------|-----------|------|
| 1.0.0-jdk21-SNAPSHOT | 1.0.0-jdk21-SNAPSHOT | 1.0.0-jdk21-SNAPSHOT | ✅ current (first lock-step candidate after the split) |

At build time, `VersionGovernanceContractTest#currentReactorTupleIsRegisteredInMatrix_step6` asserts that the current reactor tuple `(revision, revision, revision)` is present in the table; after the split, adding a tuple row enables a new independent combination.

### Framework platform manifest (framework-lines)

Each Framework version line carries a platform dependency baseline, asserted at build time by `VersionGovernanceContractTest#matrixManifestMatchesBaseline_step1` to match the "Key Dependency Matrix" above:

| Framework line | Status | Java | Spring Boot | Spring AI | SpringDoc | Knife4j |
|--------------|------|------|-------------|-----------|-----------|---------|
| 1.0.0 | current-stable (internal release 2026-09-10, tag `v1.0.0`) | 21 | 3.5.16 | 1.1.8 | 2.8.17 | 4.5.0 |
| 1.0.1 | patch (audit success judgment fix, tag `v1.0.1`, 2026-10-05; staged — `current-stable` stays on 1.0.0 until the release sync updates the generation-channel pin) | 21 | 3.5.16 | 1.1.8 | 2.8.17 | 4.5.0 |

### How generation channels pick the target Framework version (VER-001 step 5)

The three project-generation channels take the **target Framework version** from this matrix; they **do not copy Studio/Tooling's own versions**:

| Channel | Target-version source | Matrix verification |
|------|--------------|----------|
| Maven Plugin (`InitProjectMojo`) | `fastddd-version.properties` `@revision@` filtering → the Framework version of the current build | resolved via `CompatibilityMatrix.manifestFor()`, WARN on unknown |
| Studio runtime (`ProjectGeneratorService` etc.) | `FastdddVersionDefaults.CURRENT` literal fallback + `@Value("${fastddd.version}")` override | `@PostConstruct` verifies `manifestFor(fastdddVersion)`, WARN on unknown |
| Archetype (deprecated) | `archetype-metadata.xml` `fastdddVersion` requiredProperty (user-overridable) | — |

The Studio-side `FastdddVersionDefaults.CURRENT` literal is mechanically coupled to the matrix's `current-stable` line by `FastdddVersionDefaultsContractTest` — when a Framework version line is upgraded, changing the matrix plus that constant surfaces the drift (`@Value` defaults must be compile-time constants, hence the literal fallback is kept).

## Studio Build Isolation (SPLIT-005)

> Giving Studio Server the ability to **build independently after moving out of the reactor** (within the monorepo, as preparation for a safe physical split).

Studio previously coupled deeply to the FastDDD root pom (parent inheriting the root, dependencies on root properties, `@...@` tokens in `application.yml` depending on root profiles). SPLIT-005 moved studio into a **self-contained shape** isomorphic to that of business consumers:

- **`fastddd-studio` (studio-parent)**: parent changed to `spring-boot-starter-parent` (providing Boot plugin management + `@..@` resource filtering + compiler defaults), managing Framework/Tooling dependencies (version-less) via `import fastddd-bom`, declaring its own `<revision>` lock-stepped with the root/BOM (ADR-005 §3.2: lock-step values during the monorepo period, mechanism decoupled — independent versioning possible after the split).
- **`fastddd-studio-server`**: inherits studio-parent (`relativePath ../pom.xml` resolving `${revision}`), no longer inheriting the FastDDD root pom.

**Timing of version independence** (ADR-005 §3.2): Studio's "currently shared `${revision}` is a monorepo-era convenience; independence comes after the split." What SPLIT-005 delivers is **build-lineage isolation** (severing root parent inheritance) — the true prerequisite for independent releasing; differentiated per-product revision is a post-split matter (the carrying mechanism is already provided by the `compatible-tuples` matrix above).

**Isolation verification command** (gated, not triggered by default, zero impact on the standard `verify` gate):

```bash
# 1. 装入全部 fastddd 制品到本地仓库
mvn install -DskipTests -B
# 2. forked Maven 构建真 studio-server（非 host reactor，仅依赖已 install 制品）
mvn verify -pl fastddd-consumer-tests -Pstudio-isolation -B
```

This invoker build clones the real `fastddd-studio` tree inside an isolated forked process and builds `fastddd-studio-server` — its framework/tooling dependencies resolve only from the local repository (published RC artifacts), proving that "studio-server still builds independently after removing root-reactor aggregation."

**Build-time guards** (part of the real `clean verify` gate, `VersionGovernanceContractTest`):
- `studioParentRevisionMatchesRoot_step3`: root pom == BOM == studio-parent agree on `<revision>`.
- `studioParentDoesNotInheritFastdddRoot_step1`: studio-parent `<parent>` is not the fastddd root + imports fastddd-bom.

## Change Boundaries (semantic versioning commitments)

To avoid "shipping breaking changes as ordinary minor upgrades," FastDDD commits to the following change boundaries per version line:

| Change type | Version line | Meaning |
|----------|------------|------|
| **PATCH (x.y.Z)** | same minor line | bug fixes only, dependency patch upgrades (e.g. Boot 3.5.16 → 3.5.17), documentation. **No API breakage of any kind.** |
| **MINOR (x.Y.0)** | same major line | new features, new modules, dependency minor upgrades. **Remains binary backward compatible** (enforced by japicmp/Revapi from 1.0.1). |
| **MAJOR (X.0.0)** | across major lines | breaking changes allowed (e.g. the Spring Boot 4 / Spring 7 / Jackson 3 migration). **Never shipped as an ordinary minor**; requires a dedicated upgrade guide. |

### Not allowed

- ❌ Upgrading the **Java major version** (Java 21 → 22+) in PATCH/MINOR.
- ❌ Upgrading the **Spring Boot major/minor version** (3.5.x → 3.6.x / 4.x) in PATCH/MINOR.
- ❌ Introducing Spring Boot 4 / Spring Framework 7 / Jackson 3 changes on the 1.0.x line.
- ❌ Removing or changing public API signatures in PATCH/MINOR (enforced by binary-compatibility checks from 1.0.1).

### Allowed

- ✅ PATCH: dependency patch upgrades, bug fixes, performance tuning, documentation.
- ✅ MINOR: new modules/features, new optional dependencies, dependency minor upgrades (verified through joint testing).

## Build & Runtime Requirements

| Item | Requirement |
|----|------|
| Build JDK | **21+** (hard prerequisite; `<release>21`, artifact major version 65) |
| Runtime JRE | **21+** (same as build) |
| Maven | **3.9.0+** (Enforcer-enforced; wrapper pin 3.9.5) |
| Enforcer gates | `requireJavaVersion[21,)` · `requireMavenVersion[3.9.0,4.0.0)` · `requirePluginVersions` · `banDuplicatePomDependencyVersions` |

> ⚠️ **JDK 21 hard prerequisite**: if the machine's default `JAVA_HOME` points to JDK 17, Maven fails with "unsupported release version 21" and the build aborts. Switch to JDK 21 explicitly before building (CI runners likewise).

## Version-switching Procedure (VER-001 step 8)

FastDDD uses CI-friendly version governance (a single `${revision}` property + flatten-maven-plugin); switching versions requires no module-pom edits — just override the `revision` property. **The `-jdk21` qualifier is the JDK 21 product-line signal and must be preserved when switching versions** (established by UPG-010 + this matrix).

> Note: internal v1.0 was released on 2026-09-10 (`1.0.0`, tag `v1.0.0`, deployed to the internal release repository; not pushed to GitHub / Maven Central / unsigned — owner decision recorded in the tag notes). The working-tree `<revision>` stays on the next development line `1.0.0-jdk21-SNAPSHOT`; the procedures below apply to subsequent patch/RC/GA switches.

### Switching an RC candidate

```bash
# JDK 21 硬前置
export JAVA_HOME=<path-to-jdk-21>; export PATH="$JAVA_HOME/bin:$PATH"

# 覆盖 revision → RC 字面量；flatten 在 install/deploy 时字面量化产物
./mvnw -B clean verify -Ptest -Drevision=1.0.0-jdk21-rc1
./mvnw -B install -DskipTests -Drevision=1.0.0-jdk21-rc1
```

After switching an RC, **synchronize**:
1. `fastddd-compatibility-matrix.yaml`'s `framework-lines[].revision` + `compatible-tuples[].framework/tooling/studio`;
2. `FastdddVersionDefaults.CURRENT` (the Studio fallback literal);
3. `VersionGovernanceContractTest#matrixManifestMatchesBaseline_step1` / `FastdddVersionDefaultsContractTest` automatically
   catch drift between the matrix and the reactor (step 6/7 guards).

### Switching to the next product line (e.g. 1.1.0-jdk21-SNAPSHOT)

```bash
# 1. 根 pom + BOM pom 的 <revision> 属性改为新字面量（BOM 无 parent，须手工同步——步骤 7 测试守护）
# 2. 矩阵新增 framework-line + compatible-tuple 行
# 3. 更新 FastdddVersionDefaults.CURRENT
# 4. 全量门禁：./mvnw -B clean verify -Ptest
```

`rootRevisionMatchesBomRevision_step7` asserts that the root pom and BOM `<revision>` literals are equal;
`scripts/verify-version-consistency.py` scans all poms in the repository asserting that no module hardcodes version literals (versions must flow through `${revision}`).
