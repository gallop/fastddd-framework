# 升级指南（Upgrade Guide）

> 本指南指导业务系统从旧基线（JDK 17 / Spring Boot 3.4.5 / FastDDD `1.0.0-SNAPSHOT`）升级到
> **JDK 21 / Spring Boot 3.5.16 / FastDDD `1.0.0-jdk21-rc1`**。
>
> 本文是 JDK/Boot/BOM 的基础操作指引，不能替代完整的业务升级验收。使用 Framework 1.0 强类型 ID、CRUD、
> Security、Event、缓存或外部基础设施的业务系统，应按[业务系统升级入口](upgrade/README.md)的能力映射表加载
> 专题指南；多外部依赖或需要灰度/UAT 的复杂系统，再阅读[业务系统升级与试点实施方案](architecture/BUSINESS_SYSTEM_UPGRADE_PLAN.md)。
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
        <groupId>com.gallop.fastddd</groupId>
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
      <groupId>com.gallop.fastddd</groupId>
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
- [ ] 代码生成插件可用（仅项目实际使用时）：`./mvnw com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-rc1:help`。
- [ ] 自身业务核心用例全量回归（业务侧主导）。

---

## 6. 常见问题

**Q: 构建报"不支持的发行版本 21"？**
A: 构建用的 JDK 不是 21。按第 1 节显式切换 `JAVA_HOME` 到 JDK 21（CI runner 同理）。

**Q: 启动报 `while scanning a simple key` / 重复键解析错误？**
A: `application*.yml` 有重复顶层键，按第 4 节合并。

**Q: 找不到 `druid-spring-boot-starter` 自动配置 / Druid 连接池未生效？**
A: 仍用了 Boot 2 坐标 `druid-spring-boot-starter`，按第 3 节迁移到 `druid-spring-boot-3-starter`。

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
