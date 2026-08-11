# FastDDD 框架消费者体验与架构审计报告

> 审计日期：2026-08-11  
> 审计范围：`fastddd-framework`、`fastddd-bom`、项目生成模板、Studio 生成链路、`FASTDDD_REFERENCE.md`、示例与测试。  
> 审计方式：主审计 + 独立对抗性代码审计；本报告仅记录问题与建议，不包含本轮代码改动。

## 1. 执行摘要

### 1.1 结论

FastDDD 当前具备较多基础能力，但框架的消费者接入体验仍存在结构性问题：**业务系统必须记住大量隐式前置条件、覆盖规则和例外行为，才能安全地使用框架。**

`FASTDDD_REFERENCE.md` 目前不仅是 API 参考，更接近业务项目的“接入补丁说明书”。如果没有这份文档，调用者很容易在配置前缀、ID 策略、安全链、事件投递、响应包装、可选依赖和生成器输出上遇到问题；即使阅读文档，部分模板和运行时代码仍与文档不一致。

根因不是“文档不够长”，而是下列设计问题：

1. starter 引入后会隐式接管大量全局行为；
2. 可选基础设施缺失时容易静默失效、静默降级或在运行期才失败；
3. BOM、模板、Studio、文档和运行时代码存在多个版本与配置事实来源；
4. 测试主要验证框架内部对象，缺少真实业务消费者的启动、依赖与 HTTP 契约测试。

### 1.2 架构目标

后续演进应遵循四项原则：

1. **最小惊讶**：引入依赖不应在未声明的情况下改变全部业务 API、安全链或线程池。
2. **显式启用**：安全认证、响应包装、OpenAPI、Outbox、P6Spy 等跨切面能力必须可独立开关。
3. **失败即说明**：关键依赖或 SPI 缺失时，在启动期给出可执行的失败原因，而不是空结果、积压数据或运行期异常。
4. **单一事实来源**：版本、配置、模板和文档应由同一份可验证契约驱动。

## 2. P0：应在 1.0 GA 前修复的问题

### P0-1：生成模板配置前缀错误，关键配置被静默忽略

**证据**

