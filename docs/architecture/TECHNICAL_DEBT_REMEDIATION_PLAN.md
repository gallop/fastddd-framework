# FastDDD 技术债治理与基础质量加固计划

> **版本:** v2.0 (经技术 Review 修正)
> **日期:** 2026-04-20
> **状态:** 待执行
> **目标:** 在项目首次提交前，建立统一的依赖治理体系，消除构建缺陷，补全示例工程，为后续功能开发奠定高质量起点

---

## 一、问题全景

### 1.1 技术债清单（12 项）

| 编号 | 技术债 | 严重度 | 类型 | 发现来源 |
|------|--------|--------|------|----------|
| D1 | `fastddd-templates/pom.xml` 7 个依赖版本硬编码，其中 3 个严重过时（jackson 2.15.3、spring-context 6.0.13、slf4j 2.0.9） | 🔴 高 | 依赖治理 | 原始已知 |
| D2 | 根 POM `dependencyManagement` 未覆盖 7 个内部模块 + 4 个三方库（freemarker、jjwt、javaparser、zip4j） | 🔴 高 | 依赖治理 | 原始已知 + Review 补充 |
| D3 | `fastddd-code-generator` 未依赖 `fastddd-templates`，maven-plugin 独立使用代码生成时模板不可用 | 🔴 高 | 构建缺陷 | 原始已知 + Review 深化 |
| D4 | `fastddd-examples` 空壳（无主类、无代码），`mvn package` 阶段失败 | 🔴 高 | 构建缺陷 | 原始已知 + Review 修正 |
| D5 | 原型 Spring Boot 版本 3.2.0 vs 项目 3.4.5，生成项目落后 2 个小版本 | 🟡 中 | 产品质量 | 原始已知 |
| D6 | 11 个硬编码依赖版本散布在 4 个模块（jjwt×3、javaparser×2、freemarker×2、maven×3） | 🟡 中 | 依赖治理 | 原始已知 |
| D7 | `fastddd-doc-maven-plugin` 未使用属性引用，与 sibling `fastddd-maven-plugin` 风格不一致 | 🟢 低 | 一致性 | 原始已知 |
| D8 | `ProjectInitializrControllerTest` 硬编码 context-path `/fastddd-studio`（4 处） | 🟢 低 | 测试质量 | 原始已知 |
| D9 | 3 个模块冗余声明 `maven-compiler-plugin`（templates、maven-plugin、studio-server） | 🟢 低 | 一致性 | Review 新增 |
| D10 | `fastddd-studio-server` 4 个依赖版本硬编码（mybatis-plus、mysql、freemarker、zip4j） | 🟡 中 | 依赖治理 | Review 新增 |
| D11 | `fastddd-code-generator` 自身遗留 21 个平铺模板，与 `fastddd-templates` 子目录模板重复 | 🟢 低 | 死代码 | Review 新增 |
| D12 | `fastddd-studio-server` 7 个内部模块依赖冗余声明 `<version>` | 🟢 低 | 一致性 | Review 新增 |

### 1.2 架构级风险传导

```
影响范围（由外到内）

  用户层  ← D5 原型版本落后 → 外部用户首次体验即产生兼容性疑虑
    ↓
  示例层  ← D4 空壳模块 → 无法运行演示，框架价值无法直观展示
    ↓
  工具层  ← D3 缺依赖 + D11 遗留模板 + D1/D6/D10 版本硬编码 → 代码生成器/Maven插件运行时失败
    ↓
  框架层  ← D2 dependencyManagement 缺口 → studio-server 等消费者被迫硬编码版本
    ↓
  测试层  ← D8 路径硬编码 → context-path 变更时测试断裂
```

**根因诊断：** 12 项债务的核心根因是 **缺乏统一的依赖治理契约**。根 POM 的 `dependencyManagement` 覆盖率不足，导致各模块各自为政地硬编码版本，进而产生版本漂移、构建不一致和运行时失败。

---

## 二、设计原则

1. **单一事实来源** — 根 POM 是版本的唯一权威，所有模块通过属性或 `dependencyManagement` 引用，禁止版本字面量
2. **渐进式修复** — 每阶段有独立验证门禁，可随时暂停，已完成阶段的产出不受后续阶段影响
3. **零功能变更** — 本次只治债不增加新功能，不修改任何业务逻辑
4. **最小化风险** — 以机械性修改为主，涉及架构调整处（D3）需附带运行时验证

