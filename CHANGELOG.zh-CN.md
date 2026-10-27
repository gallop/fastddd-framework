# Changelog

[English](CHANGELOG.md) | **简体中文**

本文件记录 FastDDD 所有显著变更，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

完整版本基线与兼容线见 [docs/COMPATIBILITY_MATRIX.zh-CN.md](docs/COMPATIBILITY_MATRIX.zh-CN.md)；
业务系统升级步骤见 [docs/UPGRADE_GUIDE.zh-CN.md](docs/UPGRADE_GUIDE.zh-CN.md)。

---

## [1.1.3] - 2026-10-27

### 新增

- **AI 结构化访谈提示词契约（v3d 双键+访谈模板对）**（`fast-ddd-ai-spring-boot-starter`）：分析与 refine prompt 识别只增的 `interview`/`clarifications` 双键——refine 保留句覆盖双键（除非用户指令明确涉及，必须原样保留在输出中），skeleton 输出清单列为可选第 8 条（输入已有则原样保留，不虚构生成）；analyze prompt 新增可选「澄清上下文」段（已澄清事实优先于 PRD 歧义表述，冲突以澄清为准）。新模板对 `ddd-interview-system`/`ddd-interview-user` 驱动多轮结构化访谈：严格二选一输出契约——形态一产出下一批澄清问题（`question`/`expects` 受控三值 `text`/`select`/`multi`，可选 `options`；每轮≤5 题、同题不重复），形态二产出完整十键方案（schema 样例与 analyze 模板逐字节同源）；禁混合输出、澄清已足禁空 questions 数组。只增不删：`content_schema` 恒 `v1`、不删任何键、canonical 键序=存量十键后尾列追加双键。

### 兼容性

- BOM 制品集不变（18 员，`FrameworkBomAllowlistTest` 守卫）。零迁移；依赖基线不变（Java 21 / Spring Boot 3.5.16）。Studio 消费链（向导访谈插步、按澄清重产方案）随 Studio 侧发版列车落地。

## [1.1.2] - 2026-10-26

### 新增

- **AI 行为面提示词契约（v3c 键族）**（`fast-ddd-ai-spring-boot-starter`）：analyze system prompt 的 JSON schema 样例新增行为面四键——`commands`（`name`/`aggregateName`/`description`）、`events`（`name`/`aggregateName`/`description`）、`policies`（`triggerEvent`/`reactionCommand`/`description`）与 `behaviors`（场景：`name`/`kind` `SEQUENCE`|`PROCESS` 与 `steps`——时序消息步 `from`/`to`/`label`/`messageKind`，流程步仅 `label`）；refine prompt 保留句扩至四键（除非用户指令明确涉及，这些键必须原样保留在输出中）；skeleton prompt 输出清单第 7 条列为可选项（PRD 中识别得出才输出）。只增不删：`content_schema` 恒 `v1`、不删任何键、canonical 键序=存量六键后尾列追加四键。

### 兼容性

- BOM 制品集不变（18 员，`FrameworkBomAllowlistTest` 守卫）。零迁移；依赖基线不变（Java 21 / Spring Boot 3.5.16）。Studio 消费链（typed 扩列、行为视图时序/流程子面、PlantUML 导出）随 Studio 侧发版列车落地。

## [1.1.1] - 2026-10-25

### 修复

- **审计日志脱敏加固**（`fast-ddd-security-starter`）：`AuditLogAspect.maskSensitiveJson` 改为后缀族匹配敏感键（`[A-Za-z0-9_]*(?:[pP]assword|[sS]ecret|[tT]oken)`）——原五个精确键漏掉的复合键（`oldPassword`/`newPassword`/`clientSecret` 等）全覆盖——且转义值整体遮蔽（原 `[^"]*` 在含 `\"` 的值上提前截断——尾部明文泄漏+产物 JSON 劈裂）。保护写审计参数的框架直接消费者；Studio 侧已全堵（`logParams=false`+`WRITE_ONLY` 载荷）。

### 新增

- **AI 上下文地图提示词契约（v3b 键族）**（`fast-ddd-ai-spring-boot-starter`）：analyze system prompt 的 JSON schema 样例新增限界上下文间 `contextRelationships`（`from`/`to`/`pattern`/`description`）与 `aggregates[].contexts`；refine prompt 增显式保留句（除非用户指令明确涉及，这些键必须原样保留在输出中）；skeleton prompt 输出清单列出 `contextRelationships`。只增不删：`boundedContexts` 平面字符串清单冻结、`content_schema` 恒 `v1`、不删任何键。Studio 消费链（typed 扩列、ER 地图视图、PlantUML 分组）随 Studio 侧发版列车落地。

### 兼容性

- BOM 制品集不变（18 员，`FrameworkBomAllowlistTest` 守卫）。零迁移；依赖基线不变（Java 21 / Spring Boot 3.5.16）。

