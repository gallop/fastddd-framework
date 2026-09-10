# 升级指南（Upgrade Guide）

> 本指南指导业务系统从旧基线（JDK 17 / Spring Boot 3.4.5 / FastDDD `1.0.0-SNAPSHOT`）升级到
> **JDK 21 / Spring Boot 3.5.16 / FastDDD `1.0.0-jdk21-rc1`**。
>
> 本文是 JDK/Boot/BOM 的基础操作指引，不能替代完整的业务升级验收。使用 Framework 1.0 强类型 ID、CRUD、
> Security、Event、缓存或外部基础设施的业务系统，应按[业务系统升级入口](upgrade/README.md)的能力映射表加载
> 本文是 Community 通用升级指南；业务项目特定的灰度/UAT 计划应在各自私有仓库维护。
>
> 开始新业务系统升级时，先读 [业务系统升级入口](upgrade/README.md)；它明确了最小必读集、按功能追加阅读规则与验收门禁。
> 兼容线见 [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md)；发布历史见 [../CHANGELOG.md](../CHANGELOG.md)。

---

## 0. 升级前评估

| 项 | 旧基线 | 新基线 |
|----|--------|--------|
| Java | 17 | **21** |
| Spring Boot | 3.4.5 | **3.5.16** |
| FastDDD | `1.0.0-SNAPSHOT` | **`1.0.0-jdk21-rc1`**（当前固定 RC；GA 后为 `1.0.0`） |
| MySQL 驱动坐标 | `mysql:mysql-connector-java` | **`com.mysql:mysql-connector-j`** 9.7.0 |
| Druid 坐标 | `druid-spring-boot-starter` | **`druid-spring-boot-3-starter`** |

**本次升级包含构建基线抬升与 Framework 1.0 契约收紧。**Java 主版本 17→21 属构建/运行基线变更；
对于使用 `BasePO`、`IService`、`BaseServiceImpl`、`BaseController` 或旧 CRUD 辅助 API 的业务系统，
还需要完成强类型 ID 与源码适配。发布后的 1.0.x 补丁/次版本线保持兼容，但从旧 SNAPSHOT 迁入首个 1.0 RC/GA
不得假设“只改依赖即可完成”。

---

## 1. 安装并切换 JDK 21（硬前置）

FastDDD 1.0.x 编译/运行要求 **JDK 21+**。若机器默认 `JAVA_HOME` 仍指向 JDK 17，
Maven 会报"不支持的发行版本 21"直接 BUILD FAILURE。

```bash
# 确认 JDK 21 已安装
java -version   # 应输出 21.x

# 构建前显式切换（写入 CI/IDE/启动脚本）
export JAVA_HOME=<path-to-jdk-21>
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # 复核
```

> IDE（IntelliJ / VS Code）需将 Project SDK 与 Maven Runner JDK 均设为 21。

---

## 2. 升级 FastDDD 依赖：import fastddd-bom

推荐做法：业务项目 `import` `fastddd-bom`，FastDDD 全模块与推荐第三方依赖版本由 BOM 统一管理，
**无需逐个声明版本**。

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
        <version>1.0.0-jdk21-rc1</version>   <!-- GA 后改 1.0.0 -->
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

**前提**：FastDDD 已安装到本地/私服仓库。从 FastDDD 源码：
```bash
export JAVA_HOME=<path-to-jdk-21>; export PATH="$JAVA_HOME/bin:$PATH"
cd <fastddd-source>
./mvnw -B -Drevision=1.0.0-jdk21-rc1 clean install -DskipTests   # 安装含 fastddd-bom 在内的全部模块
```

### 禁止覆盖上游版本

业务项目 **不要** 显式声明/覆盖 Spring Boot、Spring、Jackson、Micrometer 等版本——它们经 FastDDD
与上游 BOM 联调验证。覆盖会引入版本分裂（升级前快照就曾因 `micrometer-registry-prometheus` 被 pin
到旧版而分裂）。统一交给 `spring-boot-starter-parent` + `fastddd-bom`。

### Tomcat 版本属性（仅 parent=spring-boot-starter-parent 的消费者）

fastddd-bom 已显式受管 `tomcat-embed-{core,el,websocket}` **10.1.59**（S3 安全整改：10.1.55 的
CVE-2026-65905/65182/68525 修复线）。是否需要你自行声明，取决于消费形态——**不是所有消费者都要配**：

| 消费形态 | 实际生效版本 | 所需动作 |
|---|---|---|
| 仅 `import` fastddd-bom（无 Boot parent） | 10.1.59（BOM 显式受管条目胜出） | 无 |
| parent = `spring-boot-starter-parent` | 默认仍是 Boot 3.5.16 管理的 10.1.55——**import 只填空，不覆盖从 parent 继承的 dependencyManagement** | 显式声明版本属性（1 行，下例） |