---

## 三、执行阶段

### Phase 0：基线快照（~15min）

**目标：** 确认当前编译基线，归档初始状态

| 步骤 | 操作 | 产出 |
|------|------|------|
| 0.1 | `mvn clean compile -DskipTests` | 编译结果（预期：成功，因 examples 仅在 package 阶段失败） |
| 0.2 | `mvn clean package -DskipTests` | 打包结果（预期：失败，examples 缺主类） |

> **注：** 不在此阶段做全面的 dependency:tree 或 checkstyle 分析，这些合并到 Phase 4 最终验证，避免重复工作。

**门禁：** 编译/打包失败清单归档，作为后续修复验证依据

---

### Phase 1：依赖治理（~2.5h）🔴 关键路径

**目标：** 根 POM 成为版本的唯一权威，消除全部硬编码版本

#### 1.1 根 POM `dependencyManagement` 补全（D2）

**新增属性：**

```xml
<properties>
    <!-- 已有属性保持不变 -->
    <freemarker.version>2.3.33</freemarker.version>
    <jjwt.version>0.11.5</jjwt.version>
    <javaparser.version>3.25.1</javaparser.version>
    <zip4j.version>2.11.5</zip4j.version>
</properties>
```

**新增内部模块（7 个）：**

| ArtifactId | Version |
|-----------|---------|
| `fast-ddd-security-starter` | `${project.version}` |
| `fast-ddd-ai-spring-boot-starter` | `${project.version}` |
| `fastddd-templates` | `${project.version}` |
| `fastddd-doc-generator` | `${project.version}` |
| `fastddd-maven-plugin` | `${project.version}` |
| `fastddd-doc-maven-plugin` | `${project.version}` |
| `fastddd-project-archetype` | `${project.version}` |

**新增三方库（4 组）：**

| GroupId:ArtifactId | Version Property |
|-------------------|-----------------|
| `org.freemarker:freemarker` | `${freemarker.version}` |
| `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` | `${jjwt.version}` |
| `com.github.javaparser:javaparser-core` / `javaparser-symbol-solver-core` | `${javaparser.version}` |
| `net.lingala.zip4j:zip4j` | `${zip4j.version}` |

#### 1.2 消除模块级硬编码版本（D1, D6, D10）

逐文件修改清单：

| 文件 | 操作 | 涉及依赖 |
|------|------|----------|
| `fastddd-templates/pom.xml` | 移除全部 7 个 `<version>` 标签 | freemarker, lombok, slf4j-api, jackson-databind, spring-context, junit-jupiter, assertj-core |
| `fastddd-code-generator/pom.xml` | 移除 freemarker `<version>` 标签 | freemarker |
| `fast-ddd-security-starter/pom.xml` | 移除 jjwt 3 个 `<version>` 标签 | jjwt-api, jjwt-impl, jjwt-jackson |
| `fastddd-doc-generator/pom.xml` | 移除 3 个 `<version>` 标签 | javaparser-core, javaparser-symbol-solver-core, freemarker |
| `fastddd-doc-maven-plugin/pom.xml` | 改为属性引用，统一风格 | maven-plugin-api, maven-core, maven-plugin-annotations |
| `fastddd-studio-server/pom.xml` | 移除 4 个 `<version>` 标签 | mybatis-plus, mysql-connector-java, freemarker, zip4j |

#### 1.3 清理冗余构建声明（D9, D12）

| 文件 | 操作 |
|------|------|
| `fastddd-templates/pom.xml` | 删除整个 `<build>` 块（含冗余 compiler-plugin + `maven.compiler.source/target` 属性） |
| `fastddd-maven-plugin/pom.xml` | 移除冗余 `maven-compiler-plugin` 声明，保留根 pluginManagement 继承 |
| `fastddd-studio-server/pom.xml` | 精简 `maven-compiler-plugin`，仅保留根 pluginManagement 未覆盖的 `annotationProcessorPaths` 部分 |
| `fastddd-studio-server/pom.xml` | 移除 7 个内部模块依赖的冗余 `<version>${project.version}</version>` 标签 |

#### 1.4 验证

