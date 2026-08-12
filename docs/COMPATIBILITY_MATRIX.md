# 兼容矩阵（Compatibility Matrix）

> 本文档定义 FastDDD 各版本线的运行时基线、维护状态与变更边界，是业务系统选择/升级版本的依据。
> 升级步骤见 [UPGRADE_GUIDE.md](UPGRADE_GUIDE.md)；发布历史见 [../CHANGELOG.md](../CHANGELOG.md)。

## 版本线基线

| FastDDD | Java（构建+运行） | Spring Boot | Maven | 状态 |
|---------|-------------------|-------------|-------|------|
| 升级前开发快照 | 17 | 3.4.5 | 3.9.x | 🔒 仅 Git tag `baseline/jdk17-boot3.4.5` 归档，**不维护** |
| **1.0.x** | **21** | **3.5.x**（当前 3.5.16） | 3.9.x（wrapper pin 3.9.5） | ✅ **当前稳定线**（首个 GA 见 RELEASE_CHECKLIST） |
| 1.1.x | 21 | 3.5.x | 3.9.x | 📋 计划（兼容性增强：japicmp/Revapi、BOM 单一来源、模板切 BOM） |
| 2.0.x | 待定 | 4.x | 待定 | 🔭 未来规划（Spring Boot 4 / Spring Framework 7 / Jackson 3 迁移） |

## 关键依赖矩阵（1.0.x 线，经 `dependency:tree` 实证）

| 依赖 | 版本 | 来源 |
|------|------|------|
| Spring Framework | 6.2.19 | Spring Boot BOM |
| Spring AI | 1.1.8 | 显式 BOM import（Boot 3.5.x 对应 1.1 维护线） |
| MyBatis-Plus（starter/jsqlparser/generator） | 3.5.12 | 显式 dependencyManagement |
| MyBatis-Spring | 3.0.3 | 显式 dependencyManagement |
| Druid | 1.2.28（`druid-spring-boot-3-starter`） | 显式 dependencyManagement |
| MySQL 驱动 | 9.7.0（`com.mysql:mysql-connector-j`） | Spring Boot BOM |
| Micrometer（core/observation/registry-prometheus） | 1.15.12 | Spring Boot BOM |
| Jackson | 2.21.x | Spring Boot BOM |
| SpringDoc OpenAPI | 2.8.17 | 显式 dependencyManagement |
| Knife4j | 4.5.0 | 显式 dependencyManagement |
| SLF4J | 2.0.18 | Spring Boot BOM |
| Lombok | 1.18.46 | Spring Boot BOM |

> 业务项目 **不要覆盖** 上表版本（尤其 Spring Boot、Spring、Jackson）——它们经 FastDDD 与上游 BOM 联调验证，
> 覆盖可能引入版本分裂。如需统一管理，`import` [`fastddd-bom`](../README.md) 即可。

## 跨产品兼容矩阵（Studio → Tooling → Framework）

> 本节是 FastDDD **三产品（Studio / Tooling / Framework）版本组合**的人类可读镜像。
> **机器可读规范源**：[`fastddd-tooling-api/src/main/resources/fastddd-compatibility-matrix.yaml`](../fastddd-tooling/fastddd-tooling-api/src/main/resources/fastddd-compatibility-matrix.yaml)。

FastDDD 当前为单仓单体——Studio / Tooling / Framework 共享同一 `${revision}` 属性（锁步发版）。
ADR-005 §3.2 已确立：三产品**版本独立分号是拆仓后事项**（"当前共享 `${revision}` 是单体期便利，拆仓后独立"）。
VER-001 以**机器可读矩阵 + 构建期校验**作为承载机制：拆仓后新增差异化元组即可表达独立发版节奏，
CI 据此阻断未登记的不兼容组合，无需改动 reactor 结构。

### 合法版本组合（compatible-tuples）

| Studio | Tooling | Framework | 状态 |
|--------|---------|-----------|------|
| 1.0.0-jdk21-SNAPSHOT | 1.0.0-jdk21-SNAPSHOT | 1.0.0-jdk21-SNAPSHOT | ✅ current（单体期锁步） |

构建期由 `VersionGovernanceContractTest#currentReactorTupleIsRegisteredInMatrix_step6` 断言当前 reactor 元组
`(revision, revision, revision)` 在表内；拆仓后新增元组行即放行新的独立组合。

### Framework 平台 manifest（framework-lines）

