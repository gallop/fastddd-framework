# Changelog

[English](CHANGELOG.md) | **简体中文**

本文件记录 FastDDD 所有显著变更，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

完整版本基线与兼容线见 [docs/COMPATIBILITY_MATRIX.zh-CN.md](docs/COMPATIBILITY_MATRIX.zh-CN.md)；
业务系统升级步骤见 [docs/UPGRADE_GUIDE.zh-CN.md](docs/UPGRADE_GUIDE.zh-CN.md)。

---

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