```bash
# 编译通过（含 Phase 0 中的失败项）
mvn clean compile -DskipTests

# 依赖树无冲突
mvn dependency:tree -Dverbose=false 2>&1 | grep -iE "(conflict|omitted for conflict)"

# 全量扫描确认零硬编码版本（排除根 POM 的 property 定义）
# 检查所有模块 pom.xml 中是否还有 <version>数字</version> 模式（非 ${property} 引用）
```

**门禁：** 全模块编译通过，`dependency:tree` 无冲突，零硬编码依赖版本

---

### Phase 2：构建完整性修复（~3h）🔴 关键路径

**目标：** 所有模块可独立构建且运行正确

#### 2.1 修复 code-generator 与 templates 的依赖关系（D3）

**问题本质：** `fastddd-code-generator` 的 `CodeGeneratorService` 引用 `anemic/*.ftl` 和 `rich/*.ftl` 路径的模板，但这些模板仅存在于 `fastddd-templates` JAR 中。当前仅 studio-server 通过同时依赖两者来拼合，maven-plugin 独立使用时模板不可用。

**修复步骤：**

1. `fastddd-code-generator/pom.xml` 添加依赖：
   ```xml
   <dependency>
       <groupId>com.gallop.fastddd</groupId>
       <artifactId>fastddd-templates</artifactId>
   </dependency>
   ```

2. 验证 `TemplateEngineConfig` 的 classpath 加载机制在引入新依赖后正常工作（`getClass().getClassLoader()` 会扫描 classpath 上所有 JAR 的 `templates/` 目录）

3. 验证 maven-plugin 通过传递依赖获得 templates 模块：
   ```
   maven-plugin → code-generator → templates（传递依赖）
   ```

#### 2.2 清理 code-generator 遗留模板（D11）

`fastddd-code-generator/src/main/resources/templates/` 下有 21 个遗留平铺模板：

```
aggregateRoot.ftl, applicationService.ftl, command.ftl, commandController.ftl,
commandHandler.ftl, controller.ftl, dto.ftl, entity.ftl, entityMapper.ftl,
entityRepository.ftl, entityRepositoryImpl.ftl, mapper.ftl, po.ftl, query.ftl,
queryController.ftl, queryHandler.ftl, repository.ftl, rest-controller.ftl,
service.ftl, serviceImpl.ftl, vo.ftl
```

这些模板与 `fastddd-templates` 中 `anemic/` 和 `rich/` 子目录下的模板功能重复但路径不同。`CodeGeneratorService` 实际只引用子目录路径，这些平铺模板是死代码。

**操作：** 删除 `fastddd-code-generator/src/main/resources/templates/` 下的全部 21 个遗留 `.ftl` 文件。

> **风险控制：** 删除前全局搜索确认无其他代码引用这些平铺路径（如 `entity.ftl` 而非 `anemic/Entity.ftl`）。

#### 2.3 修复 examples 空壳模块（D4）

**范围收敛：** 本次仅创建最小可运行骨架，确保 `mvn package` 通过。完善的示例（Service 层、集成测试等）作为后续独立任务。

**创建文件清单：**

```
fastddd-examples/src/main/java/com/gallop/fastddd/examples/
├── FastDddExampleApplication.java       ← Spring Boot 主类
├── domain/
│   └── User.java                        ← BasePO 实体（演示贫血模型）
├── mapper/
│   └── UserMapper.java                  ← BaseMapper 接口
├── service/
│   └── UserService.java                 ← IService 接口
├── service/impl/
│   └── UserServiceImpl.java             ← BaseServiceImpl 实现
└── controller/
    └── UserController.java              ← BaseController 继承

fastddd-examples/src/main/resources/
├── application.yml                      ← H2 内存库（零配置启动）
└── schema.sql                           ← 建表语句
```

**关键设计决策：**
- 默认使用 **H2 内存库**，用户 clone 后无需安装 MySQL 即可运行
- 仅演示贫血模型（ANEMIC），与框架快速上手路径对齐
- 不包含集成测试（避免引入 Testcontainers 等重依赖，留给后续完善）

#### 2.4 更新原型 Spring Boot 版本（D5）

**操作：**

1. 在 `archetype-metadata.xml` 中新增参数：
   ```xml
   <requiredProperty key="springBootVersion">
       <defaultValue>3.4.5</defaultValue>
   </requiredProperty>
   ```