最小示例（W1 试点实测：baby-growth-claude 缺此行时 tomcat 停留 10.1.55，补 1 行即恢复 10.1.59；
import-only 的 xia-go 无需任何配置）：

```xml
<properties>
  <!-- 仅 parent=spring-boot-starter-parent 时需要；Boot 官方管理版本 ≥10.1.59 后本行可移除 -->
  <tomcat.version>10.1.59</tomcat.version>
</properties>
```

核查命令（确认实际解析到的 tomcat 版本）：

```bash
./mvnw dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
```

> `<tomcat.version>` 是本指南"禁止覆盖上游版本"的**唯一既定例外**：它走 Boot 官方版本属性通道
> （不是自定版本号），仅用于安全修复跟随。

---

## 3. 迁移变更的依赖坐标

### MySQL 驱动

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
> 9.7.0 驱动向下兼容 MySQL Server 8.x；连接 9.x 服务端也支持。

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

### Web/CRUD 依赖边界（连接池选型显式化，适用于包含 ECO-006C4 整改的版本线）

`fast-ddd-crud` 不再传递连接池与 MyBatis-Plus 官方 starter（连带 `spring-boot-starter-jdbc`/HikariCP 链），
改为传递 MyBatis-Plus 运行能力本身（`mybatis-plus` bundle + `mybatis-spring` + MyBatis-Plus 自动配置 + `spring-jdbc`）；
`druid-spring-boot-3-starter` 在 crud 变为 optional。聚合 `fast-ddd-starter` 显式携带 Druid
（"引入即含默认池"语义不变）。按消费形态自查：

| 消费形态 | 影响 | 所需动作 |
|---|---|---|
| 聚合 `fast-ddd-starter` | 默认池仍为 Druid | 无 |
| 仅 `fast-ddd-web`/`fast-ddd-crud`，**有数据库** | classpath 不再有传递连接池 | **显式选池**：`druid-spring-boot-3-starter`（保持 Druid）或 `spring-boot-starter-jdbc`（Hikari），二选一 |
| 仅 `fast-ddd-web`，**无数据库** | 从"必须配数据源才能启动"变为"正常启动"（收益） | 无 |
| 仅 `fast-ddd-model` | 只携带 `mybatis-plus-annotation`（注解） | 无 |

> **⚠️ 无池时的隐蔽失败模式**：已配 `spring.datasource.url` 但 classpath 无任何连接池时，
> DataSource 自动配置会**静默退让**——应用照常启动、无报错，但容器内**没有 DataSource/JdbcTemplate/Mapper Bean**，
> 首次使用数据访问才暴露（`NoSuchBeanDefinitionException`）。升级后务必核对启动日志中的连接池初始化行
> （Druid `{dataSource-1} inited` 或 `HikariPool-1 - Start completed`）。

核查命令：

```bash
./mvnw dependency:tree -Dincludes=com.alibaba:druid-spring-boot-3-starter,com.zaxxer:HikariCP,org.springframework.boot:spring-boot-starter-jdbc
```

若项目同时自带 `mybatis-plus-spring-boot3-starter` 且此前依赖 crud 传递的 Druid，升级后池型会变为 Hikari
（功能正常、池型翻转）；要保持 Druid 按上表显式补 `druid-spring-boot-3-starter`。

### Event 依赖边界（池中立化，适用于包含 ECO-006C6 整改的版本线）

`fast-ddd-event` 不再直依 `mybatis-plus-spring-boot3-starter`（连带 `spring-boot-starter-jdbc`/HikariCP 链），
改为传递 Outbox 实际必需的 MyBatis-Plus 运行能力四件套（`mybatis-plus` bundle + `mybatis-spring` +
MyBatis-Plus 自动配置 + `spring-jdbc`，均为池中立）。默认 local 模式（无库、无配置、无排除）开箱即用；
outbox 模式由消费者显式提供连接池。按消费形态自查：

| 消费形态 | 影响 | 所需动作 |
|---|---|---|
| 仅 `fast-ddd-event`，local 模式（默认） | 从"必须配数据源/排除自动配置才能启动"变为"正常启动"（收益） | 无（既有 `spring.autoconfigure.exclude` 排除配置可保留，不冲突） |
| `fast-ddd-event`，outbox 模式 | classpath 不再有传递连接池 | **显式选池**：`spring-boot-starter-jdbc`（Hikari）或 `druid-spring-boot-3-starter`，并自备 `outbox_events` DDL |
| `fast-ddd-event`，outbox 模式且**非 web** | `JacksonAutoConfiguration` 的 `ObjectMapper` 依赖 spring-web 的 builder，不在场 | 消费者自备 `ObjectMapper`（建议 `findAndRegisterModules()`），并显式依赖 `jackson-datatype-jsr310`（`DomainEvent.createdAt` 为 `Instant`；web 消费者经 starter-json 自动获得，无需处理） |