每个 Framework 版本线携带平台依赖基线，由 `VersionGovernanceContractTest#matrixManifestMatchesBaseline_step1`
在构建期断言与上表"关键依赖矩阵"一致：

| Framework 线 | 状态 | Java | Spring Boot | Spring AI | SpringDoc | Knife4j |
|--------------|------|------|-------------|-----------|-----------|---------|
| 1.0.0-jdk21 | current-stable | 21 | 3.5.16 | 1.1.8 | 2.8.17 | 4.5.0 |

### 生成通道如何取目标 Framework 版本（VER-001 步骤 5）

三条项目生成通道从本矩阵取**目标 Framework 版本**，**不复制 Studio/Tooling 自身版本**：

| 通道 | 目标版本来源 | 矩阵校验 |
|------|--------------|----------|
| Maven Plugin（`InitProjectMojo`） | `fastddd-version.properties` `@revision@` 过滤 → 当前构建 Framework 版本 | 解析后 `CompatibilityMatrix.manifestFor()` 校验，未知则 WARN |
| Studio 运行时（`ProjectGeneratorService` 等） | `FastdddVersionDefaults.CURRENT` 字面量回落 + `@Value("${fastddd.version}")` 可覆盖 | `@PostConstruct` 校验 `manifestFor(fastdddVersion)`，未知则 WARN |
| Archetype（deprecated） | `archetype-metadata.xml` `fastdddVersion` requiredProperty（用户可覆盖） | — |

Studio 侧 `FastdddVersionDefaults.CURRENT` 字面量由 `FastdddVersionDefaultsContractTest` 机械耦合到矩阵
`current-stable` 线——升级 Framework 版本线时改矩阵 + 该常量即感知漂移（`@Value` 默认须编译期常量，故保留字面量回落）。

## Studio 构建隔离（SPLIT-005）

> 让 Studio Server 具备**迁出 reactor 后独立构建**的能力（单仓内，为安全物理拆仓做准备）。

Studio 此前深度耦合 FastDDD 根 pom（parent 继承根、依赖根属性、`application.yml` 的 `@...@` token 依赖根 profile）。SPLIT-005 把 studio 迁到与业务消费者同构的**自洽形态**：

- **`fastddd-studio`（studio-parent）**：parent 改为 `spring-boot-starter-parent`（提供 Boot 插件管理 + `@..@` 资源过滤 + 编译器默认），经 `import fastddd-bom` 管 Framework/Tooling 依赖（version-less），自声明 `<revision>` 与根/BOM 锁步（ADR-005 §3.2：单体期值锁步、机制解耦——拆仓后可独立分号）。
- **`fastddd-studio-server`**：继承 studio-parent（`relativePath ../pom.xml` 解析 `${revision}`），不再继承 FastDDD 根 pom。

**版本独立化时序**（ADR-005 §3.2）：Studio「当前共享 `${revision}` 是单体期便利，拆仓后独立」。SPLIT-005 交付的是**构建血缘隔离**（切断根 parent 继承），这是独立发版能力的真前置；差异化 per-product revision 属拆仓后事项（承载机制已由本节上方兼容矩阵 `compatible-tuples` 提供）。

**隔离验证命令**（gated，默认不触发，标准 `verify` 门禁零影响）：

```bash
# 1. 装入全部 fastddd 制品到本地仓库
mvn install -DskipTests -B
# 2. forked Maven 构建真 studio-server（非 host reactor，仅依赖已 install 制品）
mvn verify -pl fastddd-consumer-tests -Pstudio-isolation -B
```

该 invoker 构建在隔离 forked 进程内克隆真实 `fastddd-studio` 树并构建 `fastddd-studio-server`——其 framework/tooling 依赖仅从本地仓库（已发布 RC 制品）解析，证明「删除根 reactor 聚合后仍能独立构建」。

**构建期守卫**（随 `clean verify` 真实门禁，`VersionGovernanceContractTest`）：
- `studioParentRevisionMatchesRoot_step3`：根 pom == BOM == studio-parent 三方 `<revision>` 一致。
- `studioParentDoesNotInheritFastdddRoot_step1`：studio-parent `<parent>` 非 fastddd 根 + import fastddd-bom。

## 变更边界（语义化版本约定）

为避免"把破坏性变更当作普通 minor 升级"，FastDDD 对各版本线变更边界作如下承诺：

