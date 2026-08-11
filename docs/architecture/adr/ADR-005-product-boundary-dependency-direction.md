# ADR-005：产品边界、依赖方向、版本与许可证

| | |
|---|---|
| **状态** | 已接受（Accepted）—— 冻结结论，执行分散到 SPLIT-002 及后续任务 |
| **日期** | 2026-08-11 |
| **关联任务** | SPLIT-001（本 ADR）/ SPLIT-002（消除反向依赖）/ OSS-001（许可证终审）/ SPLIT-005（Studio 隔离） |
| **承重决策来源** | 项目记忆 `refactor-program-decisions`：Framework = Apache-2.0；ID 删旧约；分支隔离；RC 对内 GA 才公开 |

## 1. 背景与动机

FastDDD 1.0 仓库目前是单 reactor、单 groupId、单版本（`${revision}=1.0.0-jdk21-SNAPSHOT`）的 26 个叶模块 + 4 个聚合 pom 的单体。产品形态实际是三类职责的混合：

- **契约层（Framework）**：开源 DDD 框架 + BOM，是对外消费者的稳定 API。
- **能力层（Tooling）**：代码/文档生成器、Maven 插件、archetype、模板——开发期工具链。
- **聚合层（Studio）**：设计工作室产品（server + UI）——运行期商业产品。

三类职责混在一个 reactor 里，已产生 **Framework 反向依赖 Tooling** 的耦合（`fast-ddd-web` → `fastddd-fastdoc-core`，详见 §4）。若不冻结边界，后续 P0 整改会继续扩大耦合，且未来物理拆仓时无法保证业务系统 Maven 坐标稳定。

本 ADR 冻结三层边界、依赖方向、坐标/版本/BOM 归属、许可证与开源时点，**不移动任何 Git 目录、不创建新仓库**（SPLIT-001 禁止项）。

## 2. 现状（命令级证据，2026-08-11）

### 2.1 模块归属清单（26 叶模块，每模块唯一归属）

| 层 | 模块（artifactId） | 数量 |
|---|---|---|
| **Framework** | fast-ddd-common、fast-ddd-config、fast-ddd-model、fast-ddd-crud、fast-ddd-application、fast-ddd-web、fast-ddd-cache、fast-ddd-event、fast-ddd-search、fast-ddd-monitor、fast-ddd-starter、fast-ddd-security-starter、fast-ddd-ai-spring-boot-starter、fast-ddd-analyzer-spring-boot-starter | 14 |
| **Tooling** | fastddd-fastdoc-core、fastddd-templates、fastddd-code-generator、fastddd-doc-generator、fastddd-maven-plugin、fastddd-doc-maven-plugin、fastddd-project-archetype | 7 |
| **Studio** | fastddd-studio-server、fastddd-studio-ui | 2 |
| **BOM** | fastddd-bom | 1 |
| **Examples** | fast-ddd-examples | 1 |
| **Consumer-tests** | fastddd-consumer-tests | 1 |

> 物理目录：Framework 模块在 `fastddd-framework/`，Tooling 在 `fastddd-tooling/`（含 fastdoc-core），Studio 在 `fastddd-studio/`。BOM / Examples / Consumer-tests 在根目录直属。聚合 pom（fastddd-framework、fastddd-tooling、fastddd-studio、根 fastddd）只做 `<modules>` 聚合，不含代码。

### 2.2 实测跨层依赖图（grep 全部模块 pom 的直接 `<dependencies>`，排除 dependencyManagement）

```
Studio    fastddd-studio-server ──► Framework×7 + Tooling×2（code-generator、templates）
Tooling   fastddd-code-generator ──► fast-ddd-common、fast-ddd-model
Tooling   fastddd-doc-generator  ──► fast-ddd-common
Tooling   fastddd-fastdoc-core   ──► fast-ddd-common
Tooling   fastddd-maven-plugin   ──► fast-ddd-common
Examples  fast-ddd-examples      ──► fast-ddd-starter
❌Framework fast-ddd-web          ──► fastddd-fastdoc-core（TOOLING）  ← 唯一反向依赖
```

**结论**：
- 除 `fast-ddd-web → fastddd-fastdoc-core` 外，**全部依赖方向正确**（上层 → 下层：Studio→Tooling→Framework，examples/consumer-tests→Framework）。
- **不存在** Tooling→Studio、Framework→Studio 依赖。
- **唯一违规**：`fast-ddd-web`（Framework）→ `fastddd-fastdoc-core`（Tooling），且声明 `<optional>true</optional>`（`fastddd-framework/fast-ddd-web/pom.xml:28-31`）。web 硬引用 fastdoc 类：`FastDocService`、`FastDocExportService`、`FastDocTemplateService`、`DatabaseDocMetadata`、`TableDocMetadata`，并通过 `fastddd.web.fastdoc.FastDocController`（`@RestController @RequestMapping("/api/fastdoc")`）暴露为 REST 端点。
- **次生影响**：optional + 硬类引用 = 消费者边界泄漏（与审计 P0-2 同类）：消费者若不引入 fastdoc-core，`FastDocController` 加载失败。FRM-002 的 `web-without-application` 已隔离复现该类失败模式。