2. 在 `archetype-resources/pom.xml` 中将硬编码的 `3.2.0` 替换为 `${springBootVersion}`

> **注：** 原型生成独立项目，无法引用父 POM 属性，因此通过 archetype 参数机制传递版本号。

#### 2.5 验证

```bash
# 全量打包（验证 D4 修复）
mvn clean package -DskipTests

# Maven 插件端到端验证（验证 D3 修复）
# 在 examples 目录下验证插件可以正常解析
cd fastddd-examples
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-SNAPSHOT:help
```

**门禁：** `mvn clean package -DskipTests` 成功，maven-plugin help goal 正常输出

---

### Phase 3：测试质量加固（~30min）

**目标：** 消除测试中的 context-path 硬编码

#### 3.1 重构测试 context-path（D8）

**文件：** `fastddd-studio/fastddd-studio-server/src/test/java/com/gallop/fastddd/studio/controller/ProjectInitializrControllerTest.java`

**方案：提取常量（保持 `@WebMvcTest` 不变）**

> **⚠️ 重要：不可改用 `@SpringBootTest`。** 该测试使用 `@MockBean` 替代 Service 依赖，是标准的 Slice Test 模式。`@SpringBootTest` 会加载全量上下文（1000+ Bean），触发 Redis 连接、Spring AI 客户端初始化等，在测试环境中不可用且启动耗时 3-5 倍。

```java
// 修改前（4 处硬编码）
mockMvc.perform(get("/fastddd-studio/api/initializr/modules"))
mockMvc.perform(post("/fastddd-studio/api/initializr/validate"))
mockMvc.perform(post("/fastddd-studio/api/initializr/resolve-dependencies"))
mockMvc.perform(get("/fastddd-studio/api/initializr/modules/nonexistent"))

// 修改后
private static final String CONTEXT_PATH = "/fastddd-studio";

mockMvc.perform(get(CONTEXT_PATH + "/api/initializr/modules"))
mockMvc.perform(post(CONTEXT_PATH + "/api/initializr/validate"))
mockMvc.perform(post(CONTEXT_PATH + "/api/initializr/resolve-dependencies"))
mockMvc.perform(get(CONTEXT_PATH + "/api/initializr/modules/nonexistent"))
```

**不引入集中化测试常量类（D10）。** 当前仅 1 个测试文件涉及此问题，过早抽象反而增加维护成本。若后续测试文件增多再提取。

#### 3.2 验证

```bash
# 单元测试通过
mvn clean test -pl fastddd-studio/fastddd-studio-server

# 确认零硬编码
grep -rn "fastddd-studio" --include="*Test.java" fastddd-studio/
# 预期：仅出现在常量定义行
```

**门禁：** studio-server 测试通过，测试文件中 context-path 仅出现在常量定义处

---

### Phase 4：最终验证（~1.5h）

**目标：** 全流程端到端验证，确认所有债务已清偿

#### 4.1 清洁构建

```bash
# 完整安装到本地仓库
mvn clean install -DskipTests
```

#### 4.2 全量测试

```bash
mvn clean test
```

#### 4.3 代码质量检查

```bash
mvn validate -Pcheckstyle
```

#### 4.4 依赖健康检查

```bash
# 版本冲突检测
mvn dependency:tree -Dverbose=false 2>&1 | grep -iE "(conflict|omitted for conflict)"

# 硬编码版本扫描（验证 Phase 1 成果）
find . -name "pom.xml" -not -path "*/archetype-resources/*" -exec grep -l "<version>[0-9]" {} \;
# 预期：仅剩根 POM 的 property 定义和 archetype 模板的占位符
```

#### 4.5 示例应用运行验证

```bash
cd fastddd-examples
mvn spring-boot:run
# 预期：应用在 H2 内存库上正常启动，日志无 ERROR
```

#### 4.6 Maven 插件功能验证

```bash
# 验证代码生成插件可独立运行（不依赖 studio）
cd fastddd-examples
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-SNAPSHOT:help -Ddetail=true
# 预期：输出所有 goal 和参数说明，无 ClassNotFoundException 或 TemplateNotFoundException
```

**门禁：** 全部 6 项验证通过，项目处于可首次提交状态

---

## 四、工作量与执行顺序

### 4.1 依赖关系图