## [1.1.0] - 2026-10-24

### Added

- **可选身份能力——飞书集成**（`fastddd-bom` 新纳管四模块：`fast-ddd-identity-core`、`fast-ddd-identity-reference-pg`、`fast-ddd-identity-spring-boot-starter`、`fast-ddd-identity-feishu`）：登录（授权码流程，含 state/浏览器绑定）、worker 令牌、目录镜像与增量同步、审计 actor 桥。整体由 `fastddd.identity.enabled` 门控（默认**关闭**）——不引入任何 `fast-ddd-identity-*` 依赖的消费者相对 1.0.0 **零行为变化**（兼容性说明见 [docs/COMPATIBILITY_MATRIX.zh-CN.md](docs/COMPATIBILITY_MATRIX.zh-CN.md)）。
- **security-starter 身份桥**（`fast-ddd-security-starter`）：可选 `AuditActorProvider`、`LocalAuthConfigurationGuard` 与审计日志切面/实体扩展点——仅在身份模块在場时激活。
- **AI 字段设计提示词契约：`defaultValue`**（`fast-ddd-ai-spring-boot-starter`）：`ddd-fields-user.txt` 现为带业务默认值的字段示范并约定输出 `defaultValue` 键（无默认值则省略该键——不输出 `null`），补全 Studio 消费链（投影 → 实体 `default_value` → PostgreSQL/Oracle/MySQL/H2 四方言 DDL `DEFAULT`）。
- **消费者测试覆盖**：`identity-*` 与 `security-*` invoker 场景（directory、directory-sync、directory-feishu、feishu-login、host-security、session-postgres、worker、disabled；local-auth-off、minimal、nodb、custom-config、with-database）另加 `bom-revision-isolation`。
- **TCK 配套制品**（`fast-ddd-identity-core`）：源码构建 reactor 内产出 `test-jar` classifier 供 TCK dogfood；**有意不**纳入 GA 发布制品集（候选仓维持 288 文件形）。

### Compatibility

- BOM 纳管 18 制品（14 核心+4 身份）；`FrameworkBomAllowlistTest` 守卫该集合。
- 核心运行时血统：13 模块 `src/main` 与 v1.0.2 字节一致；`fast-ddd-security-starter` 仅有 5 文件身份桥面（W2 时代交付，M2 门禁已覆盖）。1.1.0 前消费者核心行为零变化。

### Migration

- 身份能力采用方：reference PG 适配器需要宿主自持 schema 迁移 V1–V3（+V4 目录增量）与可选 `V1__sys_audit_log_actor_columns`；见 `docs/identity/`（W4-d 发布说明、迁移指南、配置样例、恢复声明）。

## [1.0.2] - 2026-10-12

### 新增

- **TableMetadata 语义扩列（additive-nullable）**（`fast-ddd-common`）：新增三个可空 `String` 列——`aggregateName`/`dddRole`/`relationsSummary`，均不带初始化器，1.0.2 之前的 JSON 载荷反序列化后三键保持 `null`（由 `TableMetadataJsonCompatibilityTest` 锁定）。下游代码生成模板以语义标注形态消费，须持 `<#if x?? && x>` / `${x!''}` 守卫形——无守卫插值在 1.0.2 之前的 jar 上抛异常。内容为 additive（semver-minor 内容量），按「单版本 1.0.2 自 trunk」发布决策落在 patch 版位——与矩阵「PATCH = bug fixes only」的字面偏差在此披露。

### 修复

- **`ddd-refine-user.txt` 提示词契约**（`fast-ddd-ai-spring-boot-starter`）：原文「（骨架，不含字段细节）」节头与「不需要输出 fields 数组」的全称断言，与实际整包 fields 载荷输入、及要求手工修改字段定义保留在输出 JSON 中的消费方（Studio 手改清单附录）冲突。措辞改为陈述常规情形并显式豁免手工修改字段。

## [1.0.1] - 2026-10-05

### Fixed