### 2.3 坐标与版本现状

- **groupId**：29 个构建模块均为 `com.gallop.fastddd`（grep 实证，唯一）；另 1 个 `${groupId}` 是 archetype 模板占位符（生成时替换，非构建模块）。
- **version**：全部经 CI-friendly `${revision}` 单一入口（UPG-010 成果），发布 POM 经 flatten 解析为字面量。
- **BOM**：`fastddd-bom` 已存在（UPG-010），是面向业务消费者的统一依赖契约入口。
- **许可证文件**：全仓无 `LICENSE` / `NOTICE` 文件；根 pom 无 `<license>` 声明。Apache-2.0 决策（承重记忆）尚未物化为文件。

## 3. 决策

### 3.1 三层产品边界与依赖方向（核心规则）

```
        ┌───────────────────────────┐
        │  Studio（聚合层 / 商业）   │  server + UI
        └─────────────┬─────────────┘
                      │ 允许
        ┌─────────────▼─────────────┐
        │  Tooling（能力层 / 开发期）│  生成器、插件、archetype、模板、fastdoc
        └─────────────┬─────────────┘
                      │ 允许
        ┌─────────────▼─────────────┐
        │  Framework（契约层 / OSS） │  框架模块 + BOM
        └─────────────┬─────────────┘
                      │ 允许（经 BOM）
        ┌─────────────▼─────────────┐
        │  业务消费者                │
        └───────────────────────────┘
```

**允许的依赖方向**（且仅这些）：
- Studio → Tooling → Framework
- 业务消费者 → Framework（经 `fastddd-bom` import）
- Examples / Consumer-tests → Framework（作为消费者验证）

**禁止的依赖方向**：
- Framework → Tooling（**当前 `fast-ddd-web → fastddd-fastdoc-core` 违规，SPLIT-002 消除**）
- Framework → Studio
- Tooling → Studio

### 3.2 坐标、版本与 BOM 归属

| 决策 | 内容 | 理由 |
|---|---|---|
| **groupId 不变** | 全部模块保持 `com.gallop.fastddd` | 验收"物理拆仓不要求业务系统改坐标"；groupId 是消费者契约的一部分 |
| **artifactId 不变** | 26 个模块 artifactId 冻结 | 同上 |
| **Framework 版本** | 语义化版本，`${revision}` 单一入口，1.0.x 线 | OSS 需稳定语义化承诺（breaking change 不作 minor，见 COMPATIBILITY_MATRIX） |
| **Studio 版本** | 独立版本，可与 Framework 解耦迭代 | 商业产品独立发版节奏；当前共享 `${revision}` 是单体期便利，拆仓后独立 |
| **BOM 归属** | `fastddd-bom` = Framework 消费者契约（OSS）；Studio 拆仓后可有自己的 BOM | Framework BOM 是公开稳定契约；Studio BOM 是内部商业聚合 |

### 3.3 许可证与开源范围（各层分别决策）

| 层 | 许可证 | 开源范围 | 公开时点 | 依据 |
|---|---|---|---|---|
| **Framework** | **Apache-2.0**（推荐方案） | 全部 14 模块源码开放 | Framework 公开 GA 时 | 承重决策已定；最终以权利人确认 + OSS-001 终审为准（plan 步骤 8） |
| **Tooling** | **Apache-2.0** | 全部 7 模块源码开放 | 随 Framework GA 同步公开 | 开发期工具链，开源驱动生态采纳；商业价值在 Studio 运行期产品，非生成器 |
| **Studio** | **专有/商业（Proprietary）** | server + UI 源码不公开 | 不公开（商业发版） | 设计工作室是商业产品；商标保留 |

**NOTICE 义务**：Hutool（`MulanPSL-2.0`，承重记忆 + FRM-000 §16.1.F）需在 Framework/Tooling 的 `NOTICE` 中声明。Spring AI、Spring Boot 等 Apache-2.0 依赖按惯例声明。

**混同禁区**（验收项）：Framework 的 Apache-2.0 开源与 Studio 的商业授权是**两种独立许可**，不得在文档/制品/仓库名中混为同一"许可证"。Studio 即便引用 Framework，也不改变 Framework 的 OSS 性质（Apache-2.0 允许商业引用）。

### 3.4 开源时点与 API 冻结

| 阶段 | Framework | Studio |
|---|---|---|
| 内部 RC（RC1~） | **不公开**（对内） | 商业 RC，对内/受邀 |
| RC1 前 | **冻结公共 API**（本 ADR + ADR-001~004 契约即冻结产物） | — |
| 公开 GA | **源码 + 制品公开**（Apache-2.0） | 商业 GA，独立 |

