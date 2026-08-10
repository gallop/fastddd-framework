# 升级指南（Upgrade Guide）

> 本指南指导业务系统从旧基线（JDK 17 / Spring Boot 3.4.5 / FastDDD `1.0.0-SNAPSHOT`）升级到
> **JDK 21 / Spring Boot 3.5.16 / FastDDD `1.0.0-jdk21-SNAPSHOT`**。
>
> 对应升级方案 UPG-011（在研业务系统联调）。兼容线见 [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md)；
> 发布历史见 [../CHANGELOG.md](../CHANGELOG.md)。

---

## 0. 升级前评估

| 项 | 旧基线 | 新基线 |
|----|--------|--------|
| Java | 17 | **21** |
| Spring Boot | 3.4.5 | **3.5.16** |
| FastDDD | `1.0.0-SNAPSHOT` | **`1.0.0-jdk21-SNAPSHOT`**（GA 后为 `1.0.0`） |
| MySQL 驱动坐标 | `mysql:mysql-connector-java` | **`com.mysql:mysql-connector-j`** 9.7.0 |
| Druid 坐标 | `druid-spring-boot-starter` | **`druid-spring-boot-3-starter`** |

**本次升级为 minor 线内升级**（Java 主版本 17→21 属构建/运行基线抬升，非框架 API 破坏）。
FastDDD 公开 API 在 1.0.x 线保持向后兼容，业务代码的框架调用基本无需改动；
主要工作在**环境（JDK 21）+ 依赖坐标（BOM）+ 配置（重复键等 Boot 3.5 严格解析）**。

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
        <version>1.0.0-jdk21-SNAPSHOT</version>   <!-- GA 后改 1.0.0 -->
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
./mvnw -B clean install -DskipTests   # 安装含 fastddd-bom 在内的全部模块
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

---

## 5. 构建与回归

```bash
# 1. 全量编译
./mvnw -B clean compile

# 2. 全量测试
./mvnw -B test

# 3. 打包
./mvnw -B clean package -DskipTests
```

### 回归清单

- [ ] 应用正常启动（日志含 `Spring Boot v3.5.16`、Druid `{dataSource-1} inited`）。
- [ ] 数据库 CRUD 正常（MySQL 驱动 9.7.0 + Druid 3-starter）。
- [ ] API 文档端点可达（SpringDoc 2.8.17 / Knife4j 4.5.0：`/v3/api-docs`、`/doc.html`）。
- [ ] 安全认证（JWT / 验证码）链路正常（fast-ddd-security-starter）。
- [ ] 监控指标（`/actuator/prometheus`，Micrometer 1.15.12）正常输出。
- [ ] 代码生成插件可用：`./mvnw com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-SNAPSHOT:help`。
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

**Q: 是否需要改业务代码里的框架 API 调用？**
A: 1.0.x 线保持 API 向后兼容，一般无需改。若用到 Spring 6.2 弃用 API（如 `@EventListener` 的
`condition()`/`id()`），属 Spring 框架层面提示，1.0.x 仍可用，Spring 7 迁移时再处理。

---

## 7. 回退

升级以**单分支**进行，出问题可回退到升级前分支/tag。FastDDD 源码侧不使用 `git reset --hard`，
单文件用 `git restore <file>` 回退；业务系统侧按各自版本控制策略回退依赖版本。