| 变更类型 | 所属版本线 | 含义 |
|----------|------------|------|
| **PATCH（x.y.Z）** | 同一 minor 线 | 仅 bug 修复、依赖补丁升级（如 Boot 3.5.16 → 3.5.17）、文档。**不引入任何 API 破坏**。 |
| **MINOR（x.Y.0）** | 同一 major 线 | 新增功能、新增模块、依赖 minor 升级。**保持向后二进制兼容**（1.0.1 起由 japicmp/Revapi 校验）。 |
| **MAJOR（X.0.0）** | 跨 major 线 | 允许破坏性变更（如 Spring Boot 4 / Spring 7 / Jackson 3 迁移）。**不作为普通 minor 推送**，需配套专门升级指南。 |

### 不允许的做法

- ❌ 在 PATCH/MINOR 中升级 **Java 主版本**（Java 21 → 22+）。
- ❌ 在 PATCH/MINOR 中升级 **Spring Boot 主/次版本**（3.5.x → 3.6.x / 4.x）。
- ❌ 在 1.0.x 线引入 Spring Boot 4 / Spring Framework 7 / Jackson 3 改动。
- ❌ 在 PATCH/MINOR 中删除或更改公开 API 签名（1.0.1 起由二进制兼容校验强制）。

### 允许的做法

- ✅ PATCH：依赖补丁升级、bug 修复、性能优化、文档。
- ✅ MINOR：新增模块/功能、新增可选依赖、依赖 minor 升级（经联调验证）。

## 构建与运行要求

| 项 | 要求 |
|----|------|
| 构建 JDK | **21+**（硬前置；`<release>21`，产物 major version 65） |
| 运行 JRE | **21+**（与构建一致） |
| Maven | **3.9.0+**（Enforcer 强制；wrapper pin 3.9.5） |
| Enforcer 门禁 | `requireJavaVersion[21,)` · `requireMavenVersion[3.9.0,4.0.0)` · `requirePluginVersions` · `banDuplicatePomDependencyVersions` |

> ⚠️ **JDK 21 硬前置**：若机器默认 `JAVA_HOME` 指向 JDK 17，Maven 会报"不支持的发行版本 21"直接 BUILD FAILURE。
> 构建前须显式切换至 JDK 21（CI runner 同理）。

## 版本切版流程（VER-001 步骤 8）

FastDDD 采用 CI-friendly 版本治理（`${revision}` 单属性 + flatten-maven-plugin），切版无需改动任何模块 pom——
仅覆盖 `revision` 属性即可。**`-jdk21` 限定符是 JDK21 产品线信号，切版时须保留**（UPG-010 + 本矩阵确立）。

> 注：Framework 版本当前**不会 GA**（`1.0.0-jdk21-SNAPSHOT`，无生产消费者）；下列流程是机制文档化，供 RC 候选/未来发版时照做。

### 切 RC 候选

```bash
# JDK 21 硬前置
export JAVA_HOME="D:/usr/local/jdk/jdk-21.0.3"; export PATH="$JAVA_HOME/bin:$PATH"

# 覆盖 revision → RC 字面量；flatten 在 install/deploy 时字面量化产物
./mvnw -B clean verify -Ptest -Drevision=1.0.0-jdk21-rc1
./mvnw -B install -DskipTests -Drevision=1.0.0-jdk21-rc1
```

切 RC 后须**同步**：
1. `fastddd-compatibility-matrix.yaml` 的 `framework-lines[].revision` + `compatible-tuples[].framework/tooling/studio`；
2. `FastdddVersionDefaults.CURRENT`（Studio 回落字面量）；
3. `VersionGovernanceContractTest#matrixManifestMatchesBaseline_step1` / `FastdddVersionDefaultsContractTest` 会自动
   捕获矩阵与 reactor 的漂移（步骤 6/7 守卫）。

### 切下一产品线（如 1.1.0-jdk21-SNAPSHOT）

```bash
# 1. 根 pom + BOM pom 的 <revision> 属性改为新字面量（BOM 无 parent，须手工同步——步骤 7 测试守护）
# 2. 矩阵新增 framework-line + compatible-tuple 行
# 3. 更新 FastdddVersionDefaults.CURRENT
# 4. 全量门禁：./mvnw -B clean verify -Ptest
```

`rootRevisionMatchesBomRevision_step7` 断言根 pom 与 BOM 两处 `<revision>` 字面量相等；
`scripts/verify-version-consistency.py` 扫描全仓 pom 断言无模块硬编码版本字面量（须经 `${revision}`）。
