# Upgrade Guide

**English** | [简体中文](UPGRADE_GUIDE.zh-CN.md)

> This guide walks business systems through upgrading from the old baseline (JDK 17 / Spring Boot 3.4.5 / FastDDD `1.0.0-SNAPSHOT`) to
> **JDK 21 / Spring Boot 3.5.16 / FastDDD `io.github.gallop.fastddd:1.0.0`**.
>
> Projects already on `1.0.0-jdk21-rc1` (old coordinates `com.gallop.fastddd`) only need to complete
> [Section 3, Maven coordinate migration](#3-fastddd-maven-coordinate-migration-100-ga) to reach 1.0.0 — zero source changes.
>
> This document is the Community general upgrade guide: it covers the JDK/Boot/BOM fundamentals (Sections 1/2) and the 1.0.0 Maven coordinate migration (Section 3),
> but it cannot replace a full business upgrade acceptance. Business systems that use Framework 1.0 strongly typed IDs, CRUD, Security, Event, caching, or external
> infrastructure should load the matching supplemental guides via the capability mapping table at the [business system upgrade entry point](upgrade/README.md); project-specific gray release / UAT
> plans should be maintained in each project's own private repository.
>
> When starting a new business system upgrade, read the [business system upgrade entry point](upgrade/README.md) first; it defines the minimal must-read set, the read-more-per-feature rules, and the acceptance gates.
> For the compatibility line see [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md) (the matrix's `current` reflects the framework development-line SNAPSHOT;
> the version consumers upgrade to is the `1.0.0` given in §0/§3 of this document); for release history see [../CHANGELOG.md](../CHANGELOG.md).

---

## 0. Pre-Upgrade Assessment

| Item | Old baseline | New baseline |
|----|--------|--------|
| Java | 17 | **21** |
| Spring Boot | 3.4.5 | **3.5.16** |
| FastDDD version | `1.0.0-SNAPSHOT` (or `1.0.0-jdk21-rc1`) | **`1.0.0`** |
| FastDDD groupId | `com.gallop.fastddd` | **`io.github.gallop.fastddd`** (see Section 3) |
| MySQL driver coordinates | `mysql:mysql-connector-java` | **`com.mysql:mysql-connector-j`** 9.7.0 |
| Druid coordinates | `druid-spring-boot-starter` | **`druid-spring-boot-3-starter`** |

**This upgrade combines a build baseline lift with the Framework 1.0 contract tightening.** The Java major move 17→21 is a build/runtime baseline change;
business systems that use `BasePO`, `IService`, `BaseServiceImpl`, `BaseController`, or the old CRUD helper APIs
additionally need the strongly typed ID and source adaptation. Post-release 1.0.x patch/minor version lines stay compatible, but moving from an old SNAPSHOT into the first 1.0 RC/GA
must not assume "just change the dependencies and you are done".

---

## 1. Install and Switch to JDK 21 (Hard Prerequisite)

FastDDD 1.0.x requires **JDK 21+** to compile and run. If the machine's default `JAVA_HOME` still points to JDK 17,
Maven reports "unsupported release version 21" and goes straight to BUILD FAILURE.

```bash
# 确认 JDK 21 已安装
java -version   # 应输出 21.x

# 构建前显式切换（写入 CI/IDE/启动脚本）
export JAVA_HOME=<path-to-jdk-21>
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # 复核
```

> IDEs (IntelliJ / VS Code) must set both the Project SDK and the Maven Runner JDK to 21.

---

## 2. Upgrade FastDDD Dependencies: import fastddd-bom

> **Parent strategy**: from 1.0 on, the recommendation is `parent = spring-boot-starter-parent` + `import fastddd-bom` (example below).
> If the project's current parent is the old `com.gallop.fastddd:fastddd-framework-parent` and you want to keep that inheritance, just change the parent block's
> `<groupId>` to `io.github.gallop.fastddd` (the sed in Section 3.3 already covers it); pick one of the two paths — do not mix them.

Recommended practice: the business project `import`s `fastddd-bom`, and versions of all FastDDD modules plus recommended third-party dependencies are managed centrally by the BOM —
**no need to declare versions one by one**.

```xml
<project>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>          <!-- 升级到 3.5.16 -->
  </parent>

  <properties>
    <java.version>21</java.version>     <!-- 升级到 21 -->
  </properties>

  <!-- 统一依赖契约入口：import fastddd-bom -->
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
    <!-- 版本由 BOM 统一管理，删除所有 fast-ddd-* 上的 <version> -->
    <dependency>
      <groupId>io.github.gallop.fastddd</groupId>
      <artifactId>fast-ddd-starter</artifactId>
      <!-- 无 <version> -->
    </dependency>
  </dependencies>
</project>
```

**Prerequisite**: FastDDD is installed into your local/internal repository. From the FastDDD source:
```bash
export JAVA_HOME=<path-to-jdk-21>; export PATH="$JAVA_HOME/bin:$PATH"
cd <fastddd-source>
./mvnw -B -Drevision=1.0.0 clean install -DskipTests   # 安装含 fastddd-bom 在内的全部模块
```

### Do Not Override Upstream Versions

Business projects should **not** explicitly declare or override versions of Spring Boot, Spring, Jackson, Micrometer, and the like — those are jointly validated by FastDDD
and the upstream BOMs. Overriding introduces version skew (the pre-upgrade snapshot once split precisely because `micrometer-registry-prometheus` was pinned
to an old version). Leave versioning to `spring-boot-starter-parent` + `fastddd-bom`.

### Tomcat Version Property (only for consumers with parent=spring-boot-starter-parent)

fastddd-bom now explicitly manages `tomcat-embed-{core,el,websocket}` at **10.1.59** (S3 security remediation: the fix line for the
CVE-2026-65905/65182/68525 issues in 10.1.55). Whether you need to declare it yourself depends on your consumption shape — **not every consumer must configure it**:

| Consumption shape | Effective version | Required action |
|---|---|---|
| `import` fastddd-bom only (no Boot parent) | 10.1.59 (the BOM's explicitly managed entry wins) | None |
| parent = `spring-boot-starter-parent` | Still defaults to the 10.1.55 managed by Boot 3.5.16 — **import only fills gaps; it does not override dependencyManagement inherited from the parent** | Explicitly declare the version property (one line, below) |

Minimal example (observed in the W1 pilot: without this line baby-growth-claude's tomcat stayed at 10.1.55; adding the one line restored 10.1.59;
the import-only xia-go needed no configuration at all):

```xml
<properties>
  <!-- 仅 parent=spring-boot-starter-parent 时需要；Boot 官方管理版本 ≥10.1.59 后本行可移除 -->
  <tomcat.version>10.1.59</tomcat.version>
</properties>
```

Verification command (confirm the tomcat version actually resolved):

```bash
./mvnw dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
```

> `<tomcat.version>` is the **one sanctioned exception** to this guide's "do not override upstream versions" rule: it goes through Boot's official version-property channel
> (not a hand-rolled version number) and exists only to track security fixes.

---

## 3. FastDDD Maven Coordinate Migration (1.0.0 GA)

Starting with 1.0.0, Framework / Tooling migrate their Maven groupId: `com.gallop.fastddd` → `io.github.gallop.fastddd`.
This section is a **mandatory step** of the 1.0.0 upgrade, independent of the JDK/Boot baseline lift.

### 3.1 Coordinate Triad: What Changes, What Stays

| Dimension | Status | Notes |
|---|---|---|
| Maven groupId | **Changes**: `com.gallop.fastddd` → `io.github.gallop.fastddd` | All 28 released artifacts (including parent/BOM/plugins) |
| artifactId | **Unchanged** | `fast-ddd-*` (modules), `fastddd-*` (aggregates/BOM/Tooling) |
| Java package name | **Unchanged**: `com.gallop.fastddd.*` | **Zero changes to business source code and imports**; classpath locations remain `com/gallop/fastddd/**` |
| Version number | **Changes**: `1.0.0-jdk21-rc1` → `1.0.0` (RC1 consumers); `1.0.0-SNAPSHOT` consumers, see the contract-tightening assessment in Section 0 | Ships in the same release as the groupId migration |

> **Deliberately retained coordinates**: `com.gallop.fastddd:fastddd-studio*` (Studio private artifacts such as `fastddd-studio-server`)
> are **out of scope for this migration** — they keep the old groupId by design. Business projects do not consume these artifacts;
> this is also why the Section 3.5 gate **cannot** blanket-ban the entire old groupId.

### 3.2 Old/New Coordinate Mapping (all 28 released artifacts)

Upgrading = replace the `<groupId>` and `<version>` of the artifacts below with the new values; keep `<artifactId>` as-is.

**Framework side (17)**

| Category | artifactId | Old coordinates (rc1 and earlier) | New coordinates (1.0.0) |
|---|---|---|---|
| parent POM | `fastddd-framework-parent` | `com.gallop.fastddd:fastddd-framework-parent` | `io.github.gallop.fastddd:fastddd-framework-parent:1.0.0` |
| BOM | `fastddd-bom` | `com.gallop.fastddd:fastddd-bom` | `io.github.gallop.fastddd:fastddd-bom:1.0.0` |
| Aggregate (source-build entry, not a dependency) | `fastddd-framework` | `com.gallop.fastddd:fastddd-framework` | `io.github.gallop.fastddd:fastddd-framework:1.0.0` |
| Aggregate starter | `fast-ddd-starter` | `com.gallop.fastddd:fast-ddd-starter` | `io.github.gallop.fastddd:fast-ddd-starter:1.0.0` |
| Module | `fast-ddd-common` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-common:1.0.0` |
| Module | `fast-ddd-config` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-config:1.0.0` |
| Module | `fast-ddd-model` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-model:1.0.0` |
| Module | `fast-ddd-crud` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-crud:1.0.0` |
| Module | `fast-ddd-web` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-web:1.0.0` |
| Module | `fast-ddd-application` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-application:1.0.0` |
| Module | `fast-ddd-cache` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-cache:1.0.0` |
| Module | `fast-ddd-event` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-event:1.0.0` |
| Module | `fast-ddd-search` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-search:1.0.0` |
| Module | `fast-ddd-monitor` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-monitor:1.0.0` |
| Module | `fast-ddd-security-starter` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-security-starter:1.0.0` |
| Module (Incubating) | `fast-ddd-ai-spring-boot-starter` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-ai-spring-boot-starter:1.0.0` |
| Module (Incubating) | `fast-ddd-analyzer-spring-boot-starter` | Same pattern as above | `io.github.gallop.fastddd:fast-ddd-analyzer-spring-boot-starter:1.0.0` |

**Tooling side (11)**

| Category | artifactId | Old coordinates (rc1 and earlier) | New coordinates (1.0.0) |
|---|---|---|---|
| parent POM | `fastddd-tooling-parent` | `com.gallop.fastddd:fastddd-tooling-parent` | `io.github.gallop.fastddd:fastddd-tooling-parent:1.0.0` |
| Aggregate (source-build entry) | `fastddd-tooling` | `com.gallop.fastddd:fastddd-tooling` | `io.github.gallop.fastddd:fastddd-tooling:1.0.0` |
| Stable contract layer | `fastddd-tooling-api` | `com.gallop.fastddd:fastddd-tooling-api` | `io.github.gallop.fastddd:fastddd-tooling-api:1.0.0` |
| Code generation | `fastddd-code-generator` | `com.gallop.fastddd:fastddd-code-generator` | `io.github.gallop.fastddd:fastddd-code-generator:1.0.0` |
| Code generation templates | `fastddd-templates` | `com.gallop.fastddd:fastddd-templates` | `io.github.gallop.fastddd:fastddd-templates:1.0.0` |
| Scaffolding (Deprecated, retained for compatibility) | `fastddd-project-archetype` | `com.gallop.fastddd:fastddd-project-archetype` | `io.github.gallop.fastddd:fastddd-project-archetype:1.0.0` |
| Maven plugin (`fastddd:` prefix) | `fastddd-maven-plugin` | `com.gallop.fastddd:fastddd-maven-plugin` | `io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0` |
| Documentation generation | `fastddd-doc-generator` | `com.gallop.fastddd:fastddd-doc-generator` | `io.github.gallop.fastddd:fastddd-doc-generator:1.0.0` |
| Maven plugin (`fastdoc:` prefix) | `fastddd-doc-maven-plugin` | `com.gallop.fastddd:fastddd-doc-maven-plugin` | `io.github.gallop.fastddd:fastddd-doc-maven-plugin:1.0.0` |
| FastDoc core | `fastddd-fastdoc-core` | `com.gallop.fastddd:fastddd-fastdoc-core` | `io.github.gallop.fastddd:fastddd-fastdoc-core:1.0.0` |
| FastDoc runtime integration | `fastddd-fastdoc-spring-boot-starter` | `com.gallop.fastddd:fastddd-fastdoc-spring-boot-starter` | `io.github.gallop.fastddd:fastddd-fastdoc-spring-boot-starter:1.0.0` |

> Independent version axes: Framework and Tooling each release 1.0.0 on their own (the first certified tuple merely happens to share the same version — that is not lock-step).
> Historical legacy monorepo artifacts (old artifactIds not in the table above, such as `fast-ddd-code-generator`) are **not in the 1.0 migration list**;
> projects still using them should first migrate to the RC1/GA coordinate system.

### 3.3 POM Changes (RC1 → 1.0.0 minimal pattern)

Verified on two real W1 projects: for RC1 consumers the upgrade is just three POM-level replacements (zero source and configuration changes):

| Change point | Before | After |
|---|---|---|
| Version property/literal | `1.0.0-jdk21-rc1` | `1.0.0` |
| BOM import groupId (root pom) | `com.gallop.fastddd` | `io.github.gallop.fastddd` |
| Dependency groupId (each submodule pom) | `com.gallop.fastddd` | `io.github.gallop.fastddd` |

Equivalent bulk replacement (across all pom.xml files; note it matches only the full `<groupId>` line, so it never touches the business's own groupIds such as `com.gallop.xiago`):

```bash
find . -name pom.xml -exec sed -i \
  's|<fastddd\.version>1\.0\.0-jdk21-rc1</fastddd\.version>|<fastddd.version>1.0.0</fastddd.version>|; \
   s|<version>1\.0\.0-jdk21-rc1</version>|<version>1.0.0</version>|; \
   s|<groupId>com\.gallop\.fastddd</groupId>|<groupId>io.github.gallop.fastddd</groupId>|g' {} +
```

Do **not** globally replace the `com.gallop.fastddd` string: Java sources and imports (the `com.gallop.fastddd.*` package names) must stay;
`com.gallop.fastddd:fastddd-studio*` (if some toolchain references it) stays too. After replacing, verify zero residue:
`grep -rn -E "1\.0\.0-jdk21-rc1|<groupId>com\.gallop\.fastddd</groupId>" --include=pom.xml .`
(except lines related to `fastddd-studio*`).

### 3.4 Artifact Acquisition Channels (internal release repository)

1.0.0 is an **internal release**: the artifacts live in the internal release repository and in the local Maven repositories of already-synced development machines; public channels
(Maven Central, etc.) are not available yet. Pick one of the three shapes depending on what you can reach:

**Shape A — machines with a synced local repository**: no configuration needed; build directly (the local repository is a read-only copy and resolves normally).

**Shape B — point settings.xml at the internal repository** (file:// protocol, `<snapshots>` disabled):

```xml
<settings>
  <profiles>
    <profile>
      <id>fastddd-internal</id>
      <repositories>
        <repository>
          <id>fastddd-release</id>
          <url>${env.FASTDDD_INTERNAL_REPO}</url>   <!-- 环境变量注入，勿硬编码个人绝对路径 -->
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>fastddd-release-plugin</id>
          <url>${env.FASTDDD_INTERNAL_REPO}</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>fastddd-internal</activeProfile></activeProfiles>
</settings>
```

```bash
export FASTDDD_INTERNAL_REPO=file:///<local-release-root>/internal-v1.0.0/repository
mvn -B -s <settings-file-path-above> clean test
```

**Shape C — one-off explicit specification**: `mvn -s <internal-repository settings file> clean test` (the release ships with an accompanying
`settings-release.xml`). **Note**: that accompanying file is an example from the author's machine, with `<url>` hard-coded to the author's absolute path; on any other machine
you must first rewrite its `<url>` to `${env.FASTDDD_INTERNAL_REPO}` (as in Shape B) or to your machine's actual file:// path, otherwise artifacts will not resolve.

Once the public release (Maven Central) lands, this section ceases to apply automatically and consumers resolve directly via the public coordinates; at that point you only remove the internal-repository
profile — coordinates and versions stay the same.

### 3.5 Anti-Mixing Gates (Maven Enforcer bannedDependencies)

The biggest risk of the coordinate migration is **old/new mixing**: old `com.gallop.fastddd` artifacts coexisting on the classpath with new `io.github.gallop.fastddd` artifacts —
both carry identical class paths (`com/gallop/fastddd/**`), the jars shadow each other, and you get hard-to-diagnose
runtime behavior anomalies. Moreover, **the local repository contains a `1.0.0` version directory for the same artifactId under the old group**, so a "version numbers agree" check
cannot block old-group resolution — the gate must decide by **coordinate existence**.

Add this to the business project's root pom (or parent POM):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <id>ban-legacy-fastddd</id>
          <goals><goal>enforce</goal></goals>
          <configuration>
            <rules>
              <bannedDependencies>
                <searchTransitive>true</searchTransitive><!-- 覆盖传递依赖，默认即 true，显式声明以自明 -->
                <excludes>
                  <!-- Framework 侧（17） -->
                  <exclude>com.gallop.fastddd:fastddd-framework-parent</exclude>
                  <exclude>com.gallop.fastddd:fastddd-bom</exclude>
                  <exclude>com.gallop.fastddd:fastddd-framework</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-starter</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-common</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-config</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-model</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-crud</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-web</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-application</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-cache</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-event</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-search</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-monitor</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-security-starter</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-ai-spring-boot-starter</exclude>
                  <exclude>com.gallop.fastddd:fast-ddd-analyzer-spring-boot-starter</exclude>
                  <!-- Tooling 侧（11） -->
                  <exclude>com.gallop.fastddd:fastddd-tooling-parent</exclude>
                  <exclude>com.gallop.fastddd:fastddd-tooling</exclude>
                  <exclude>com.gallop.fastddd:fastddd-tooling-api</exclude>
                  <exclude>com.gallop.fastddd:fastddd-code-generator</exclude>
                  <exclude>com.gallop.fastddd:fastddd-templates</exclude>
                  <exclude>com.gallop.fastddd:fastddd-project-archetype</exclude>
                  <exclude>com.gallop.fastddd:fastddd-maven-plugin</exclude>
                  <exclude>com.gallop.fastddd:fastddd-doc-generator</exclude>
                  <exclude>com.gallop.fastddd:fastddd-doc-maven-plugin</exclude>
                  <exclude>com.gallop.fastddd:fastddd-fastdoc-core</exclude>
                  <exclude>com.gallop.fastddd:fastddd-fastdoc-spring-boot-starter</exclude>
                </excludes>
                <message>检出旧 FastDDD Community 坐标（com.gallop.fastddd）与 1.0.0 新坐标混装：请将直接依赖与传递依赖统一迁移到 io.github.gallop.fastddd（UPGRADE_GUIDE 第 3 节）。fastddd-studio* 为 Studio 保留坐标，不在禁用清单。</message>
              </bannedDependencies>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Semantic fine points:

- **Ban by coordinate, not by version**: an exclude without a version segment means that artifactId is disallowed under the old group at **any version**
  (including old-group `1.0.0` — it really exists in local repositories and would slip past a version-consistency check).
- **Never write `<exclude>com.gallop.fastddd</exclude>` or `<exclude>com.gallop.fastddd:*</exclude>`**:
  it would wrongly hit `fastddd-studio*` (Studio's retained coordinates) and the business's own `com.gallop.*` groupIds.
- `searchTransitive=true` extends the rule to **transitive dependencies** — the most common mixing source is some dependency library still on the old coordinates.

**List boundary**: the 28 entries above = the artifactIds of the **migrated modules** (artifacts whose artifactId survived the migration unchanged). Under the old group
`com.gallop.fastddd` there are also **renamed/split/retired** coordinates that are not on this list: the old root aggregator `fastddd` (renamed during the migration to
`fastddd-framework-parent`/`fastddd-tooling-parent`; the old artifactId is deprecated), and retired legacy monorepo artifacts such as
`fast-ddd-examples`, `fastddd-consumer-tests`, `fast-ddd-core`, `fast-ddd-demo-app`, `fast-ddd-code-generator`.
Consumers never reference these coordinates as runtime module dependencies: if the old root `fastddd` is referenced at all, it only appears in `<parent>` (covered by manual check 1
below, the effective-pom review), and retired jars sneaking in are covered by check 3, the final-package inspection. Hence the 28 entries do not cover the old group's **entire** historical
release surface — they cover the artifacts "that still exist under the same artifactId after the migration and would enter the dependency tree", which is exactly the flip side of "you cannot blanket-ban the whole old groupId".

**Dead-entry note**: in the list, `fastddd-framework-parent`/`fastddd-bom`/`fastddd-framework`/`fastddd-tooling-parent`/
`fastddd-tooling`/`fastddd-project-archetype`/`fastddd-maven-plugin`/`fastddd-doc-maven-plugin` are parent/BOM/
aggregate/plugin coordinates that **never appear in the dependency tree**, so bannedDependencies will never match them (they are listed only as placeholders for the completeness of the 28-artifact list);
those categories are covered by the manual effective-pom/plugin-reference checks below — do not infer from "Enforcer passed" that parent/BOM/plugins are also clean.

Three manual/scripted checks beyond Enforcer (places bannedDependencies cannot reach):

1. **Parent references**: the `effective-pom`'s `<parent>` contains no `com.gallop.fastddd` (Enforcer does not validate the parent chain):
   ```bash
   ./mvnw -B help:effective-pom -Doutput=effective.xml
   grep -n "com.gallop.fastddd" effective.xml   # 期望仅 fastddd-studio* 或 0 命中
   ```
2. **BOM imports and plugin references**: the `<dependencyManagement>` imports and the Tooling plugins in `<build><plugins>`
   (`fastddd-maven-plugin`/`fastddd-doc-maven-plugin`) have all switched coordinates (the effective-pom review covers these too).
3. **Final-package artifact surface**: for each fastddd jar under `BOOT-INF/lib` in the boot jar, check the `groupId=` inside the embedded
   `pom.properties` (same artifactId plus same version across the old and new groups yields **completely identical file names** — jar-internal metadata is the only way to tell):
   ```bash
   unzip -l target/*.jar | grep -E "BOOT-INF/lib/fast-?ddd.*\.jar"    # 清点 fastddd 制品
   unzip -p target/*.jar BOOT-INF/lib/fast-ddd-common-1.0.0.jar > /tmp/fdc.jar
   unzip -p /tmp/fdc.jar "META-INF/maven/*/fast-ddd-common/pom.properties" | grep groupId
   # 期望输出 groupId=io.github.gallop.fastddd；出现 com.gallop.fastddd 即混装（fastddd-studio* 除外）
   ```

### 3.6 Four-Layer Upgrade Acceptance Check

1. **Dependency tree**: `./mvnw -B dependency:tree` → every fastddd artifact is `io.github.gallop.fastddd:*:jar:1.0.0`,
   zero hits for the old group, zero hits for SNAPSHOT (Enforcer already automates this layer; the tree is for producing evidence);
2. **effective-pom**: no old-group references across the three places — parent/BOM import/plugins (verification commands in Section 3.5);
3. **Final package**: no old-group fastddd jars in the boot jar's `BOOT-INF/lib`, and `pom.properties` groupId is correct;
4. **Business smoke test**: run the regression checklist in Section 6 (connection pool initialization line, `/actuator/health` UP, core use cases).

Four-layer evidence from the two real W1 projects (6 modules SUCCESS + 15 tests + a zero-mixing dependency tree) is in the release evidence
`internal-v1.0.0/evidence/UPGRADE-VERIFY-w1.md`.

---

## 4. Dependency Coordinates Changed by the Migration

### MySQL Driver

```diff
- <dependency>
-   <groupId>mysql</groupId>
-   <artifactId>mysql-connector-java</artifactId>
- </dependency>
+ <dependency>
+   <groupId>com.mysql</groupId>
+   <artifactId>mysql-connector-j</artifactId>
+   <!-- 版本由 Spring Boot BOM 管理（9.7.0），无需声明 -->
+ </dependency>
```
> The 9.7.0 driver is backward compatible with MySQL Server 8.x; connecting to 9.x servers is also supported.

### Druid

```diff
- <dependency>
-   <groupId>com.alibaba</groupId>
-   <artifactId>druid-spring-boot-starter</artifactId>   <!-- Boot 2 坐标 -->
- </dependency>
+ <dependency>
+   <groupId>com.alibaba</groupId>
+   <artifactId>druid-spring-boot-3-starter</artifactId>  <!-- Boot 3 坐标 -->
+   <!-- 版本由 fastddd-bom 管理（1.2.28） -->
+ </dependency>
```

### Web/CRUD Dependency Boundary (explicit connection pool selection; applies to version lines containing the ECO-006C4 remediation)

`fast-ddd-crud` no longer passes along a connection pool or the official MyBatis-Plus starter (nor the `spring-boot-starter-jdbc`/HikariCP chain);
instead it carries the MyBatis-Plus runtime capability itself (`mybatis-plus` bundle + `mybatis-spring` + MyBatis-Plus auto-configuration + `spring-jdbc`);
`druid-spring-boot-3-starter` becomes optional in crud. The aggregate `fast-ddd-starter` explicitly carries Druid
(the "include it and you get the default pool" semantics are unchanged). Self-check by consumption shape:

| Consumption shape | Impact | Required action |
|---|---|---|
| Aggregate `fast-ddd-starter` | Default pool is still Druid | None |
| `fast-ddd-web`/`fast-ddd-crud` only, **with a database** | No transitive connection pool on the classpath anymore | **Explicitly choose a pool**: `druid-spring-boot-3-starter` (keep Druid) or `spring-boot-starter-jdbc` (Hikari) — exactly one |
| `fast-ddd-web` only, **no database** | Changes from "must configure a datasource to start" to "starts normally" (a benefit) | None |
| `fast-ddd-model` only | Carries only `mybatis-plus-annotation` (annotations) | None |

> **⚠️ Hidden failure mode without a pool**: when `spring.datasource.url` is configured but the classpath has no connection pool at all,
> DataSource auto-configuration **silently backs off** — the application starts as usual, no error, but the container has **no DataSource/JdbcTemplate/Mapper beans**;
> the failure only surfaces on first data access (`NoSuchBeanDefinitionException`). After upgrading, always verify the connection pool initialization line in the startup log
> (Druid `{dataSource-1} inited` or `HikariPool-1 - Start completed`).

Verification command:

```bash
./mvnw dependency:tree -Dincludes=com.alibaba:druid-spring-boot-3-starter,com.zaxxer:HikariCP,org.springframework.boot:spring-boot-starter-jdbc
```

If the project also brings its own `mybatis-plus-spring-boot3-starter` and previously relied on the Druid passed along by crud, the pool type flips to Hikari after the upgrade
(functionally fine — just a pool-type flip); to keep Druid, explicitly add `druid-spring-boot-3-starter` as in the table above.

### Event Dependency Boundary (pool neutrality; applies to version lines containing the ECO-006C6 remediation)

`fast-ddd-event` no longer depends directly on `mybatis-plus-spring-boot3-starter` (nor its `spring-boot-starter-jdbc`/HikariCP chain);
instead it passes along the four-piece MyBatis-Plus runtime capability that Outbox actually requires (`mybatis-plus` bundle + `mybatis-spring` +
MyBatis-Plus auto-configuration + `spring-jdbc`, all pool-neutral). The default local mode (no database, no configuration, no exclusions) works out of the box;
in outbox mode the consumer provides the connection pool explicitly. Self-check by consumption shape:

| Consumption shape | Impact | Required action |
|---|---|---|
| `fast-ddd-event` only, local mode (default) | Changes from "must configure a datasource / exclude auto-configuration to start" to "starts normally" (a benefit) | None (existing `spring.autoconfigure.exclude` entries can stay — no conflict) |
| `fast-ddd-event`, outbox mode | No transitive connection pool on the classpath anymore | **Explicitly choose a pool**: `spring-boot-starter-jdbc` (Hikari) or `druid-spring-boot-3-starter`, and provide your own `outbox_events` DDL |
| `fast-ddd-event`, outbox mode, **non-web** | `JacksonAutoConfiguration`'s `ObjectMapper` needs the spring-web builder, which is absent | The consumer provides its own `ObjectMapper` (recommended: `findAndRegisterModules()`) and explicitly depends on `jackson-datatype-jsr310` (`DomainEvent.createdAt` is an `Instant`; web consumers get it automatically via starter-json — nothing to do) |

> **⚠️ Same silent pool back-off pattern as the previous section**: when `spring.datasource.*` is configured but no pool implementation exists, outbox wiring backs off entirely
> (`@ConditionalOnSingleCandidate(DataSource)` does not hold); startup reports no error but there is no outbox bean — check the connection pool initialization line in the startup log.

Verification command (confirm event no longer passes along a pool or the official starter):

```bash
./mvnw dependency:tree -Dincludes=com.zaxxer:HikariCP,com.alibaba:druid-spring-boot-3-starter,com.baomidou:mybatis-plus-spring-boot3-starter
```

### Security Dependency Boundary (audit persistence minimization; applies to version lines containing the ECO-006C2 remediation)

`fast-ddd-security-starter` no longer passes along the official MyBatis-Plus starter or its connection pool chain (`spring-boot-starter-jdbc`/HikariCP):
the module's use of MyBatis-Plus is only annotations on audit entities (kept visible via `mybatis-plus-annotation`), and audit writes go through a
hand-written `JdbcTemplate` INSERT (`spring-jdbc` provides only the type signatures). Self-check by consumption shape:

| Consumption shape | Impact | Required action |
|---|---|---|
| security-starter only, **no database** | No DataSource bean and no startup failure: auditing automatically falls back to SLF4J output (database-less audit fallback) | None |
| security-starter + database auditing | Requires DataSource/JdbcTemplate beans | **Explicitly choose a pool** (same rule as Web/CRUD and Event): `druid-spring-boot-3-starter` or `spring-boot-starter-jdbc` — exactly one |
| Project brings its own Security components | The framework's `@ConditionalOnMissingBean` back-off family (PasswordEncoder, SecurityFilterChain, etc.) yields to the product's wiring | None (product beans win; both jwt states start) |

> **Audit table column width and existing-table migration**: the audit persistence `method` column has been widened from `VARCHAR(10)` to `VARCHAR(200)`; table structures are created by
> the framework's automatic DDL; **the framework will not automatically ALTER existing user databases** — for the migration path for existing narrow-column tables, see
> [security/AUDIT_LOG_TABLE_MIGRATION.md](security/AUDIT_LOG_TABLE_MIGRATION.md).

Verification command (confirm security no longer passes along the official starter or a pool):

```bash
./mvnw dependency:tree -Dincludes=com.baomidou:mybatis-plus-spring-boot3-starter,com.zaxxer:HikariCP,org.springframework.boot:spring-boot-starter-jdbc
```

---

## 5. Configuration Review (Boot 3.5 strict parsing)

Boot 3.5's SnakeYAML moves from tolerating to strictly rejecting **duplicate top-level YAML keys**. Before upgrading, self-check `application*.yml`:

- Duplicate top-level keys (e.g. two `fastddd:` blocks, or two `spring:` blocks merged improperly) → merge into a single key.
- `.properties` files are unaffected, but cleaning up duplicate keys is still recommended.

Druid's auto-configuration under Boot 3.5 is provided by `druid-spring-boot-3-starter`; once the starter coordinates are correct (see the previous section),
the existing `spring.datasource.druid.*` configuration needs no changes.

### Local Secret Configuration and Pre-Startup Checks

Tracked `application-*.yml` files may only reference environment variables, for example `${JWT_SECRET}`; actual values live in the ignored `.env`, a managed secret service, or IDE/CI environment variables. Committing an `.env.example` is recommended, but it must contain no usable secrets.

If the application supports `.env` auto-loading, be explicit about the relationship between the load path and the process working directory. If an old configuration contains `${LLM_API_KEY:<old-default-value>}`, do not copy the whole expression into `LLM_API_KEY` during migration — that creates a circular placeholder; fill in the final rotated value instead.

FastDDD 1.0 uses HS512 for the default HMAC JWT. `JWT_SECRET` must be at least 64 UTF-8 bytes; short keys from old projects are rejected before the application context is created — this is a security misconfiguration to fix, not a compatibility issue to work around.

---

## 6. Build and Regression

```bash
# 1. 全量编译
./mvnw -B clean compile

# 2. 默认单元测试（不依赖共享数据库或真实第三方服务）
./mvnw -B clean test

# 3. 打包
./mvnw -B clean package -DskipTests
```

Tests against real databases, messaging, or third-party services should use a separate Failsafe profile (typically named `*IT` or `*IntegrationTest`), run only after the required variables are injected in a controlled environment, for example:

```bash
./mvnw -B -P integration-test verify
```

When running `spring-boot:run` from a standalone launcher module, first complete a full-Reactor `install`, then run from the module containing the main class; do not run that goal on the aggregate root POM. Startup acceptance must check both the listening port and that `/actuator/health` returns `UP`.

### Regression Checklist

- [ ] The application starts normally (logs contain `Spring Boot v3.5.16` and Druid `{dataSource-1} inited`).
- [ ] Database CRUD works (MySQL driver 9.7.0 + Druid 3-starter).
- [ ] API documentation endpoints are reachable (SpringDoc 2.8.17 / Knife4j 4.5.0: `/v3/api-docs`, `/doc.html`).
- [ ] Security authentication (JWT / verification code) flows work (fast-ddd-security-starter).
- [ ] Monitoring metrics (`/actuator/prometheus`, Micrometer 1.15.12) output correctly.
- [ ] The code generation plugin works (only if the project actually uses it): `./mvnw io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:help`.
- [ ] Full regression of the project's own core business use cases (business-side led).

---

## 7. FAQ

**Q: The build reports "unsupported release version 21"?**
A: The build is not running on JDK 21. Explicitly switch `JAVA_HOME` to JDK 21 as in Section 1 (same for CI runners).

**Q: Startup reports `while scanning a simple key` / a duplicate-key parsing error?**
A: `application*.yml` has duplicate top-level keys; merge them per Section 5.

**Q: `druid-spring-boot-starter` auto-configuration not found / the Druid connection pool not taking effect?**
A: You are still on the Boot 2 coordinates `druid-spring-boot-starter`; migrate to `druid-spring-boot-3-starter` per Section 4.

**Q: After the upgrade the application starts fine, but Mapper/`JdbcTemplate` injection throws `NoSuchBeanDefinitionException`?**
A: Since ECO-006C4, `fast-ddd-crud` no longer passes along a connection pool. Projects that use only web/crud and need a database must explicitly choose a pool
(`druid-spring-boot-3-starter` or `spring-boot-starter-jdbc`); see "Web/CRUD Dependency Boundary" in Section 4 —
without a pool, DataSource auto-configuration silently backs off and startup reports no error.

**Q: After the upgrade the connection pool changed from Druid to Hikari (`HikariPool` shows up in the logs)?**
A: The project brings its own `mybatis-plus-spring-boot3-starter` (which carries the Hikari chain), while Druid previously came transitively from crud.
That transitive path is gone, and the old "Druid wins when both pools are present" default no longer exists; to keep the Druid pool type and the existing
`spring.datasource.druid.*` configuration, explicitly add `druid-spring-boot-3-starter`.

**Q: `com.mysql:mysql-connector-j` cannot be resolved?**
A: The old coordinates `mysql:mysql-connector-java` are deprecated; migrate per Section 4 and let the Spring Boot BOM manage the version.

**Q: Is changing dependencies alone enough?**
A: Projects that use only the Starter and whose `clean compile`, tests, startup, and critical paths all pass may need only the dependency and configuration migration; do not assume this up front. If generic/ID compile errors appear on `BasePO`, `IService`, `BaseServiceImpl`, `BaseController`, load the ID migration checklist via the [upgrade entry point](upgrade/README.md) and complete the business-side adaptation. Do not modify Framework contracts to preserve old call sites.

**Q: At startup the application reports `JWT_SECRET` is under 64 bytes?**
A: The FastDDD 1.0 HS512 validation is in effect. Generate and inject a dedicated key of at least 64 UTF-8 bytes for the environment; do not restore the old short key or weaken the validation.

**Q: Configuration reports `Circular placeholder reference` involving the LLM API Key?**
A: Check whether `.env` copied an old nested YAML placeholder such as `${LLM_API_KEY:...}` verbatim into `LLM_API_KEY`. `.env` should hold the final value, not a Spring placeholder expression.

**Q: `spring-boot:run` reports the main class cannot be found, or single-module compilation shows old interfaces?**
A: The former usually means running from the aggregate root instead of the launcher module; the latter usually means the single module resolved a stale local business JAR. Run a full-Reactor `install` first, then run from the module containing the main class.

**Q: Enforcer's bannedDependencies flags `com.gallop.fastddd:*`?**
A: Some direct or transitive dependency is still on the old coordinates. Use `./mvnw dependency:tree -Dincludes=com.gallop.fastddd`
to locate where it is introduced and upgrade that dependency to its migrated version; for third-party libraries that cannot be upgraded right away, use `dependencyManagement`
to force-replace the old coordinates with their new-coordinate equivalents. Note that `fastddd-studio*` hits are Studio's retained coordinates, not on the ban list, and will not trigger this error.

**Q: The local repository has `com.gallop.fastddd:fast-ddd-common:1.0.0` — same version number, so why does it still count as mixing?**
A: It does. After the coordinate migration the groupId is the identity — old-group `1.0.0` and new-group `1.0.0` are two different artifacts, and jars with identical class paths
shadow each other. That is exactly why the gate decides by coordinate existence (no version segment).

---

## 8. Rollback

The upgrade proceeds on a **single branch**; if problems arise, roll back to the pre-upgrade branch/tag. On the FastDDD source side, do not use `git reset --hard`;
roll back single files with `git restore <file>`; on the business-system side, roll back dependency versions per your own version-control policy.

### 8.1 Coordinate Migration Rollback Procedure

Rolling back 1.0.0 → RC1 = undoing the three POM replacements from Section 3.3 (property/literal, BOM groupId, dependency groupId);
no source changes needed. This channel was rehearsed and verified on the two W1 projects (after rollback, versions, pool types, and behavior all returned to baseline).

### 8.2 Rollback Boundaries and Known Risks (Important)

- **Rolling back to RC1 / an old SNAPSHOT in production is not recommended**: the Tomcat 10.1.55 that the old version line resolves by default
  carries known vulnerabilities (1.0.0 has converged onto the 10.1.59 fix line through both channels — the explicitly managed BOM version and the Boot property),
  and the old SNAPSHOT line is not supported at all. The rollback rehearsal proves the **downgrade channel works** — it is not a recommendation to roll back.
- The transitive-dependency governance included in 1.0.0 (crud/event/security connection pool explicitness, see Section 4) reverts to the old "carried transitively"
  behavior after a rollback — rollback is not a pure version-number change; re-verify pool configuration and startup logs per Section 4.
- For product defects found after release, the official path is to **roll forward with a new patch version (1.0.1+)**: the internal 1.0.0 artifacts and tags
  are immutable — they cannot be moved or overwritten.