来源：承重记忆"RC 对内、GA 才公开" + plan 步骤 9。

## 4. 反向依赖处置（移交 SPLIT-002）

`fast-ddd-web → fastddd-fastdoc-core`（optional）的处置不在本 ADR 执行范围（SPLIT-001 禁止移动代码）。`fastdoc-core` 归属已明确为 **Tooling**（物理目录 `fastddd-tooling/`、声明于 tooling 聚合 pom、artifactId `fastddd-fastdoc-core`，三证一致）。SPLIT-002 的问题**不是 fastdoc-core 归属**，而是如何切断 web 对它的跨层依赖，二选一：

- **方案 A（接口下沉）**：把 web 真正需要的 doc 抽象（`FastDocService` 等）下沉为 Framework 内部模块/包，web 依赖同层的 Framework 抽象；`fastdoc-core`（Tooling）保留为实现，doc-generator 消费之。
- **方案 B（控制器上移）**：移除 web 对 fastdoc-core 的依赖，`FastDocController` 迁出到 Tooling 或 Studio（doc 导出本属工具/产品能力，不属于框架核心）。

无论哪种，目标态：**Framework 零 Tooling 依赖**，依赖方向规则（§3.1）成立，§5 enforcer 方可接线。

## 5. 依赖方向强制规则设计（plan 步骤 10）

> 本节是**设计**，不在本任务接线（SPLIT-001 禁止改构建产物结构）。接线为独立小任务（建议 M1 末或 SPLIT-002 完成后）。

**目标**：让依赖方向违规在构建期自动失败，而非靠人工 review。

**规则 1 — Maven Enforcer `banDependencies`（extra-enforcer-rules）**：在 Framework 各模块（或 framework 聚合 pom）禁止声明 Tooling/Studio artifact：

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId>
  <executions><execution>
    <id>ban-framework-upstream-deps</id><goals><goal>enforce</goal></goals>
    <configuration><rules><banDependencies>
      <searchTransitive>false</searchTransitive>
      <message>Framework 模块禁止依赖 Tooling/Studio（ADR-005 §3.1）</message>
      <bans>
        <ban>com.gallop.fastddd:fastddd-fastdoc-core</ban>
        <ban>com.gallop.fastddd:fastddd-templates</ban>
        <ban>com.gallop.fastddd:fastddd-code-generator</ban>
        <ban>com.gallop.fastddd:fastddd-doc-generator</ban>
        <ban>com.gallop.fastddd:fastddd-maven-plugin</ban>
        <ban>com.gallop.fastddd:fastddd-doc-maven-plugin</ban>
        <ban>com.gallop.fastddd:fastddd-project-archetype</ban>
        <ban>com.gallop.fastddd:fastddd-studio-*</ban>
      </bans>
    </banDependencies></rules></configuration>
  </execution></executions>
</plugin>
```

> 接线前提：SPLIT-002 已消除 `fast-ddd-web → fastdoc-core`，否则 enforcer 会在当前代码上立即失败。故 enforcer 接线排在 SPLIT-002 之后。

**规则 2 — ArchUnit（包级方向）**：作为 enforcer 的补充，按 Java 包名断言方向，捕获反射/类路径级泄漏：

```java
// Framework 包不得依赖 tooling/studio 包
noClasses().that().resideInAPackage("com.gallop.fastddd..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "com.gallop.fastddd.fastdoc..", "com.gallop.fastddd.tooling..")
// （Framework 自身包前缀需精确化，排除 fastdoc 等）
```

两条规则互补：enforcer 守 POM 声明级，ArchUnit 守字节码级。

## 6. 后果与权衡

- **正面**：后续任务无需重判代码归属（验收）；拆仓时坐标稳定（验收）；OSS 与商业授权不混同（验收）；方向违规可自动拦截。
- **代价**：SPLIT-002 必须先消除 web→fastdoc 违规，enforcer 才能接线（否则当前代码即失败）。
- **遗留**：Tooling 的 Apache-2.0 与 Studio 的商业授权为**推荐结论**，最终以权利人确认 + OSS-001 终审为准（plan 步骤 8 明示 Framework 许可证"最终选择以权利人确认和 OSS-001 审核为准"）。LICENSE/NOTICE 文件物化列为发布前任务。

## 7. 验收对照（SPLIT-001）

- ✅ 所有现有模块都有唯一归属 → §2.1 清单（26 叶模块逐一归属）。
- ✅ 后续任务不需要重新判断代码归属 → §3.1 边界 + §3.2 坐标冻结。
- ✅ 物理拆仓不会要求业务系统改既有坐标 → §3.2 groupId/artifactId 不变。
- ✅ Framework 开源与 Studio 商业授权未混同 → §3.3 混同禁区。
- ✅ 开源范围、版权责任人、公开时点都有 ADR 结论 → §3.3 + §3.4（责任人 = 权利人，待 OSS-001 终审登记）。