```
Phase 0（15min）
    │
    ▼
Phase 1（2.5h）───────────────────┐
    │                              │
    ▼                              │（可并行）
Phase 2（3h）                  Phase 3（30min）
    │                              │
    └──────────┬───────────────────┘
               ▼
          Phase 4（1.5h）
```

- **Phase 1 → Phase 2** 是串行的（依赖治理是构建修复的前提）
- **Phase 1 → Phase 3** 可并行（测试修复不依赖依赖治理）
- **Phase 4** 依赖全部完成

### 4.2 工时估算

| 阶段 | 工时 | 前置依赖 | 主要风险 |
|------|------|----------|----------|
| Phase 0 | 0.25h | 无 | 低 |
| Phase 1 | 2.5h | Phase 0 | 低 — 机械性 POM 修改 |
| Phase 2 | 3h | Phase 1 | 中 — 需创建新代码 + 遗留模板清理 |
| Phase 3 | 0.5h | 无 | 低 — 单文件修改 |
| Phase 4 | 1.5h | 全部 | 低 — 验证性工作 |
| **合计** | **7.75h** | | |

### 4.3 提交策略

每个 Phase 完成后独立提交，保持 git 历史清晰：

```
Phase 0 → (无需提交，仅验证)
Phase 1 → chore: 统一依赖治理 — 补全根 POM dependencyManagement，消除全部硬编码版本
Phase 2 → fix: 修复构建完整性 — code-generator 模板依赖、遗留模板清理、examples 骨架、原型版本对齐
Phase 3 → refactor: 消除测试 context-path 硬编码
Phase 4 → (无需提交，仅验证)
```

---

## 五、不在本次范围内的事项

以下事项经评估后决定**不在本次治理范围内**，作为后续独立任务处理：

| 事项 | 原因 |
|------|------|
| examples 完善示例（Service 层、集成测试、种子数据） | 超出技术债治理范围，属于功能开发 |
| examples 充血模型（RICH）示例 | 需要设计独立的聚合根，工作量大 |
| 引入集中化测试常量基类 | 当前仅 1 个测试文件涉及，过早抽象 |
| 三层架构跨层依赖治理（tooling→framework 双向依赖） | 涉及架构重构，风险和范围远超本次 |
| templates 模块的遗留模板重构（统一为子目录结构） | 功能层面已通过 D11 清理解决 |
| CI/CD 流水线配置 | 项目尚未首次提交，CI 管道未建立 |

---

## 六、修正记录

本计划 v2.0 基于 v1.0 的技术 Review 修正，主要变更：

| 修正项 | v1.0 内容 | v2.0 修正 | 原因 |
|--------|-----------|-----------|------|
| R1 | Phase 3 建议改用 `@SpringBootTest` | 改为提取常量，保持 `@WebMvcTest` | `@SpringBootTest` 会触发 Redis/AI 连接，测试环境不可用 |
| R2 | D3 仅描述"添加依赖" | 补充遗留模板清理 + maven-plugin 传递依赖验证 | 遗留模板与子目录模板共存导致维护混乱 |
| R3 | D4 描述"根构建会失败" | 修正为"`mvn package` 阶段失败"，`compile` 可通过 | mainClass 仅在 package 阶段检查 |
| R4 | 未纳入 studio-server 版本治理 | Phase 1.2 补充 4 个硬编码版本 | freemarker、zip4j 未被根 POM 管理 |
| R5 | 未提及遗留模板 | 新增 D11 + Phase 2.2 清理步骤 | 21 个遗留平铺模板是死代码 |
| R6 | D9 仅提 templates | 扩展为 3 个模块（含 maven-plugin、studio-server） | Review 发现冗余范围更大 |
| R7 | 未纳入 studio-server 冗余 version 声明 | 新增 D12 + Phase 1.3 清理 | 补全 dependencyManagement 后这些声明变为冗余 |
| R8 | Phase 4 无 maven-plugin 验证 | Phase 4.6 新增端到端验证 | 验证 D3 修复的关键测试 |
| R9 | Phase 0 含全面依赖树+checkstyle 分析 | 精简为快照验证，全面分析合并到 Phase 4 | 避免重复工作，节省 50 分钟 |
| R10 | examples 创建完整 CRUD 示例 | 收敛为最小可运行骨架 | 治债与功能开发分离 |