> **⚠️ 池静默退让与上节同型**：已配 `spring.datasource.*` 但无任何池实现时 outbox 装配整体退让
> （`@ConditionalOnSingleCandidate(DataSource)` 不成立），启动不报错但无 outbox Bean——核对启动日志连接池初始化行。

核查命令（确认 event 不再传递池与官方 starter）：

```bash
./mvnw dependency:tree -Dincludes=com.zaxxer:HikariCP,com.alibaba:druid-spring-boot-3-starter,com.baomidou:mybatis-plus-spring-boot3-starter
```

### Security 依赖边界（审计持久化最小化，适用于包含 ECO-006C2 整改的版本线）

`fast-ddd-security-starter` 不再传递 MyBatis-Plus 官方 starter 及其连接池链（`spring-boot-starter-jdbc`/HikariCP）：
模块对 MyBatis-Plus 的使用仅为审计实体的注解（以 `mybatis-plus-annotation` 保留可见），审计写入走
`JdbcTemplate` 手写 INSERT（`spring-jdbc` 仅提供类型签名）。按消费形态自查：

| 消费形态 | 影响 | 所需动作 |
|---|---|---|
| 仅 security-starter，**无数据库** | 无 DataSource Bean 也不启动失败：审计自动退回 SLF4J 输出（无库审计回退） | 无 |
| security-starter + 数据库审计 | 需要 DataSource/JdbcTemplate Bean | **显式选池**（与 Web/CRUD、Event 同规则）：`druid-spring-boot-3-starter` 或 `spring-boot-starter-jdbc`，二选一 |
| 项目自带 Security 组件 | 框架 `@ConditionalOnMissingBean` 退让族（PasswordEncoder、SecurityFilterChain 等）让位产品装配 | 无（产品 Bean 胜出，jwt 两态均可启动） |

> **审计表列宽与存量迁移**：审计持久化的 `method` 列已由 `VARCHAR(10)` 扩至 `VARCHAR(200)`，表结构由
> 框架自动建表 DDL 创建；**框架不会自动 ALTER 已有用户库**——存量窄列表的迁移路径见
> [security/AUDIT_LOG_TABLE_MIGRATION.md](security/AUDIT_LOG_TABLE_MIGRATION.md)。

核查命令（确认 security 不再传递官方 starter 与池）：

```bash
./mvnw dependency:tree -Dincludes=com.baomidou:mybatis-plus-spring-boot3-starter,com.zaxxer:HikariCP,org.springframework.boot:spring-boot-starter-jdbc
```

---

## 4. 配置审查（Boot 3.5 严格解析）

Boot 3.5 的 SnakeYAML 对**重复 YAML 顶层键**由宽容改为严格拒绝。升级前自查 `application*.yml`：

- 重复的顶层键（如两处 `fastddd:`、两处 `spring:` 合并不当）→ 合并为单一键。
- `.properties` 不受影响，但仍建议清理重复键。

Druid 在 Boot 3.5 下的自动配置由 `druid-spring-boot-3-starter` 提供，确认 starter 坐标正确（见上节）后，
原 `spring.datasource.druid.*` 配置无需改动。

### 本地秘密配置与启动前检查

受跟踪的 `application-*.yml` 只能引用环境变量，例如 `${JWT_SECRET}`；实际值放在被忽略的 `.env`、受管密钥服务或 IDE/CI 环境变量中。建议提交 `.env.example`，但其中不得有可用秘密。

如果应用支持 `.env` 自动加载，必须明确加载路径与进程工作目录的关系。旧配置中若出现 `${LLM_API_KEY:旧默认值}`，迁移时不要把整段表达式写进 `LLM_API_KEY`，否则会产生循环占位符；应填入轮换后的最终值。

FastDDD 1.0 默认 HMAC JWT 使用 HS512。`JWT_SECRET` 必须至少 64 个 UTF-8 字节；旧项目的短密钥会在应用上下文创建前被拒绝，这是应修复的安全配置而非应绕过的兼容问题。

---

## 5. 构建与回归

```bash
# 1. 全量编译
./mvnw -B clean compile

# 2. 默认单元测试（不依赖共享数据库或真实第三方服务）
./mvnw -B clean test

# 3. 打包
./mvnw -B clean package -DskipTests
```

