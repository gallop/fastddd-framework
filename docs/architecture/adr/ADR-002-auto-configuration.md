# ADR-002：自动配置契约（每模块模式枚举 + 类存在性门禁 + 失败分析器 + back-off）

- **状态**：已冻结（FRM-001 架构 review）
- **日期**：2026-08-11
- **关联**：审计 P0-2/P0-4/P0-5/P0-7、P1-2/P1-3/P1-8；整改方案 §7 CFG-001～CFG-NNN
- **决策依据**：审计"无统一加载门禁/无失败诊断/无 back-off"逐项核验

---

## 1. 背景（现状，代码级证据）

框架共 **17 个 `@AutoConfiguration` 类**（另有若干 `@Configuration` 配置类，`grep -l @AutoConfiguration` 实证），但缺乏统一的加载治理：

- **无统一关闭语义（结构问题，承重）**：只有 `cache`、`monitor` 把开关放在最外层配置类（`FastDddCacheAutoConfiguration`、`FastDddMonitorAutoConfiguration` 用 `@ConditionalOnProperty` 在 class 级别 gate）。`security`（`FastDddSecurityAutoConfiguration.java:68` 无 class 级门禁）与 `event`（`EventAutoConfiguration` 无 class 级门禁）即便业务设 `enabled=false`，内部 bean（如 `jwtSigner` `FastDddSecurityAutoConfiguration.java:78-83`、`rocketMQPublisher`）仍被构造，且因依赖未满足直接**启动失败**而非优雅降级。审计 P0-7。
- **无 FailureAnalyzer**：全仓 grep `FailureAnalyzer` 零命中。典型失败路径是 `BeanCreationException` 堆栈，业务方无法定位"是哪个模块因缺哪个依赖被禁用"。审计 P0-4。
- **无 @ConditionalOnMissingBean back-off（关键模块）**：框架 bean 与业务 bean 重名时直接冲突而非框架退让。审计 P1-3。自定义条件注解（`@ConditionalOnDev` 等，`fast-ddd-config`）存在但**未被任何自动配置使用**。审计 P1-2。
- **类存在性门禁不一致**：部分模块用 `@ConditionalOnClass`（如 MyBatis Plus 相关），部分仅靠 `@ConditionalOnProperty`，缺依赖时不会按"模块自动禁用"语义降级。审计 P0-2/P0-5。
- **`@ConditionalOnClass` 名称碰撞隐患**：字符串类名条件在不同模块间可能引用同名占位类。

## 2. 决策

**2.1 每模块引入模式枚举（`FastDddModuleMode`：`ENABLED`/`DISABLED`/`AUTO`）**

- `ENABLED`：强制启用，缺依赖即启动失败（fail-fast，配 FailureAnalyzer 给出可读原因）。
- `DISABLED`：完全不装配该模块的任何 bean（class 级 `@ConditionalOnProperty`，最外层 gate）。
- `AUTO`（默认）：`@ConditionalOnClass(核心依赖类)` + `@ConditionalOnProperty(matchIfMissing=true)`，缺依赖则**静默跳过**并记 INFO 日志。

每个模块对外暴露 `fastddd.<module>.mode`（默认 `AUTO`）。`enabled` 布尔作为兼容别名（`true→AUTO` 行为、`false→DISABLED`），1.0.x 保留、1.1 弃用。

**2.2 类存在性门禁统一在最外层**

每个自动配置类的 class 级首条件为该模块的核心依赖类（`@ConditionalOnClass(name=...)`），确保"缺依赖 = 模块不装配"，杜绝"缺依赖却构造内部 bean 而崩溃"。security 模块的 `jwtSigner`/`JwtTokenProvider` 必须受 `mode != DISABLED` 与 `@ConditionalOnClass(JWT 类)` 双重门禁（与 ADR-004 联动，关闭 P0-7）。

**2.3 每模块一个 FailureAnalyzer**

为 security/event/search/cache/monitor/crud 各注册一个 `FailureAnalyzer`，把 `BeanCreationException` 翻译为"模块 X 因依赖 Y 缺失/配置 Z 非法而未启动，处理方式：①补依赖 ②设 fastddd.X.mode=DISABLED ③……"。放在 `META-INF/spring.factories`（Boot 3 仍支持）/ `META-INF/spring/org.springframework.boot.diagnostics.FailureAnalyzer.imports`。

**2.4 @ConditionalOnMissingBean back-off**

所有框架自动配置提供的公共 bean（FilterChain、EventPublisher、CacheManager 装饰器等）加 `@ConditionalOnMissingBean`，业务定义同名 bean 时框架退让并记 INFO。统一处理审计 P1-3。

## 3. 替代方案

- **备选 B（仅补 @ConditionalOnClass，不引入模式枚举）**：改动小，但 `ENABLED`（强制 fail-fast）和 `DISABLED`（彻底不装配）无法表达，业务只能靠 AUTO 的隐式行为，调试体验差（回退到当前 P0-4 痛点）。不采用。
- **备选 C（Spring Boot 原生 `@AutoConfiguration` + `@ConditionalOnClass` 不额外封装）**：最薄封装，但无法承载框架统一的"三语义"文档与 FailureAnalyzer 体系，且 21 个配置类分散各自实现易再次漂移。不作为主路径，但 `@AutoConfiguration` 注解本身仍使用。

## 4. 后果与迁移

- **公共 API 新增**：`FastDddModuleMode` 枚举、`fastddd.<module>.mode` 属性、5 个 FailureAnalyzer（公共诊断契约）。
- **配置兼容**：现有 `enabled` 布尔属性保留为别名，业务零改动即升级；想用 fail-fast 显式设 `mode=ENABLED`。
- **行为变化**：security/event 模块在 `mode=DISABLED` 时真正不装配（修复 P0-7 的"关不掉"）。这是 bugfix 性质的行为修正，非破坏性。
- **测试影响**：现有集成测试需确认各模块默认 AUTO 行为下不再出现"缺依赖却崩"。

## 5. 1.0 GA 范围 vs 延后

- **1.0 GA**：`FastDddModuleMode` 枚举、`fastddd.<module>.mode` 属性 + `enabled` 别名、全部自动配置类的 class 级门禁统一、5 个核心模块 FailureAnalyzer、公共 bean 的 `@ConditionalOnMissingBean` back-off。
- **延后（1.1+）**：`enabled` 别名正式弃用、未启用的条件注解（`@ConditionalOnDev` 等）接入自动配置或移除。

## 6. 对内部 RC / 公开 GA 的约束

- **RC**：全部自动配置类逐一回归 ENABLED/DISABLED/AUTO 三态；FailureAnalyzer 在人为删除依赖时输出正确诊断。
- **GA**：`fastddd.<module>.mode` 属性、`FastDddModuleMode` 枚举、FailureAnalyzer 诊断文案为公共契约，1.0.x 内不得破坏性变更。

## 7. 参考

- 研究证据：审计 P0-2/P0-4/P0-5/P0-7（行 230-285）、P1-2/P1-3/P1-8（行 306-345）
- 关联 ADR：ADR-004（security 模块的 jwtSigner 门禁与本 ADR 联动关闭 P0-7）