- 参考手册明确要求配置前缀为 `fastddd:`：[`FASTDDD_REFERENCE.md`](FASTDDD_REFERENCE.md#3-yaml-配置)。
- 单项目模板和多端模板仍输出 `fast-ddd:`：
  - [`application.yml.ftl`](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/application.yml.ftl)
  - [`sub-app-application.yml.ftl`](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/multi-channel/sub-app-application.yml.ftl)
- 实际配置绑定前缀均为 `fastddd.*`：
  - [`JwtProperties`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/jwt/JwtProperties.java)
  - [`CacheProperties`](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/CacheProperties.java)
  - [`SearchProperties`](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/SearchProperties.java)
  - [`SqlMonitoringProperties`](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringProperties.java)

**影响**

- JWT、缓存、搜索、监控等生成配置不会绑定，且通常没有清晰报错。
- 模板中的固定 JWT secret 会在前缀修正后变成安全问题或因密钥校验失败导致启动失败。
- 业务团队会误判为“框架配置不生效”，需要靠源码排查。

**修复方案**

1. 所有模板统一输出 `fastddd:`。
2. 生成项目一律使用 `${JWT_SECRET}`，不生成默认或固定密钥。
3. 在一个发布周期内提供 `fast-ddd.*` 到 `fastddd.*` 的兼容迁移器，并在启动日志中打印弃用告警。
4. 增加“生成后真实启动”测试：断言 JWT、缓存、搜索、监控属性都已绑定，而非只检查 YAML 字符串。

**验收标准**

- 单项目和多端项目的实际 `Environment` 中能读取 `fastddd.security.jwt.*` 等属性。
- 旧前缀出现时有明确迁移提示；兼容期结束后有明确失败提示。

### P0-2：Outbox 在非 RocketMQ 场景只入库、不投递

**证据**

- 存在 `OutboxEventRepository` 时，自动配置选择 `TransactionalEventPublisher`：[`EventAutoConfiguration`](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/EventAutoConfiguration.java)。
- 发布器只将事件写入 `outbox_events`：[`TransactionalEventPublisher`](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/outbox/TransactionalEventPublisher.java)。
- 定时投递只存在于可选的 RocketMQ 实现：[`RocketMQPublisher`](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/rocketmq/RocketMQPublisher.java)。
- 参考手册却写为“后台定时器负责实际投递”。

**影响**

业务会以为已获得可靠事件投递，实际数据长期停留在 `PENDING`，形成隐蔽的一致性故障。

**修复方案**

1. 明确拆分 `fast-ddd-event-core` 与 `fast-ddd-event-outbox`。
2. 引入 `OutboxTransport` 与 `OutboxDispatcher` SPI；只有显式启用 `fastddd.event.outbox.enabled=true` 时才切换到 Outbox 模式。
3. Outbox 已启用但没有可用 dispatcher/transport 时，启动失败并给出修复指引。
4. 默认 dispatcher 必须具备原子 claim/lease、重试、死信、积压指标与多实例竞争保护。

**验收标准**

- 本地事件、Outbox 事件、RocketMQ 事件具有明确且可观察的工作模式。
- Outbox 事件在没有 transport 时不能静默积压。
- 使用 Testcontainers 覆盖两个应用实例抢占、重试、死信和恢复场景。

### P0-3：关闭 JWT 开关后仍可能要求 JWT Secret

**证据**

- HTTP Security 配置受 `fastddd.security.jwt.enabled` 控制：[`SecurityAutoConfiguration`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/config/SecurityAutoConfiguration.java)。
- 外层自动配置仍可创建 HMAC signer：[`FastDddSecurityAutoConfiguration`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/FastDddSecurityAutoConfiguration.java)。
- 默认 HMAC signer 会校验 secret：[`HmacJwtSigner`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/jwt/HmacJwtSigner.java)。

**影响**

业务项目按配置关闭 JWT、且不设置 secret 时，仍可能启动失败；开关语义不完整。

**修复方案**

1. 将 JWT signer、provider、filter、认证控制器与安全链拆成独立自动配置。
2. 所有 JWT Bean 均受同一个 `enabled` 条件控制。
3. 分别提供 `resource-server`、`auth-server`、`disabled` 模式，避免仅用一个布尔开关承载所有安全语义。

**验收标准**

- `enabled=false` 且无 secret 的消费者应用可以启动。
- `enabled=true` 且 HMAC 模式缺 secret 时启动失败，并指明配置路径。

### P0-4：安全配置、模板和文档不能作为同一契约使用

**证据**

- 参考手册要求精确放行认证路径，模板仍可能生成 `/api/auth/**`。
- 当前 JWT 已支持 access/refresh 类型隔离、刷新轮换和撤销，但参考手册仍将 logout 描述为“仅清除 SecurityContext”。
- 生成器和模板中存在多个 FastDDD、Spring Boot、SpringDoc、Knife4j 版本默认值。

**影响**

调用者即使严格按手册接入，也可能得到与当前运行时代码不一致的安全语义和配置结果。

**修复方案**

1. 把配置说明、默认值、模板变量、兼容矩阵统一由机器可读 manifest 生成。
2. 将 JWT 令牌类型、撤销存储、集群实现要求和 Actuator 规则纳入参考手册。
3. 在 CI 中增加“文档示例可编译/可启动”测试。

## 3. P1：高概率增加接入成本或造成生产偏差的问题

### P1-1：基础 starter 传递依赖过重

执行 `mvn dependency:tree -pl fastddd-framework/fast-ddd-starter` 已确认，基础 starter 会传递带入：

- `mybatis-plus-generator`；
- `druid-spring-boot-3-starter`；
- `springdoc-openapi-starter-webmvc-ui`；
- `knife4j-openapi3-jakarta-spring-boot-starter`。

相关 POM：[`fast-ddd-crud`](../fastddd-framework/fast-ddd-crud/pom.xml)、[`fast-ddd-web`](../fastddd-framework/fast-ddd-web/pom.xml)、[`fast-ddd-starter`](../fastddd-framework/fast-ddd-starter/pom.xml)。

**影响**

- 一个基础 CRUD 项目被迫引入连接池、代码生成、API 文档 UI 等非核心运行时能力。
- 业务已有 Hikari、OpenAPI、Spring Security 或数据库驱动选择时，依赖冲突和升级成本明显上升。

**修复方案**

- 数据建模/CRUD 与 Druid、Generator、数据库驱动拆分。
- Web API 与 OpenAPI/Knife4j 拆分。
- 将 `fast-ddd-starter` 定义为真正的最小核心，或保留旧全家桶 starter 并标记为兼容入口。

### P1-2：Web 自动配置无条件改变业务 API 契约

**证据**

- Web 自动配置无条件导入响应包装配置：[`FastDddWebAutoConfiguration`](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/FastDddWebAutoConfiguration.java)。
- `SmartResponseAdvice` 会包装符合条件的业务响应：[`SmartResponseAdvice`](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/advice/SmartResponseAdvice.java)。
- 手册却要求业务方显式加 `@EnableApiResponseWrapper`。

**影响**

仅引入 starter 即可能把已有业务接口的 JSON 返回结构改为 `ApiResponse`，造成前端、第三方回调、OpenAPI 契约不兼容。

**修复方案**

- 响应包装改为显式 opt-in；可由 `fastddd.web.response-envelope.enabled` 控制。
- 全局异常处理、Jackson 格式、OpenAPI 同样独立开关。
- 在管理后台 starter 中可默认开启，在通用 web starter 中默认关闭。

### P1-3：SecurityFilterChain、PasswordEncoder 和审计用户提供者缺少让路机制

**证据**

- `PasswordEncoder` 不对用户 Bean 退让：[`SecurityAutoConfiguration`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/config/SecurityAutoConfiguration.java)。
- 默认 `SecurityFilterChain` 也没有对业务安全链退让。
- 安全 starter 无条件注册 `@Primary SecurityCurrentUserProvider`：[`FastDddSecurityAutoConfiguration`](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/FastDddSecurityAutoConfiguration.java)。

**影响**

已有 Argon2、自定义认证链、多租户审计字段或多安全域的业务应用，可能遇到 Bean 冲突、链路抢占或审计字段语义错误。

**修复方案**

1. 所有默认 Bean 增加精确的 `@ConditionalOnMissingBean`。
2. 默认安全链仅在业务没有 `SecurityFilterChain` 时创建。
3. 提供 `FastDddSecurityCustomizer`，允许业务在同一链中扩展，而非只能排除整个自动配置。
4. `SecurityCurrentUserProvider` 仅在没有业务实现时注册，并优先返回 JWT 中的 `userId`。

### P1-4：搜索模块的“单依赖、SQL 零配置”承诺不成立

**证据**

- `fast-ddd-search` 把 CRUD/Model 标记为 optional。
- SQL `SearchService` 仅在 MyBatis `BaseMapper` 存在时创建：[`FastDddSearchAutoConfiguration`](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/FastDddSearchAutoConfiguration.java)。
- Mapper 无法解析时，搜索会返回空结果而不是配置错误：[`SqlSearchService`](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/sql/SqlSearchService.java)。

**影响**

按文档只引入搜索模块时，可能没有 `SearchService` Bean；Mapper 配置错误时又可能被误判为“确实没有搜索结果”。

**修复方案**

- 拆分 `fast-ddd-search-sql` 与 `fast-ddd-search-meilisearch`。
- SQL 模式下明确传递所需依赖，或在条件不满足时输出 Failure Analysis。
- Mapper/索引映射不完整时抛出可诊断异常，而不是返回空集合。

### P1-5：SQL 监控的运行时链路不闭环

**证据**

- P6Spy 通过类名反射创建 formatter：[`spy.properties`](../fastddd-framework/fast-ddd-monitor/src/main/resources/spy.properties)。
- formatter 依赖 Spring 注入属性与 MeterRegistry：[`SqlMonitoringMessageFormatter`](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringMessageFormatter.java)。
- “Actuator controller”只是普通 MVC Controller，并不是真正的 `@Endpoint`：[`SqlMonitoringController`](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringController.java)。
- 现有监控测试主要直接 new 对象，未启动 P6Spy/DataSource/Actuator 链路。

**影响**

监控看似启用，但指标、脱敏、动态配置和 Actuator 暴露不一定真实生效；随 starter 打包的 `spy.properties` 还可能影响业务自身的 P6Spy 配置。

**修复方案**

1. 使用 Spring 管理的 DataSource proxy，或提供可靠的 P6Spy ↔ Spring bridge。
2. 使用真正的 Actuator `@Endpoint`，不伪装为 `/actuator/**` MVC 路由。
3. 不在 starter JAR 中放置全局 `spy.properties`；由业务项目显式配置。
4. 默认关闭 SQL 参数日志，提供字段脱敏策略和真实链路集成测试。

### P1-6：生成器默认输出 deprecated 且高风险的 CRUD 模式

**证据**

- `BaseController` 已废弃：[`BaseController`](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/controller/BaseController.java)。
- 贫血模型模板仍生成该基类：[`Controller.ftl`](../fastddd-tooling/fastddd-templates/src/main/resources/templates/anemic/Controller.ftl)。
- 通用 CRUD 接口直接使用 `BasePO` 作为请求体：[`BaseAdminCrudController`](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/controller/BaseAdminCrudController.java)。

**影响**

生成 API 易暴露 `id`、审计字段、逻辑删除字段等持久化字段；细粒度权限和领域约束依赖调用者自行覆盖实现。

**修复方案**

- 禁止生成 deprecated 基类。
- 默认生成 `BaseReadOnlyController` 或显式 DTO + Command 的写接口。
- 后台 CRUD 模板生成类级权限、写操作权限、审计和数据权限扩展提示。
- 对敏感领域不提供空实现的写 CRUD 端点。

### P1-7：ID 策略与乐观锁规则不一致

**证据**

- `BasePO` 显式声明 `IdType.ASSIGN_ID`：[`BasePO`](../fastddd-framework/fast-ddd-model/src/main/java/com/gallop/fastddd/model/po/BasePO.java)。
- 模板生成 `id-type: auto`。
- 手册将其描述为可由 MyBatis 全局配置控制。

**影响**

调用者难以预测最终主键生成策略；模板、表结构和运行时结果可能不一致。

**修复方案**

- 明确“框架默认雪花 ID”与“数据库自增 ID”两套策略，不混用显式注解和全局覆盖描述。
- 根据表元数据生成 `BasePO` 或 `VersionedPO`，存在 version 列时才生成乐观锁实体。

### P1-8：BOM、模板和 Studio 的版本治理脱节

**证据**

- BOM 负责 FastDDD 与推荐第三方版本：[`fastddd-bom/pom.xml`](../fastddd-bom/pom.xml)。
- 单项目和多端模板仍直接声明 FastDDD、SpringDoc、Knife4j 版本：
  - [`pom.xml.ftl`](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/pom.xml.ftl)
  - [`parent-pom.xml.ftl`](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/multi-channel/parent-pom.xml.ftl)

**影响**

升级需要同时改 BOM、模板、Studio 默认值和文档；遗漏会生成版本错配项目。

**修复方案**

- 所有模板只 import `fastddd-bom`，不重复管理 BOM 已覆盖的版本。
- 维护单一版本 manifest，驱动根 POM、BOM、模板、Studio、文档和兼容矩阵。
- CI 断言生成项目的 dependency tree 与 BOM 一致。

## 4. P2：架构与质量债务

### 4.1 自动配置过度使用全局副作用

- 事件模块无条件启用 `@EnableAsync`、`@EnableScheduling`，并占用全局 `taskExecutor`：[`EventAutoConfiguration`](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/EventAutoConfiguration.java)。
- 缓存模块自动启用 Spring Cache，且自定义 `CacheManager` 未对业务已有 `CacheManager` 明确退让：[`FastDddCacheAutoConfiguration`](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/FastDddCacheAutoConfiguration.java)。
- 多个模块使用组件扫描将框架实现直接加入业务容器，增加覆盖和重复注册的不可预期性。

**建议**：框架使用专用 executor、显式 bean 名称、条件化自动配置和稳定 SPI；避免使用宽泛的组件扫描实现核心装配。

### 4.2 可选依赖和默认值缺少生产级边界

- 验证码默认内存存储和 Noop 限流适合开发，但需要更强的生产环境 fail-fast 策略。
- CORS、Swagger、H2、WebSocket、监控日志等能力应采用最小默认暴露面。
- SQL 参数、业务返回、异常堆栈和监控配置均应默认按敏感数据处理。

### 4.3 消费者契约测试不足

目前部分模块只有单元测试，或测试直接构造对象，无法覆盖真实自动配置、依赖冲突、HTTP 契约和运行时基础设施。

特别是以下消费者场景必须建立回归矩阵：

| 场景 | 必须验证的结果 |
|---|---|
| 最小 Web 项目 | 不改变响应格式，除非显式启用包装器 |
| 已有 SecurityFilterChain | 不出现 Bean 冲突，框架能力可插入同一安全链 |
| JWT 关闭 | 无 secret 时仍可启动 |
| Redis / 非 Redis | 缓存行为、CacheManager 选择和降级信息明确 |
| SQL / Meilisearch | 必需 Bean、索引、Mapper 缺失时可诊断 |
| Outbox 多实例 | claim、重试、死信、积压指标正确 |
| SQL 监控 | 真实 SQL → metrics → Prometheus/Endpoint 链路可用 |
| 单项目/多端生成 | 生成后 Maven 编译、启动、配置绑定和健康检查均通过 |
| 升级兼容 | BOM、模板、Studio 与 dependency tree 对齐 |

## 5. 推荐的模块演进模型

建议将当前“全家桶”能力拆为可组合 starter：

```text
fast-ddd-core
├── fast-ddd-mybatis-starter
├── fast-ddd-web-starter
│   ├── fast-ddd-response-envelope-starter
│   └── fast-ddd-openapi-starter
├── fast-ddd-security-resource-server
├── fast-ddd-security-auth-server
├── fast-ddd-event-core
│   └── fast-ddd-event-outbox
├── fast-ddd-cache-starter
├── fast-ddd-search-sql
├── fast-ddd-search-meilisearch
└── fast-ddd-monitor-p6spy
```

设计要求：

- `core` 不应带入数据库驱动、连接池、文档 UI、消息中间件或监控 Agent。
- Auth Server 控制器不应随着 Resource Server 自动出现。
- OpenAPI、Knife4j、响应包装、SQL 监控、Outbox 均必须单独 opt-in。
- 为原 `fast-ddd-starter` 提供兼容层和迁移说明；在 Snapshot 阶段优先完成边界收敛，再发布 1.0 GA。

## 6. 分期改造建议

### 阶段 A：发布前可靠性基线

目标：消除静默失效和直接错误配置。

1. 统一模板前缀为 `fastddd`，移除固定 JWT secret。
2. 修复 JWT 关闭语义，补齐 Security/Actuator/JWT 文档。
3. Outbox 无 dispatcher 时 fail-fast。
4. 统一 BOM、模板、Studio 默认版本。
5. 生成项目执行真实启动测试。
6. 禁止模板生成 deprecated CRUD 基类。

### 阶段 B：自动配置可组合化

目标：业务应用可在已有基础设施上平滑接入。

1. SecurityFilterChain、PasswordEncoder、CurrentUserProvider 完整退让。
2. 响应包装、异常处理、OpenAPI、缓存、监控改为独立开关。
3. 事件使用专用 executor，不接管业务 `taskExecutor`。
4. 拆分过重的 starter 和非必要传递依赖。

### 阶段 C：可观测性与消费者质量门禁

目标：框架发布前由自动化证明“业务项目能用”。

1. 建立独立消费者示例工程，而不只使用框架内部测试包。
2. 使用 ApplicationContextRunner、MockMvc、Testcontainers 和真实 Maven 生成项目覆盖契约矩阵。
3. 在 CI 中校验配置元数据、文档片段、模板产物和 dependency tree。
4. 发布前运行二进制兼容性与 BOM 依赖对齐检查。

## 7. 文档治理建议

`FASTDDD_REFERENCE.md` 应从“必须背诵的避坑手册”回归为 API/设计参考：

1. 保留概念、模块选择、SPI、示例和兼容矩阵。
2. 将危险配置转换为启动期校验，不要求调用者记忆。
3. 从 `spring-configuration-metadata.json` 和版本 manifest 自动生成配置表与默认值。
4. 对每段可复制配置提供最小可运行样例及 CI 校验。
5. 单独维护迁移指南，避免新用户在参考文档中混入历史兼容细节。

## 8. 最终判断

FastDDD 下一阶段最重要的工作不是继续增加能力，而是让已有能力具备**清晰边界、可预测默认值、显式开关、可诊断失败和可验证接入方式**。

当业务方只需按模块选择依赖、提供必要配置与 SPI，就能启动并得到符合预期的行为时，`FASTDDD_REFERENCE.md` 才真正是参考手册，而不是业务项目不可缺少的“防坑补丁”。
