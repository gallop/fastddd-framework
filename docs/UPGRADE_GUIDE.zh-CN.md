# 升级指南（Upgrade Guide）

[English](UPGRADE_GUIDE.md) | **简体中文**

> 本指南指导业务系统从旧基线（JDK 17 / Spring Boot 3.4.5 / FastDDD `1.0.0-SNAPSHOT`）升级到
> **JDK 21 / Spring Boot 3.5.16 / FastDDD `io.github.gallop.fastddd:1.0.0`**。
>
> 已在 `1.0.0-jdk21-rc1`（旧坐标 `com.gallop.fastddd`）上的项目，升级到 1.0.0 只需完成
> [第 3 节 Maven 坐标迁移](#3-fastddd-maven-坐标迁移100-ga)，源码零改动。
>
> 本文是 Community 通用升级指南：覆盖 JDK/Boot/BOM 基础操作（第 1/2 节）与 1.0.0 Maven 坐标迁移（第 3 节），
> 但不能替代完整的业务升级验收。使用 Framework 1.0 强类型 ID、CRUD、Security、Event、缓存或外部基础设施的
> 业务系统，应按[业务系统升级入口](upgrade/README.zh-CN.md)的能力映射表加载对应补充指南；业务项目特定的灰度/UAT
> 计划应在各自私有仓库维护。
>
> 开始新业务系统升级时，先读 [业务系统升级入口](upgrade/README.zh-CN.md)；它明确了最小必读集、按功能追加阅读规则与验收门禁。
> 兼容线见 [COMPATIBILITY_MATRIX.zh-CN.md](COMPATIBILITY_MATRIX.zh-CN.md)（矩阵 `current` 为框架开发线 SNAPSHOT 口径，
> 消费者升级版本以本文 §0/§3 的 `1.0.0` 为准）；发布历史见 [../CHANGELOG.zh-CN.md](../CHANGELOG.zh-CN.md)。

---

## 0. 升级前评估

| 项 | 旧基线 | 新基线 |
|----|--------|--------|
| Java | 17 | **21** |
| Spring Boot | 3.4.5 | **3.5.16** |
| FastDDD 版本 | `1.0.0-SNAPSHOT`（或 `1.0.0-jdk21-rc1`） | **`1.0.0`** |
| FastDDD groupId | `com.gallop.fastddd` | **`io.github.gallop.fastddd`**（见第 3 节） |
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

> **parent 策略**：1.0 起推荐 `parent = spring-boot-starter-parent` + `import fastddd-bom`（下例）。
> 若项目当前 parent 是旧 `com.gallop.fastddd:fastddd-framework-parent` 且想保留该继承，只需把 parent 块的
> `<groupId>` 换成 `io.github.gallop.fastddd`（第 3.3 节 sed 已覆盖）；两条路径二选一，不要混用。

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

**前提**：FastDDD 已安装到本地/私服仓库。从 FastDDD 源码：
```bash
export JAVA_HOME=<path-to-jdk-21>; export PATH="$JAVA_HOME/bin:$PATH"
cd <fastddd-source>
./mvnw -B -Drevision=1.0.0 clean install -DskipTests   # 安装含 fastddd-bom 在内的全部模块
```

> **双版本轴**：上面命令安装的只是 **Framework 轴**（`io.github.gallop.fastddd:fast-ddd-*` + `fastddd-bom`，当前 1.0.0）——
> 即业务项目消费的依赖。代码生成工具（`fastddd-maven-plugin`、`fastddd-templates`，当前 **1.0.2**）位于独立的
> `fastddd-tooling` 仓库，属于**另一条独立版本轴**：禁止把 Tooling 版本写进 framework 依赖，也禁止把 Framework
> 版本写进插件坐标。本地源码安装插件需在 tooling 仓库另跑 `./mvnw -B -Drevision=1.0.2 clean install -DskipTests`——
> 从发布仓消费的业务项目通常无需此步。

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

## 3. FastDDD Maven 坐标迁移（1.0.0 GA）

Framework / Tooling 自 1.0.0 起迁移 Maven groupId：`com.gallop.fastddd` → `io.github.gallop.fastddd`。
本节是 1.0.0 升级的**必做步骤**，与 JDK/Boot 基线抬升相互独立。

### 3.1 坐标三分法：什么变，什么不变

| 维度 | 状态 | 说明 |
|---|---|---|
| Maven groupId | **变**：`com.gallop.fastddd` → `io.github.gallop.fastddd` | 全部 28 个发布制品（含 parent/BOM/插件） |
| artifactId | **不变** | `fast-ddd-*`（模块）、`fastddd-*`（聚合/BOM/Tooling） |
| Java 包名 | **不变**：`com.gallop.fastddd.*` | **业务源码与 import 零改动**；classpath 上的类路径仍是 `com/gallop/fastddd/**` |
| 版本号 | **变**：`1.0.0-jdk21-rc1` → `1.0.0`（RC1 消费者）；`1.0.0-SNAPSHOT` 消费者见第 0 节契约收紧评估 | 与 groupId 迁移是同一次发布 |

> **特殊保留坐标**：`com.gallop.fastddd:fastddd-studio*`（Studio 私有制品，如 `fastddd-studio-server`）
> **不在本次迁移范围**——它们仍使用旧 groupId，属于设计性保留。业务项目不消费这些制品；
> 这也是第 3.5 节门禁**不能**笼统禁止整个旧 groupId 的原因。

### 3.2 新旧坐标映射表（全部 28 个发布制品）

升级 = 把下表制品的 `<groupId>` 与 `<version>` 替换为新值，`<artifactId>` 原样保留。

**Framework 侧（17）**

| 类别 | artifactId | 旧坐标（rc1 及更早） | 新坐标（1.0.0） |
|---|---|---|---|
| parent POM | `fastddd-framework-parent` | `com.gallop.fastddd:fastddd-framework-parent` | `io.github.gallop.fastddd:fastddd-framework-parent:1.0.0` |
| BOM | `fastddd-bom` | `com.gallop.fastddd:fastddd-bom` | `io.github.gallop.fastddd:fastddd-bom:1.0.0` |
| 聚合（源码构建入口，非依赖） | `fastddd-framework` | `com.gallop.fastddd:fastddd-framework` | `io.github.gallop.fastddd:fastddd-framework:1.0.0` |
| 聚合 starter | `fast-ddd-starter` | `com.gallop.fastddd:fast-ddd-starter` | `io.github.gallop.fastddd:fast-ddd-starter:1.0.0` |
| 模块 | `fast-ddd-common` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-common:1.0.0` |
| 模块 | `fast-ddd-config` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-config:1.0.0` |
| 模块 | `fast-ddd-model` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-model:1.0.0` |
| 模块 | `fast-ddd-crud` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-crud:1.0.0` |
| 模块 | `fast-ddd-web` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-web:1.0.0` |
| 模块 | `fast-ddd-application` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-application:1.0.0` |
| 模块 | `fast-ddd-cache` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-cache:1.0.0` |
| 模块 | `fast-ddd-event` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-event:1.0.0` |
| 模块 | `fast-ddd-search` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-search:1.0.0` |
| 模块 | `fast-ddd-monitor` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-monitor:1.0.0` |
| 模块 | `fast-ddd-security-starter` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-security-starter:1.0.0` |
| 模块（Incubating） | `fast-ddd-ai-spring-boot-starter` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-ai-spring-boot-starter:1.0.0` |
| 模块（Incubating） | `fast-ddd-analyzer-spring-boot-starter` | 同上规律 | `io.github.gallop.fastddd:fast-ddd-analyzer-spring-boot-starter:1.0.0` |

**Tooling 侧（11）**

| 类别 | artifactId | 旧坐标（rc1 及更早） | 新坐标（1.0.0） |
|---|---|---|---|
| parent POM | `fastddd-tooling-parent` | `com.gallop.fastddd:fastddd-tooling-parent` | `io.github.gallop.fastddd:fastddd-tooling-parent:1.0.0` |
| 聚合（源码构建入口） | `fastddd-tooling` | `com.gallop.fastddd:fastddd-tooling` | `io.github.gallop.fastddd:fastddd-tooling:1.0.0` |
| 稳定契约层 | `fastddd-tooling-api` | `com.gallop.fastddd:fastddd-tooling-api` | `io.github.gallop.fastddd:fastddd-tooling-api:1.0.0` |
| 代码生成 | `fastddd-code-generator` | `com.gallop.fastddd:fastddd-code-generator` | `io.github.gallop.fastddd:fastddd-code-generator:1.0.0` |
| 代码生成模板 | `fastddd-templates` | `com.gallop.fastddd:fastddd-templates` | `io.github.gallop.fastddd:fastddd-templates:1.0.0` |
| 脚手架（Deprecated，保留兼容） | `fastddd-project-archetype` | `com.gallop.fastddd:fastddd-project-archetype` | `io.github.gallop.fastddd:fastddd-project-archetype:1.0.0` |
| Maven 插件（`fastddd:` 前缀） | `fastddd-maven-plugin` | `com.gallop.fastddd:fastddd-maven-plugin` | `io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0` |
| 文档生成 | `fastddd-doc-generator` | `com.gallop.fastddd:fastddd-doc-generator` | `io.github.gallop.fastddd:fastddd-doc-generator:1.0.0` |
| Maven 插件（`fastdoc:` 前缀） | `fastddd-doc-maven-plugin` | `com.gallop.fastddd:fastddd-doc-maven-plugin` | `io.github.gallop.fastddd:fastddd-doc-maven-plugin:1.0.0` |
| FastDoc 核心 | `fastddd-fastdoc-core` | `com.gallop.fastddd:fastddd-fastdoc-core` | `io.github.gallop.fastddd:fastddd-fastdoc-core:1.0.0` |
| FastDoc 运行期集成 | `fastddd-fastdoc-spring-boot-starter` | `com.gallop.fastddd:fastddd-fastdoc-spring-boot-starter` | `io.github.gallop.fastddd:fastddd-fastdoc-spring-boot-starter:1.0.0` |

> 版本轴独立：Framework 与 Tooling 各自发布 1.0.0（首个认证元组两者恰好同版本，不代表锁步）。
> 历史旧单体制品（如 `fast-ddd-code-generator` 等不在上表的旧 artifactId）**不在 1.0 迁移清单内**，
> 仍在使用旧单体制品的项目应先迁移到 RC1/GA 坐标系。

### 3.3 POM 修改（RC1 → 1.0.0 最小模式）

RC1 消费者经 W1 双项目实测，升级仅 POM 层三类替换（源码、配置零改动）：

| 修改点 | 前 | 后 |
|---|---|---|
| 版本属性/字面量 | `1.0.0-jdk21-rc1` | `1.0.0` |
| BOM import groupId（根 pom） | `com.gallop.fastddd` | `io.github.gallop.fastddd` |
| 依赖 groupId（各子模块 pom） | `com.gallop.fastddd` | `io.github.gallop.fastddd` |

等价批量替换（对全部 pom.xml；注意只匹配 `<groupId>` 整行，不伤业务自身 groupId 如 `com.gallop.xiago`）：

```bash
find . -name pom.xml -exec sed -i \
  's|<fastddd\.version>1\.0\.0-jdk21-rc1</fastddd\.version>|<fastddd.version>1.0.0</fastddd.version>|; \
   s|<version>1\.0\.0-jdk21-rc1</version>|<version>1.0.0</version>|; \
   s|<groupId>com\.gallop\.fastddd</groupId>|<groupId>io.github.gallop.fastddd</groupId>|g' {} +
```

**不要**全局替换 `com.gallop.fastddd` 字符串：Java 源码与 import（`com.gallop.fastddd.*` 包名）必须保留；
`com.gallop.fastddd:fastddd-studio*`（若某工具链引用）也保留。替换后复核零残留：
`grep -rn -E "1\.0\.0-jdk21-rc1|<groupId>com\.gallop\.fastddd</groupId>" --include=pom.xml .`
（`fastddd-studio*` 相关行除外）。

### 3.4 制品获取渠道（内部 release 仓）

1.0.0 为**内部发布**：制品存在于内部 release 仓与已同步的开发机本地 Maven 仓，公开渠道
（Maven Central 等）暂不可用。按可达形态三选一：

**形态 A——已同步本地仓的机器**：无需任何配置，直接构建（本地仓为只读复制，正常解析）。

**形态 B——经 settings.xml 指向内部仓**（file:// 协议，`<snapshots>` 关闭）：

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
mvn -B -s <上述 settings 路径> clean test
```

**形态 C——一次性显式指定**：`mvn -s <内部仓 settings 文件> clean test`（内部仓随发布附带
`settings-release.xml`）。**注意**：该随附文件为作者本机示例，`<url>` 已写死作者机器绝对路径；非作者机器
使用前须将其 `<url>` 改写为 `${env.FASTDDD_INTERNAL_REPO}`（同形态 B）或本机实际 file:// 路径，否则解析不到制品。

公开发布（Maven Central）落地后，本节自动失效，消费者按公开坐标直接解析；届时只需移除内部仓
profile，坐标与版本不变。

### 3.5 防混装门禁（Maven Enforcer bannedDependencies）

坐标迁移的最大风险是**新旧混装**：旧 `com.gallop.fastddd` 制品与新 `io.github.gallop.fastddd` 制品
在 classpath 并存——两者类路径完全相同（`com/gallop/fastddd/**`），jar 会互相遮蔽，产生难排查的
运行期行为异常。且**本地仓中旧组同 artifactId 存在 `1.0.0` 版本目录**，"版本号一致"检查
拦不住旧组解析，门禁必须按**坐标存在性**判定。

在业务项目根 pom（或父 POM）加入：

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

语义要点：

- **按坐标禁用而非按版本**：exclude 不带版本段 = 该 artifactId 在旧组下**任何版本**都不允许出现
  （含旧组 `1.0.0`——本地仓确实存在，版本一致性检查会漏）。
- **禁止写成 `<exclude>com.gallop.fastddd</exclude>` 或 `<exclude>com.gallop.fastddd:*</exclude>`**：
  会误伤 `fastddd-studio*`（Studio 保留坐标）及业务自有 `com.gallop.*` groupId。
- `searchTransitive=true` 使规则覆盖**传递依赖**——最常见的混装源是某个依赖库还停在旧坐标。

**清单边界**：上述 28 条 = **已迁移模块**的 artifactId（artifactId 在迁移中保持不变的制品）。旧组
`com.gallop.fastddd` 下另有**改名/拆分/退役**坐标不在本清单：旧根聚合器 `fastddd`（迁移中改名为
`fastddd-framework-parent`/`fastddd-tooling-parent`，旧 artifactId 已废弃）、退役旧单体制品
`fast-ddd-examples`、`fastddd-consumer-tests`、`fast-ddd-core`、`fast-ddd-demo-app`、`fast-ddd-code-generator` 等。
这些坐标不作为运行时模块依赖被消费者引用：旧根 `fastddd` 若被引用仅会出现在 `<parent>`（由下方第 1 处
effective-pom 人工核查覆盖），退役 jar 若混入由第 3 处最终包检查覆盖。因此 28 条不覆盖旧组**全部**历史
发布面，而是覆盖"迁移后仍以同 artifactId 存在、会进依赖树"的制品——这正是"不能笼统禁整个旧 groupId"的另一面。

**死条目提示**：清单中 `fastddd-framework-parent`/`fastddd-bom`/`fastddd-framework`/`fastddd-tooling-parent`/
`fastddd-tooling`/`fastddd-project-archetype`/`fastddd-maven-plugin`/`fastddd-doc-maven-plugin` 属 parent/BOM/
聚合/插件坐标，**不会出现在依赖树**，bannedDependencies 不会命中它们（列入仅为 28 制品清单完整性占位）；
这三类由下方 effective-pom/插件引用人工核查覆盖，勿以"Enforcer 通过"推断 parent/BOM/插件也已干净。

Enforcer 之外的三处人工/脚本核查（bannedDependencies 管不到的位置）：

1. **parent 引用**：`effective-pom` 的 `<parent>` 无 `com.gallop.fastddd`（Enforcer 不校验 parent 链）：
   ```bash
   ./mvnw -B help:effective-pom -Doutput=effective.xml
   grep -n "com.gallop.fastddd" effective.xml   # 期望仅 fastddd-studio* 或 0 命中
   ```
2. **BOM import 与插件引用**：`<dependencyManagement>` import、`<build><plugins>` 的 Tooling 插件
   （`fastddd-maven-plugin`/`fastddd-doc-maven-plugin`）坐标均已切换（effective-pom 一并覆盖）。
3. **最终包制品面**：boot jar 内 `BOOT-INF/lib` 的 fastddd jar 逐一核对内嵌 `pom.properties` 的
   `groupId=`（新旧组同 artifactId 同版本**文件名完全相同**，只能核 jar 内元数据）：
   ```bash
   unzip -l target/*.jar | grep -E "BOOT-INF/lib/fast-?ddd.*\.jar"    # 清点 fastddd 制品
   unzip -p target/*.jar BOOT-INF/lib/fast-ddd-common-1.0.0.jar > /tmp/fdc.jar
   unzip -p /tmp/fdc.jar "META-INF/maven/*/fast-ddd-common/pom.properties" | grep groupId
   # 期望输出 groupId=io.github.gallop.fastddd；出现 com.gallop.fastddd 即混装（fastddd-studio* 除外）
   ```

### 3.6 升级验收四层检查

1. **依赖树**：`./mvnw -B dependency:tree` → fastddd 制品全部 `io.github.gallop.fastddd:*:jar:1.0.0`，
   旧组 0 命中、SNAPSHOT 0 命中（Enforcer 已自动化此层，tree 用于出具证据）；
2. **effective-pom**：parent/BOM import/插件三处无旧组引用（第 3.5 节核查命令）；
3. **最终包**：boot jar `BOOT-INF/lib` 无旧组 fastddd jar、`pom.properties` groupId 正确；
4. **业务冒烟**：按第 6 节回归清单执行（连接池初始化行、`/actuator/health` UP、核心用例）。

W1 两个真实项目的四层证据（6 模块 SUCCESS + 15 测试 + 树零混装）见发布证据
`internal-v1.0.0/evidence/UPGRADE-VERIFY-w1.md`。

---

## 4. 迁移变更的依赖坐标

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

## 5. 配置审查（Boot 3.5 严格解析）

Boot 3.5 的 SnakeYAML 对**重复 YAML 顶层键**由宽容改为严格拒绝。升级前自查 `application*.yml`：

- 重复的顶层键（如两处 `fastddd:`、两处 `spring:` 合并不当）→ 合并为单一键。
- `.properties` 不受影响，但仍建议清理重复键。

Druid 在 Boot 3.5 下的自动配置由 `druid-spring-boot-3-starter` 提供，确认 starter 坐标正确（见上节）后，
原 `spring.datasource.druid.*` 配置无需改动。

### 1.0.1 补丁注记：审计 success 判型（行为变更，附回退开关）

FastDDD 1.0.1 修复 `@AuditLog` 切面对失败业务请求的失真（此前 controller 正常返回失败信封时恒记 `success=1`——最显性=`/api/auth/login` 失败登录被记成功）。升级 1.0.1 后，返回的 `ApiResponse` 失败信封（直返或一层 `ResponseEntity` body，`code != 200` 含 `null`）记 `success=0` 且信封 message 非空白时落 `error_message`；非信封与异步返回维持既有语义，守卫绝不影响被审计请求路径（完整判型边界见 `CHANGELOG.zh-CN.md` 的 `[1.0.1]` 条目）。

若消费方须暂时保持 1.0.0 恒成功语义，置 `fastddd.security.audit.result-judging.enabled=false`（缺省 `true`；键须 kebab-case 全字面量——`@Value` 无宽松绑定）。该开关定位为过渡措施，下游审计消费面对齐修正语义后应移除。

### 本地秘密配置与启动前检查

受跟踪的 `application-*.yml` 只能引用环境变量，例如 `${JWT_SECRET}`；实际值放在被忽略的 `.env`、受管密钥服务或 IDE/CI 环境变量中。建议提交 `.env.example`，但其中不得有可用秘密。

如果应用支持 `.env` 自动加载，必须明确加载路径与进程工作目录的关系。旧配置中若出现 `${LLM_API_KEY:旧默认值}`，迁移时不要把整段表达式写进 `LLM_API_KEY`，否则会产生循环占位符；应填入轮换后的最终值。

FastDDD 1.0 默认 HMAC JWT 使用 HS512。`JWT_SECRET` 必须至少 64 个 UTF-8 字节；旧项目的短密钥会在应用上下文创建前被拒绝，这是应修复的安全配置而非应绕过的兼容问题。

---

## 6. 构建与回归

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
- [ ] 代码生成插件可用（仅项目实际使用时）：`./mvnw io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:help`。
- [ ] 自身业务核心用例全量回归（业务侧主导）。

---

## 7. 常见问题

**Q: 构建报"不支持的发行版本 21"？**
A: 构建用的 JDK 不是 21。按第 1 节显式切换 `JAVA_HOME` 到 JDK 21（CI runner 同理）。

**Q: 启动报 `while scanning a simple key` / 重复键解析错误？**
A: `application*.yml` 有重复顶层键，按第 5 节合并。

**Q: 找不到 `druid-spring-boot-starter` 自动配置 / Druid 连接池未生效？**
A: 仍用了 Boot 2 坐标 `druid-spring-boot-starter`，按第 4 节迁移到 `druid-spring-boot-3-starter`。

**Q: 升级后应用能正常启动，但 Mapper/`JdbcTemplate` 注入报 `NoSuchBeanDefinitionException`？**
A: ECO-006C4 起 `fast-ddd-crud` 不再传递连接池。仅引 web/crud 且需要数据库的项目必须显式选池
（`druid-spring-boot-3-starter` 或 `spring-boot-starter-jdbc`），见第 4 节"Web/CRUD 依赖边界"——
无池时 DataSource 自动配置静默退让，启动不报错。

**Q: 升级后连接池从 Druid 变成了 Hikari（日志出现 `HikariPool`）？**
A: 项目自带 `mybatis-plus-spring-boot3-starter`（自带 Hikari 链），而此前 Druid 来自 crud 的传递依赖。
该传递已移除，双池并存时"Druid 胜出"的旧默认不复存在；要保持 Druid 池型与既有
`spring.datasource.druid.*` 配置，显式补 `druid-spring-boot-3-starter`。

**Q: `com.mysql:mysql-connector-j` 解析不到？**
A: 旧坐标 `mysql:mysql-connector-java` 已废弃，按第 4 节迁移；版本交 Spring Boot BOM 管理。

**Q: 是否只改依赖就够了？**
A: 只使用 Starter 且 `clean compile`、测试、启动和关键链路均通过的项目，可能只需依赖与配置迁移；不能预先假设如此。若出现 `BasePO`、`IService`、`BaseServiceImpl`、`BaseController` 的泛型/ID 编译错误，按[升级入口](upgrade/README.zh-CN.md)加载 ID 迁移清单完成业务侧适配。不要为保留旧调用而修改 Framework 契约。

**Q: 应用启动时提示 `JWT_SECRET` 不足 64 字节？**
A: FastDDD 1.0 的 HS512 校验已生效。为当前环境生成并注入至少 64 UTF-8 字节的独立密钥；不要恢复旧短密钥或降低校验。

**Q: 配置提示 `Circular placeholder reference`，涉及 LLM API Key？**
A: 检查 `.env` 是否把 `${LLM_API_KEY:...}` 这类旧 YAML 嵌套占位符原样写到了 `LLM_API_KEY`。`.env` 应保存最终值而不是 Spring 占位表达式。

**Q: `spring-boot:run` 提示找不到 main class 或单模块编译出现旧接口？**
A: 前者通常是从聚合根而非启动模块执行；后者通常是单模块解析到过期本地业务 JAR。先执行全 Reactor `install`，再从包含主类的模块运行。

**Q: Enforcer 报 bannedDependencies 检出 `com.gallop.fastddd:*`？**
A: 某个直接依赖或传递依赖还停在旧坐标。用 `./mvnw dependency:tree -Dincludes=com.gallop.fastddd`
定位引入来源，把该依赖升级到已迁移版本；无法立即升级的第三方库依赖可在 `dependencyManagement`
中将旧坐标强制替换为新坐标等价物。注意检出 `fastddd-studio*` 属 Studio 保留坐标，不在禁用清单，不会触发此报错。

**Q: 为什么本地仓有 `com.gallop.fastddd:fast-ddd-common:1.0.0`，版本号一样还算混装？**
A: 算。坐标迁移后 groupId 即身份——旧组 `1.0.0` 与新组 `1.0.0` 是两个不同制品，类路径相同的 jar
会互相遮蔽。门禁按坐标存在性判定（不带版本段），正是为此。

---

## 8. 回退

升级以**单分支**进行，出问题可回退到升级前分支/tag。FastDDD 源码侧不使用 `git reset --hard`，
单文件用 `git restore <file>` 回退；业务系统侧按各自版本控制策略回退依赖版本。

### 8.1 坐标迁移的回退操作

回退 1.0.0 → RC1 = 撤销第 3.3 节三类 POM 替换（属性/字面量、BOM groupId、依赖 groupId），
源码无需动。该通道经 W1 双项目演练验证（回退后版本、池型、行为全数回归基线）。

### 8.2 回退边界与已知风险（重要）

- **不建议生产环境回退**到 RC1 / 旧 SNAPSHOT：旧版本线默认解析的 Tomcat 10.1.55
  含已知漏洞（1.0.0 已通过 BOM 显式受管 + Boot 属性双通道收敛到 10.1.59 修复线），
  旧 SNAPSHOT 线不受任何支持。回退演练证明的是**降级通道可用**，不是回退建议。
- 1.0.0 包含的传递依赖面治理（crud/event/security 连接池显式化，见第 4 节）在回退后
  会回到旧的"传递携带"行为——回退不是纯版本号变化，须按第 4 节重新核对池配置与启动日志。
- 发布后发现产品问题，官方路径是**新补丁版本（1.0.1+）前滚**：内部 1.0.0 制品与标签
  不可移动、不可覆盖。