- **失败业务请求的审计 success 语义**（`fast-ddd-security-starter`，行为变更——升级前请阅读）：`@AuditLog` 切面在目标方法正常返回后按返回值信封判定 success。返回的 `ApiResponse`（方法直返，或作为一层 `ResponseEntity` 包裹的 body）且 `code != 200`（沿 `ApiResponse#isSuccess()` 的 200 硬判定；`code` 为 `null` 亦计失败）记为 `success = 0`，信封 message 非空白时写入 `error_message`。此前切面在正常返回后无条件置 `success = true`，凡 controller 承接业务异常转失败信封（HTTP 200/400 正常返回、异常不出抛）的请求**恒被记为成功**——最显性=框架自带 `/api/auth/login` 端点的失败登录曾被记为成功。
  - 判型边界：仅判上两类显式信封形。非信封返回（裸 DTO、`String`）、`ResponseEntity` body 为 `null` 或非 `ApiResponse`、异步返回体在切面时刻尚未定形——均维持既有 `success = true`。
  - 守卫绝不污染被审计请求路径：守卫自身任何异常（含信封子类覆写 `isFailure()`/`getMessage()` 抛出）兜底记 `success = true` 并降级 debug 日志；消费者类路径缺席 `fast-ddd-web`（optional 依赖）时守卫恒休眠。
  - **回退开关**：置 `fastddd.security.audit.result-judging.enabled=false` 可整体回退 1.0.0 恒成功语义（缺省 `true`）。该键经 `@Value` 匹配、**无宽松绑定**——须用上述 kebab-case 全字面量；camelCase 变体会静默落缺省值 `true`。

## [1.0.0] - 2026-09-13

首个正式发布版本基线：**JDK 21 + Spring Boot 3.5.16**。
本版本由 `JDK21_SPRING_BOOT_3_5_UPGRADE_PLAN.md` 的 UPG-000～UPG-010 驱动，
从 JDK 17 / Boot 3.4.5 开发快照确定性升级而来。

> **重发说明（2026-09-13，Owner 决策）。** 2026-09-10 的内部 1.0.0 交付撤销并在原版本位重发：
> 前置核实确认旧制品无任何消费者（两个试点项目消费的是 `com.gallop.fastddd:1.0.0-jdk21-rc1`，
> 并非本 GA）、从未部署、从未对外交付。旧 tag 对象、目标提交与制品字节已带 SHA-256 指纹归档于
> `.fastddd-releases/internal-v1.0.0-r2/evidence/withdrawn-internal-v1.0.0/`；旧内部 release 仓隔离保留
> 并标记撤销。下方「重发新增条目」小节列出重发制品相对被撤销制品的增量——均为文档与登记类变更，
> **无运行时代码变更**。

### 重发新增条目（2026-09-13，相对被撤销的 2026-09-10 制品）

- **新增——文档国际化（L2）**：根 `README.md` 改为英文正典并新增 `README.zh-CN.md` 中文镜像；P0/P1 批次——17 份参考文档补齐 `*.zh-CN.md` 副本（指南、API 参考、兼容矩阵、升级文档）。
- **变更——治理档案迁出**：30 份内部治理/演进文档移出仓库至仓外私有档案 `fastddd-docs-internal`；留守文档引用修正（docs 索引、兼容矩阵、升级入口、ID 迁移指南、Jackson 配置、ADR-007）。仓库自此仅承载 Community 安全内容。
- **变更——发布状态文档**同步 GA 1.0.0 坐标：迁移指南与版本字面量定稿。
- **变更——版本登记统一为 `1.0.0`**（Owner 决策 2026-09-13）：未发布的 `1.0.0-jdk21` 版本计划撤销；根 pom 与 `fastddd-bom` 默认 `revision` 回落 `1.0.0-SNAPSHOT`，consumer-tests IT 默认版本同步；历史 rc1 记录不变。
- **修复——英文正典**翻译定稿及文档集对抗性审查整改。
- **修复——`Outbox` MySQL 测试 javadoc** 去除本机 JDK 路径（脱敏）。

### Added（新增）

- **CI-friendly 版本治理**：引入 `${revision}` 属性作为 reactor 唯一版本入口，27 个模块版本统一受其驱动；
  配合 `flatten-maven-plugin`（`resolveCiFriendliesOnly`）在发布 POM 中把 `${revision}` 解析为字面量，
  满足"版本只从一个属性进入 reactor"且"发布 POM 版本已解析"。
- **`fastddd-bom` 统一依赖契约模块**：业务项目只需 `import` 该 BOM 即获得 FastDDD 全部模块与推荐第三方
  依赖的一致版本，无需各自声明版本。BOM 为独立 POM，发布产物自洽（无环境属性泄漏）。
- **Maven Enforcer 构建门禁**：`requireJavaVersion[21,)`、`requireMavenVersion[3.9.0,4.0.0)`、
  `requirePluginVersions`（所有插件版本须显式）、`banDuplicatePomDependencyVersions`。
- **JDK 21 编译基线**：根 pom `java.version=21` + `maven.compiler.release`，产物 class major version 65。
- **Spring Boot 3.5.16 + 依赖确定性对齐**：Spring AI 1.1.8、SpringDoc 2.8.17、Knife4j 4.5.0、
  MyBatis-Plus 3.5.12、MyBatis-Spring 3.0.4、Druid `druid-spring-boot-3-starter` 1.2.28、
  mysql-connector-j 9.7.0、Micrometer 1.15.12（消除旧版分裂）、Jackson 2.21.x（Boot 管理）。
