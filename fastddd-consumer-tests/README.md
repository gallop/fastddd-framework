# FastDDD 独立消费者测试骨架（FRM-002）

用 [maven-invoker-plugin](https://maven.apache.org/plugins/maven-invoker-plugin/) 在**隔离的 forked Maven 进程**里构建最小消费者工程，验证 `fast-ddd-*` 模块作为**外部依赖**被消费时的真实边界——而非作为 reactor 子模块被消费（后者会让 reactor classpath 掩盖 optional 依赖问题，审计 P0-2）。

> **隔离原理**：每个 `src/it/<scenario>/` 是一个独立的 Maven 工程（parent = `spring-boot-starter-parent`，仅 import `fastddd-bom`）。invoker 把它复制到 `target/its/<scenario>/` 后 fork 一个新的 Maven 调用执行 `clean test`；forked 构建从**本地仓库**解析 `fast-ddd-*`（即 `mvn install` 装入的制品），不继承 reactor 的 `target/classes`。这正是真实外部消费者的解析路径。

## 运行方式（两步，CI）

```bash
# JDK 21 硬前置
export JAVA_HOME="D:/usr/local/jdk/jdk-21.0.3"; export PATH="$JAVA_HOME/bin:$PATH"

# 1. 安装框架制品到本地仓库（消费者解析来源）
./mvnw -B install -DskipTests

# 2. 跑 gated 场景（默认 web-*）
./mvnw -B verify -pl fastddd-consumer-tests -Pconsumer-tests

# 可选：跑全部 7 场景（含 5 个 ungated scaffold，含已知失败）
./mvnw -B verify -pl fastddd-consumer-tests -Pconsumer-tests -Dinvoker.test='*'
```

**默认跳过**：consumer-tests 模块的 invoker 绑定 `<invoker.skip>true</invoker.skip>`，需 `-Pconsumer-tests` 启用。标准 `./mvnw clean verify -Ptest`（全量门禁）**不触发**消费者测试——日志实证 `invoker:3.9.0:run (consumer-integration-test) ... Skipping invocation per configuration`，该模块仅贡献 0 个 surefire 单测（forked 工程在 `src/it/` 下，不在模块自身测试 classpath）。全量门禁保持 BUILD SUCCESS、0 回归（基线见整改方案 §16.1.B：364 run / 0 failures）。

**诊断日志**：每个场景的完整输出保留在 `target/its/<scenario>/build.log`（验收③）。失败时 `streamLogsOnFailures=true` 同时回显关键日志。

## 场景清单

| 场景 | 模块 | gated | 期望 | 实测（2026-08-11） | 跟踪任务 |
|---|---|---|---|---|---|
| `web-minimal` | fast-ddd-starter + application + H2 | ✅ | 启动 | ✅ 启动（验收①） | — |
| `web-without-application` | fast-ddd-starter（无 application） | ✅ | 复现 P0-2 | ✅ 复现（验收②） | WEB-001 |
| `security-minimal` | fast-ddd-security-starter | ❌ | scaffold | ✅ 启动（给 ≥64B secret） | SEC-001 |
| `cache-minimal` | fast-ddd-cache | ❌ | scaffold | ❌ Redis 类泄漏 | CACHE-001 |
| `event-minimal` | fast-ddd-event | ❌ | scaffold | ❌ 缺 ObjectMapper bean | EVT（ADR-003） |
| `monitor-minimal` | fast-ddd-monitor | ❌ | scaffold | ❌ P0-4 复现 | MON-001 |
| `search-minimal` | fast-ddd-search | ❌ | scaffold | ✅ 启动（无 ES 不崩） | SEARCH-001 |

**默认 `invoker.test=web-*`** → 仅 2 个 gated 场景，CI 绿。`-Dinvoker.test=*` → 全部 7 个。

### ungated 失败的实测根因（build.log 证据，2026-08-11 复跑）

- **cache-minimal**：`Caused by: java.lang.NoClassDefFoundError: org/springframework/data/redis/connection/MessageListener`。cache 硬引用 Spring Data Redis 类但未 `@ConditionalOnClass` 门禁——与 P0-2 同类的 optional 边界泄漏。归 CACHE-001 / ADR-002 修。
- **event-minimal**：`UnsatisfiedDependencyException` 创建 bean `outboxEventServiceImpl`：构造器参数 1 `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'`。`OutboxEventServiceImpl` 假设 ObjectMapper 存在（通常由 web starter 提供），minimal 消费者未提供。归 ADR-002 自动配置门禁 / EVT（ADR-003）。
- **monitor-minimal**：`UnsatisfiedDependencyException` 创建 bean `sqlMetricsCollector`（`SqlMonitoringAutoConfiguration`）：方法参数 1 `No qualifying bean of type '...SqlMonitoringProperties' ... expected single matching bean but found 2: sqlMonitoringProperties, fastddd.monitor.sql-com.gallop.fastddd.monitor.sql.SqlMonitoringProperties`。**审计 P0-4 的直接复现**——`@ConfigurationProperties` bean 重复注册。归 MON-001。

### gated 对的实测证据（build.log，2026-08-11 复跑）

- **web-minimal**：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`（`ContextLoadsTest`）——上下文在独立 classpath 成功启动（验收①）。
- **web-without-application**：`Tests run: 1, Failures: 0`（`ReproducesP02Test`，特征测试**通过**=缺陷存在）；build.log 因果链：`BeanCreationException: Error creating bean with name 'globalExceptionHandler'` → `NoClassDefFoundError: com/gallop/fastddd/application/exception/CommandValidationException` → `ClassNotFoundException: com.gallop.fastddd.application.exception.CommandValidationException`（验收②，P0-2 精确复现）。

> **关于 `-Pconsumer-tests` 警告**：从 reactor 根调用 `mvn ... -Pconsumer-tests` 时 Maven 会打印 `The requested profile "consumer-tests" could not be activated because it does not exist`——这是因为 profile 定义在子模块 `fastddd-consumer-tests/pom.xml` 而非根 pom。警告是**装饰性**的：profile 对该子模块**实际生效**（证据：默认 gated 跑出 web-* 2 场景、`-Dinvoker.test=*` 跑出全 7 场景；若 profile 未生效，invoker 会被 `<invoker.skip>true</invoker.skip>` 跳过，跑出 0 场景，与实测相悖）。

## 特征测试约定（characterization）

`web-without-application` 用**特征测试**而非 `invoker.buildResult=failure`：当 P0-2 缺陷存在时测试**通过**（断言上下文抛 `CommandValidationException`）。当 WEB-001 修复该缺陷（上下文可启动）后，`assertThrows` 因"未抛异常"而失败——这是**翻转信号**，提示把该场景改为正向 `contextLoads`。同样的约定适用于未来对 cache/event/monitor 失败的刻画。

## 验收对照（FRM-002）

- ✅ 至少一个最小消费者能在独立 classpath 编译和启动 → `web-minimal`。
- ✅ 能复现 Web 缺 application 的当前失败 → `web-without-application`（P0-2，build.log 证据）。
- ✅ 测试失败时保留可诊断日志 → `target/its/<scenario>/build.log`。
- ✅ 预置 Web/Security/Cache/Event/Monitor/Search 六类空消费者 → 7 场景（web ×2 + 5 scaffold）。
- ✅ 公共构建命令接入 CI，暂不强制所有场景通过 → 默认 `web-*` gated 实测 **Passed: 2, Failed: 0**（BUILD SUCCESS）；全量 `-Dinvoker.test=*` 实测 **Passed: 4, Failed: 3**，3 个 ungated 失败已登记到对应任务（CACHE-001 / EVT / MON-001）。