真实数据库、消息或第三方服务测试应使用单独的 Failsafe profile（通常命名为 `*IT` 或 `*IntegrationTest`），只在受控环境注入所需变量后执行，例如：

```bash
./mvnw -B -P integration-test verify
```

若从独立启动模块运行 `spring-boot:run`，先完成一次全 Reactor `install`，再从含主类的模块执行；不要在聚合根 POM 上运行该目标。启动验收必须同时检查监听端口和 `/actuator/health` 返回 `UP`。

### 回归清单

- [ ] 应用正常启动（日志含 `Spring Boot v3.5.16`、Druid `{dataSource-1} inited`）。
- [ ] 数据库 CRUD 正常（MySQL 驱动 9.7.0 + Druid 3-starter）。
- [ ] API 文档端点可达（SpringDoc 2.8.17 / Knife4j 4.5.0：`/v3/api-docs`、`/doc.html`）。
- [ ] 安全认证（JWT / 验证码）链路正常（fast-ddd-security-starter）。
- [ ] 监控指标（`/actuator/prometheus`，Micrometer 1.15.12）正常输出。
- [ ] 代码生成插件可用（仅项目实际使用时）：`./mvnw io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-rc1:help`。
- [ ] 自身业务核心用例全量回归（业务侧主导）。

---

## 6. 常见问题

**Q: 构建报"不支持的发行版本 21"？**
A: 构建用的 JDK 不是 21。按第 1 节显式切换 `JAVA_HOME` 到 JDK 21（CI runner 同理）。

**Q: 启动报 `while scanning a simple key` / 重复键解析错误？**
A: `application*.yml` 有重复顶层键，按第 4 节合并。

**Q: 找不到 `druid-spring-boot-starter` 自动配置 / Druid 连接池未生效？**
A: 仍用了 Boot 2 坐标 `druid-spring-boot-starter`，按第 3 节迁移到 `druid-spring-boot-3-starter`。

**Q: 升级后应用能正常启动，但 Mapper/`JdbcTemplate` 注入报 `NoSuchBeanDefinitionException`？**
A: ECO-006C4 起 `fast-ddd-crud` 不再传递连接池。仅引 web/crud 且需要数据库的项目必须显式选池
（`druid-spring-boot-3-starter` 或 `spring-boot-starter-jdbc`），见第 3 节"Web/CRUD 依赖边界"——
无池时 DataSource 自动配置静默退让，启动不报错。

**Q: 升级后连接池从 Druid 变成了 Hikari（日志出现 `HikariPool`）？**
A: 项目自带 `mybatis-plus-spring-boot3-starter`（自带 Hikari 链），而此前 Druid 来自 crud 的传递依赖。
该传递已移除，双池并存时"Druid 胜出"的旧默认不复存在；要保持 Druid 池型与既有
`spring.datasource.druid.*` 配置，显式补 `druid-spring-boot-3-starter`。

**Q: `com.mysql:mysql-connector-j` 解析不到？**
A: 旧坐标 `mysql:mysql-connector-java` 已废弃，按第 3 节迁移；版本交 Spring Boot BOM 管理。

**Q: 是否只改依赖就够了？**
A: 只使用 Starter 且 `clean compile`、测试、启动和关键链路均通过的项目，可能只需依赖与配置迁移；不能预先假设如此。若出现 `BasePO`、`IService`、`BaseServiceImpl`、`BaseController` 的泛型/ID 编译错误，按[升级入口](upgrade/README.md)加载 ID 迁移清单完成业务侧适配。不要为保留旧调用而修改 Framework 契约。

**Q: 应用启动时提示 `JWT_SECRET` 不足 64 字节？**
A: FastDDD 1.0 的 HS512 校验已生效。为当前环境生成并注入至少 64 UTF-8 字节的独立密钥；不要恢复旧短密钥或降低校验。

**Q: 配置提示 `Circular placeholder reference`，涉及 LLM API Key？**
A: 检查 `.env` 是否把 `${LLM_API_KEY:...}` 这类旧 YAML 嵌套占位符原样写到了 `LLM_API_KEY`。`.env` 应保存最终值而不是 Spring 占位表达式。

**Q: `spring-boot:run` 提示找不到 main class 或单模块编译出现旧接口？**
A: 前者通常是从聚合根而非启动模块执行；后者通常是单模块解析到过期本地业务 JAR。先执行全 Reactor `install`，再从包含主类的模块运行。

---

## 7. 回退

升级以**单分支**进行，出问题可回退到升级前分支/tag。FastDDD 源码侧不使用 `git reset --hard`，
单文件用 `git restore <file>` 回退；业务系统侧按各自版本控制策略回退依赖版本。