- **PMD 7**：`maven-pmd-plugin` 升至 3.24.0（PMD 7.3.0），支持 `targetJdk=21`。
- **Maven Wrapper**：`./mvnw` pin Maven 3.9.5，CI 与本地 Maven 版本统一。
- **Docker JDK 21**：构建阶段 `maven:3.9.9-eclipse-temurin-21`，运行阶段 `eclipse-temurin:21-jre-alpine`。
- **版本治理文档**：新增 `docs/COMPATIBILITY_MATRIX.md`、`docs/UPGRADE_GUIDE.md`、`docs/RELEASE_CHECKLIST.md`。

### Changed（变更）

- Spring Boot 3.4.5 → **3.5.16**；JDK 17 → **JDK 21**（编译与运行时）。
- Druid 坐标迁移：`druid-spring-boot-starter`（Boot 2 坐标）→ **`druid-spring-boot-3-starter`**（4 处引用）。
- MySQL 驱动迁移：`mysql:mysql-connector-java` → **`com.mysql:mysql-connector-j`** 9.7.0（6 处引用）。
- 清理手工版本属性（mysql-connector / micrometer / lombok / freemarker / junit / testcontainers），
  统一交 Spring Boot BOM 管理，消除版本分裂。
- `application-dev.yml` 修复重复 `fastddd:` 顶层键（Boot 3.5 SnakeYAML 严格解析拒绝重复键）。

### Fixed（修复）

- **B-01**：`PerformanceIntegrationTest.testBatchInsertPerformance` 因"逻辑删除→重插相同 username"撞
  `username` UNIQUE 而失败；改用注入 `JdbcTemplate` 的物理 `DELETE` 清理。全量门禁 356 tests / 0 failures。
- `fast-ddd-examples/pom.xml` 预先存在的 `<relativePath>` 错误（`../../pom.xml` → `../pom.xml`）。
- `fast-ddd-web/logback-spring.xml` 两处被 logstash-encoder 忽略的无效 `<includeStackTrace>` 属性清理。

### Known Limitations / 遗留（不阻塞 1.0.0）

- **CI runner 未部署**：远程为内网 Gitea，`.github/workflows/*.yml` 为声明式文件、无 runner 执行；
  JDK 21 / wrapper / `./mvnw` / PMD7 改动属"CI 就绪即生效"基线，待 runner 部署后验证。
- **`checkstyle.xml` 规则文件缺失**：`configLocation=checkstyle.xml` 但仓库无此文件，
  code-quality 步骤带 `continue-on-error` 容错；补规则文件留独立任务。
- **外部基础设施运行时冒烟待补**：Redis、Elasticsearch、RocketMQ 分布式链路、Docker 构建启动健康检查
  在本机无对应运行时，仅做编译/上下文级验证；待部署环境或 Docker 就绪后补 smoke。
- **`SecurityIntegrationTest` 类级 `@Disabled`**：自引用 `@SpringBootTest(classes=自身)` 上下文加载问题，
  既有测试债（非本次升级引入、非为过门禁新加）。
- **Mockito self-attach 警告**：JDK 21 下 inline mock 自附加（全绿、覆盖率正常），
  显式 mockito agent 与 JaCoCo argLine 共存留 JDK 25+ 迁移时配置。
- **BOM 与根 pom 依赖清单重复**：本次为降低 reactor 解析风险，根 pom `<dependencyManagement>` 保持不变，
  BOM 独立镜像清单。登记为已知技术债，1.0.1 由根 pom `import` BOM 收敛（独立验证 reactor 解析）。

### 1.0.1 路线（已登记必做项）

- **二进制兼容性校验（japicmp / Revapi）**：1.0.0 无 GA 基线 jar，japicmp 无可比基准；
  建立首个 GA 基线后，1.0.1 起对每次发布做 API 兼容性校验。
- **根 pom 委托 BOM**：根 pom `<dependencyManagement>` 改为 `import` `fastddd-bom`，实现依赖版本单一来源。
- **生成模板切 BOM**：archetype-resources / FreeMarker 生成项目模板默认 `import` `fastddd-bom`，
  让生成的业务项目开箱即用统一依赖契约。

---

## 升级前开发快照（归档，不维护）

| 项 | 值 |
|----|----|
| Java | 17 |
| Spring Boot | 3.4.5 |
| FastDDD 版本字符串 | `1.0.0-SNAPSHOT` |
| 状态 | 仅 Git tag `baseline/jdk17-boot3.4.5` 归档，不再维护 |

> 该快照不作为任何兼容线对外承诺。所有在研业务系统应随 1.0.0 一起升级到 JDK 21 / Boot 3.5.16 基线
> （见 [docs/UPGRADE_GUIDE.zh-CN.md](docs/UPGRADE_GUIDE.zh-CN.md) 与 UPG-011）。
