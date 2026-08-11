# FastDDD 框架消费者体验与架构审计报告

> 审计日期：2026-08-11
>
> 审计基线：HEAD 740da39，复核时工作树干净
>
> 审计范围：fastddd-framework、fastddd-bom、项目生成模板、Studio 生成链路、FASTDDD_REFERENCE.md、示例与测试
>
> 审计方式：主审计、独立对抗性代码审计、依赖树核对、消费者上下文启动和关键行为复现
>
> 报告性质：问题、修复方案与任务规划；本轮不包含框架代码修改
>
> 执行方案：[FastDDD 1.0 基础框架架构整改实施方案](architecture/FASTDDD_1_0_FRAMEWORK_REMEDIATION_PLAN.md)

## 1. 执行摘要

### 1.1 总体结论

FastDDD 已具备较完整的 DDD、Web、安全、事件、缓存、搜索和监控能力，但在当前版本中，业务系统仍必须记住大量隐式前置条件、覆盖规则和例外行为，才能稳定接入。

[FASTDDD_REFERENCE.md](FASTDDD_REFERENCE.md) 目前不仅是 API 参考，更接近业务项目的“接入补丁说明书”。即使业务方完整阅读文档，部分模板、自动配置和运行时代码仍与文档不一致；如果不阅读该文档，配置失效、Bean 冲突、接口契约变化和基础设施误启用的概率较高。

问题的根因不是文档不够长，而是：

1. 自动配置边界过宽，模块引入后会隐式接管全局行为。
2. optional 依赖与直接类型引用并存，消费者真实 classpath 与 reactor 测试结果不同。
3. 可选基础设施缺失时存在静默降级、错误降级或运行期失败。
4. BOM、模板、Studio、文档和运行时代码存在多个配置与版本事实来源。
5. 测试偏重框架内部对象，缺少独立消费者的启动、依赖、HTTP 和基础设施契约验证。

当前确认有 7 类 P0 问题，不建议在这些问题关闭前发布 1.0 GA。

### 1.2 问题概览

| 等级 | 数量 | 结论 |
|---|---:|---|
| P0 | 7 | 会导致生成配置失效、消费者启动失败、跨缓存串值、事件可靠性失效或通用默认凭据 |
| P1 | 10 | 高概率增加接入成本、引发 Bean 冲突、改变 API 契约或造成生产配置偏差 |
| P2 | 3 类 | 自动配置治理、生产默认值和消费者测试体系债务 |

### 1.3 目标原则

后续演进应遵循：

1. 最小惊讶：引入依赖不应在未声明的情况下改变全部业务 API、安全链、序列化规则或线程池。
2. 显式启用：认证服务、响应包装、OpenAPI、Outbox、SQL 监控等跨切面能力必须独立开关。
3. 失败即说明：关键依赖或 SPI 缺失时，应在启动期给出可执行的 Failure Analysis。
4. 业务优先：框架默认 Bean 必须对业务已有实现退让。
5. 单一事实来源：版本、配置、模板和文档应由同一份可验证契约驱动。

## 2. P0：1.0 GA 前必须关闭

### P0-1：生成模板与运行时配置契约不一致

**已确认的证据**

- 单项目和多端模板输出 fast-ddd 根前缀：
  - [单项目 application.yml.ftl](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/application.yml.ftl)
  - [多端 sub-app-application.yml.ftl](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/multi-channel/sub-app-application.yml.ftl)
- 运行时属性绑定使用 fastddd 根前缀：
  - [JwtProperties](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/jwt/JwtProperties.java)
  - [CacheProperties](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/CacheProperties.java)
  - [SearchProperties](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/SearchProperties.java)
  - [SqlMonitoringProperties](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringProperties.java)
- 多端模板把 http、cors 放在 security 同级，实际属性层级要求 fastddd.security.http 与 fastddd.security.cors。
- 模板使用 slow-threshold，实际属性名是 slow-query-threshold。
- 模板中的 fastddd.event 与 fastddd.application.cqrs 当前没有对应的配置属性。
- 单项目模板会生成可预测 JWT secret，且常见 artifactId 下长度不足框架要求。

**影响**

生成项目看似配置完整，实际 JWT、缓存、搜索、监控和安全放行规则可能未绑定；部分错误会静默存在，部分会在前缀修正后转化为启动失败或安全风险。

**修复方案**

1. 以 Spring Configuration Metadata 为准，逐项修正单项目、多端项目和 Studio 输出。
2. 删除不存在的配置项；需要保留的能力先实现属性契约，再进入模板。
3. 模板只引用 JWT_SECRET，不生成默认、固定或可预测密钥。
4. 项目仍处于 SNAPSHOT 且业务系统可同步升级，不建议增加旧前缀兼容迁移器。
5. 增加旧 fast-ddd 前缀检测，出现时启动失败并给出新路径。
6. 增加生成后真实启动测试，不再只检查 YAML 字符串。

**验收标准**

- 单项目和多端项目的实际 Environment 能读取全部生成属性。
- 生成结果不包含 fast-ddd 根、未知属性和固定 secret。
- 四种组合均通过 Maven 编译和 Spring 上下文启动：单端/多端、贫血/充血。

### P0-2：Web 模块的 optional 依赖会导致消费者启动失败

**已确认的证据**

- [fast-ddd-web/pom.xml](../fastddd-framework/fast-ddd-web/pom.xml) 将 fast-ddd-application 标记为 optional。
- [GlobalExceptionHandler](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/exception/GlobalExceptionHandler.java) 直接引用 CommandValidationException 及其内部类型。
- 在消费者 classpath 排除 application 后，Web 自动配置可复现 ClassNotFoundException。

**影响**

只使用贫血模型或基础 Web 能力的消费者，可能在框架测试通过的情况下启动失败。reactor classpath 会掩盖该问题。

**修复方案**

1. 将 CQRS 异常处理拆入独立配置类，并以 class 条件隔离所有 application 类型引用。
2. 或将 application 改为非 optional；但这会增加 Web 基础模块耦合，不作为首选。
3. 使用独立消费者工程或 Maven Invoker 验证 optional 依赖真实边界。

**验收标准**

- classpath 不含 fast-ddd-application 时，fast-ddd-web 消费者可启动。
- classpath 包含 application 时，CommandValidationException 仍被正确转换。
- CI 中的测试不依赖 reactor 意外提供 optional 依赖。

### P0-3：L1 缓存没有 cache name 命名空间

**已确认的证据**

- [CacheRegistry](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/CacheRegistry.java) 默认把同一个 CaffeineLocalCache 实例复用于不同 cache name。
- [CaffeineLocalCache](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/local/CaffeineLocalCache.java) 只按裸 key 存取。
- 已复现：users 缓存写入 key=1 后，orders 缓存读取同一 key 得到 users 的值；清理 orders 还会清空 users。

**影响**

可能出现跨业务缓存串值、ClassCastException、误清理和数据泄露。这不是性能问题，而是数据隔离错误。

**修复方案**

1. 每个 cache name 创建独立 Caffeine Cache；或统一以 cacheName 和 key 组成内部键。
2. L1、L2、失效通知和统计指标统一使用相同命名空间规则。
3. 为 clear、evict、null value 和多级缓存同步增加隔离测试。

**验收标准**

- 不同 cache name 使用相同 key 时互不读取、覆盖或清理。
- Redis 同步通知只影响目标命名空间。
- 并发创建相同 cache name 时只产生一个一致实例。

### P0-4：SQL 监控自动配置会导致应用启动失败

**已确认的证据**

- [SqlMonitoringAutoConfiguration](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringAutoConfiguration.java) 同时使用 EnableConfigurationProperties 和显式属性 Bean，产生两个 SqlMonitoringProperties。
- 已复现 NoUniqueBeanDefinitionException。
- [SqlMonitoringController](../fastddd-framework/fast-ddd-monitor/src/main/java/com/gallop/fastddd/monitor/sql/SqlMonitoringController.java) 是普通 MVC Controller，既不是真正的 Actuator Endpoint，也没有被典型消费者自动配置导入。
- starter 内的 spy.properties 会成为消费者全局资源，但 formatter 又依赖 Spring 注入，运行链路不稳定。

**影响**

启用监控可能直接阻断启动；即使绕过 Bean 冲突，真实 SQL、指标、脱敏和端点链路仍不闭环。

**修复方案**

1. SqlMonitoringProperties 只保留一种注册方式。
2. 使用 Spring 管理的 DataSource Proxy，或建立可验证的 P6Spy 与 Spring bridge。
3. 将管理接口实现为真正的 Actuator Endpoint，并遵循 exposure 配置。
4. 不在通用 starter JAR 中放置会全局生效的 spy.properties。
5. SQL 参数日志默认关闭，并提供字段脱敏策略。

**验收标准**

- 最小消费者引入 monitor 后可以启动。
- 执行真实 SQL 后能产生 Micrometer 指标，并能从配置允许的管理端点读取。
- 未启用 SQL 明文记录时，日志不包含密码、Token 和敏感字段值。

### P0-5：本地事件、Outbox 与 RocketMQ 的自动配置边界失效

**已确认的证据**

- [EventAutoConfiguration](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/EventAutoConfiguration.java) 组件扫描整个 event 包，仅排除 RocketMQ。
- [OutboxEventConfiguration](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/outbox/OutboxEventConfiguration.java) 会要求 Mapper 和 DataSource。
- 无数据库的本地事件消费者已复现缺少 SqlSessionFactory 或 SqlSessionTemplate 而启动失败。
- [OutboxEventRepository](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/outbox/OutboxEventRepository.java) 存在同名 MyBatis 映射方法的 statement id 冲突。
- [RocketMQPublisher](../fastddd-framework/fast-ddd-event/src/main/java/com/gallop/fastddd/event/rocketmq/RocketMQPublisher.java) 的 FOR UPDATE 锁未覆盖完整发送事务。
- 没有 transport 时可能出现只入库、不投递或选择错误发布器。

**影响**

本地事件消费者被迫依赖数据库；Outbox 可靠投递、多实例竞争和降级语义不可预测，可能造成静默积压或重复发送。

**修复方案**

1. 拆分四层：event-core、outbox-store、outbox-dispatcher、rocketmq-transport。
2. 禁止通过宽泛组件扫描装配 Outbox，改为明确的条件自动配置。
3. 只有显式开启 Outbox 时才要求 DataSource 和 Mapper。
4. Outbox 开启但没有 dispatcher/transport 时启动失败。
5. 重命名冲突的 Mapper 方法并补齐 SQL 映射测试。
6. claim/lease、状态转换和发送边界必须具备明确事务模型；多实例下验证重试、幂等和死信。

**验收标准**

- event-core 在无数据库、无 MyBatis、无 RocketMQ 时可启动并发布本地事件。
- Outbox 模式缺少存储或 transport 时给出明确 Failure Analysis。
- 两个实例并发消费时，同一事件只被一个实例 claim。
- 重试、死信、恢复和积压指标有 Testcontainers 回归测试。

### P0-6：生成项目植入通用默认管理员口令

**已确认的证据**

- [init-data.sql.ftl](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/init-data.sql.ftl) 固定创建 admin/admin。
- 该账号被绑定 ADMIN 角色，并分配全部权限。

**影响**

只要业务执行生成的初始化 SQL，就会形成可预测的通用超级管理员入口。“首次登录后修改”不能消除默认暴露窗口。

**修复方案**

1. 默认初始化脚本不创建任何账号。
2. 开发演示账号必须进入显式 dev profile，且不能进入生产 SQL。
3. 如业务要求自动初始化管理员，密码必须来自部署 Secret 或一次性随机值，并在首次登录强制修改。
4. CI 扫描模板和产物，禁止已知用户名/口令、固定 JWT secret 和云服务密钥。

**验收标准**

- 默认生成项目没有 admin/admin 或其他固定管理账号。
- 无管理员初始化参数时，生产启动不会创建管理员。
- 一次性凭据不能在日志、Git、镜像层或构建产物中泄露。

### P0-7：JWT 关闭开关不能关闭完整 JWT Bean 图

**已确认的证据**

- [SecurityAutoConfiguration](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/config/SecurityAutoConfiguration.java) 的 HTTP 安全配置受 enabled 条件控制。
- [FastDddSecurityAutoConfiguration](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/FastDddSecurityAutoConfiguration.java) 的外层配置仍会创建 signer。
- [HmacJwtSigner](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/jwt/HmacJwtSigner.java) 依赖 JwtProperties 并校验 secret。
- 已复现 enabled=false 且无 secret 时，因缺少 JwtProperties 而确定性启动失败。

**影响**

业务无法通过开关真正禁用 JWT，也无法安全地只使用自定义 SecurityFilterChain 或外部身份服务。

**修复方案**

1. signer、provider、filter、认证 Controller 与默认安全链使用同一模式条件。
2. 将布尔开关升级为 disabled、resource-server、auth-server 三种明确模式。
3. HMAC、RSA/JWK 等签名方式使用独立条件；只校验当前模式真正需要的配置。

**验收标准**

- disabled 模式在无 JwtProperties、无 secret 时可启动。
- resource-server 模式不会自动暴露登录、刷新和验证码端点。
- auth-server 的 HMAC 模式缺 secret 时启动失败并指出准确配置路径。

## 3. P1：高概率增加接入成本或造成生产偏差

### P1-1：叶子模块混入构建期和可选运行时能力

fast-ddd-starter 作为 all-in-one 入口本身不是错误，但其叶子模块会传递带入 MyBatis Generator、Druid、MySQL 驱动、SpringDoc 和 Knife4j。

相关证据：

- [fast-ddd-crud/pom.xml](../fastddd-framework/fast-ddd-crud/pom.xml)
- [fast-ddd-web/pom.xml](../fastddd-framework/fast-ddd-web/pom.xml)
- [fast-ddd-starter/pom.xml](../fastddd-framework/fast-ddd-starter/pom.xml)

**建议**：把代码生成、连接池、数据库驱动、OpenAPI UI 从核心运行时叶子模块拆出；保留 all-in-one starter 作为显式聚合入口。

### P1-2：Web 自动配置默认改变已有 API 契约

- [FastDddWebAutoConfiguration](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/FastDddWebAutoConfiguration.java) 自动导入响应处理。
- [SmartResponseAdvice](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/advice/SmartResponseAdvice.java) 会包装业务响应。
- 框架还会改变 Long、枚举和时间的 Jackson 序列化规则。

**建议**：通用 Web starter 默认不包装响应、不改变全局 ObjectMapper；响应 envelope、异常格式和 Jackson 规则分别 opt-in。管理后台聚合 starter 可选择默认开启。

### P1-3：安全默认 Bean、白名单与 CORS 不适合通用 starter

- SecurityFilterChain、PasswordEncoder 和 SecurityCurrentUserProvider 缺少完整的业务 Bean 退让机制。
- [SecurityHttpProperties](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/config/SecurityHttpProperties.java) 默认公开 WebSocket、H2、Swagger、Initializr 等业务相关路径。
- [CorsProperties](../fastddd-framework/fast-ddd-security-starter/src/main/java/com/gallop/fastddd/security/config/CorsProperties.java) 通过 JVM system property 判断 profile，不读取 Spring Environment。

**建议**：默认安全链仅在不存在业务链时创建；通用默认白名单收敛到最小集合；由 Environment 和 Profiles 判断环境；提供 FastDddSecurityCustomizer 供业务扩展同一链。

### P1-4：搜索模块的 SQL 零配置承诺不成立

- [FastDddSearchAutoConfiguration](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/FastDddSearchAutoConfiguration.java) 只有在 MyBatis BaseMapper 存在时创建 SQL SearchService。
- [SqlSearchService](../fastddd-framework/fast-ddd-search/src/main/java/com/gallop/fastddd/search/sql/SqlSearchService.java) 在 Mapper 无法解析时可能返回空结果。

**建议**：拆分 search-sql 与 search-meilisearch；SQL 模式缺少 Mapper 或依赖时启动失败，不把配置错误转换为空集合。

### P1-5：生成模板覆盖调用者的数据源输入

- 根配置固定激活 dev。
- [application-dev.yml.ftl](../fastddd-tooling/fastddd-templates/src/main/resources/templates/project/application-dev.yml.ftl) 会用 localhost、artifactId 和 your_password 覆盖 Studio 输入。
- CRUD 依赖选择 Druid，但生产模板配置 Hikari 参数，配置与实际连接池可能错位。

**建议**：模板只写入用户确认的数据源参数；连接池类型作为显式选项；不同 profile 继承同一逻辑配置，不重新拼装库名和密码。

### P1-6：生成器默认输出 deprecated 且高风险的 CRUD

- [BaseController](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/controller/BaseController.java) 已废弃。
- [贫血模型 Controller.ftl](../fastddd-tooling/fastddd-templates/src/main/resources/templates/anemic/Controller.ftl) 仍生成该基类。
- [BaseAdminCrudController](../fastddd-framework/fast-ddd-web/src/main/java/com/gallop/fastddd/web/controller/BaseAdminCrudController.java) 直接以 PO 作为请求体。

**建议**：禁止生成 deprecated 基类；默认生成只读 Controller 或 DTO/Command 写接口；写端点生成显式权限、校验、审计和数据权限扩展点。

### P1-7：ID 策略在实体、模板、DDL 与手册之间矛盾

- [BasePO](../fastddd-framework/fast-ddd-model/src/main/java/com/gallop/fastddd/model/po/BasePO.java) 显式使用 ASSIGN_ID。
- 模板生成 id-type:auto。
- 手册又描述为可由 MyBatis 全局配置控制。

当前证据支持“ID 策略矛盾”，尚不足以证明乐观锁本身存在运行时冲突。

**建议**：明确雪花 ID 与数据库自增两套策略；实体注解、DDL、模板和手册由同一选项生成。

### P1-8：业务自定义 CacheManager 时框架不退让

[FastDddCacheAutoConfiguration](../fastddd-framework/fast-ddd-cache/src/main/java/com/gallop/fastddd/cache/FastDddCacheAutoConfiguration.java) 在业务已有 CacheManager 时仍可能创建框架 CacheManager，已验证会因无唯一候选导致启动失败。

**建议**：默认 Bean 对现有 CacheManager 退让；如确需并存，使用明确 bean name 和 CacheResolver，不依赖 Primary 猜测。

### P1-9：版本治理存在多个事实来源

- [fastddd-bom/pom.xml](../fastddd-bom/pom.xml) 管理框架与推荐第三方版本。
- 单项目、多端模板、Studio 和 Archetype 仍存在独立版本默认值。

**建议**：维护单一版本 manifest，驱动根 POM、BOM、模板、Studio、Archetype、文档和兼容矩阵；模板只 import BOM，不重复管理 BOM 已覆盖的版本。

### P1-10：参考手册包含不可编译或过期示例

[FASTDDD_REFERENCE.md](FASTDDD_REFERENCE.md) 当前至少存在：

- 推荐项目中不存在的 RequiresPermission 注解。
- 声称至少 64 字节、实际不足要求的 JWT secret 示例。
- 已过期的 logout 语义。
- 把必须配置 secret 的 HMAC 路径称为零配置。
- ID 策略在不同章节互相矛盾。

**建议**：所有 Java、YAML 和 Maven 示例进入可编译、可启动的文档测试；配置表从 metadata 生成；历史迁移内容移入独立迁移指南。

## 4. P2：架构与质量债务

### 4.1 自动配置使用全局副作用

- 事件模块启用全局异步和调度，并使用通用 taskExecutor 名称。
- 多个模块通过宽泛 ComponentScan 将实现直接加入消费者容器。
- 缓存、响应处理和序列化配置会影响全应用。

**建议**：框架使用专用 executor、明确 bean name、条件自动配置和稳定 SPI；自动配置类直接声明所需 Bean，不扫描整个实现包。

### 4.2 开发默认值缺少生产边界

- 验证码内存存储和 Noop 限流只适合开发。
- Swagger、H2、WebSocket、SQL 参数与详细异常信息应采用最小暴露面。
- 生产环境缺少限流器、Secret、撤销存储或必要基础设施时应 fail-fast。

### 4.3 消费者契约测试不足

GeneratedProjectCompileTest 等测试名称声称验证编译，实际主要检查字符串、XML 和 YAML，没有执行真实 Maven 构建和 Spring 启动。框架内部 reactor 测试还会掩盖 optional 依赖边界。

## 5. 目标架构与基础框架治理规范

### 5.1 三层产品架构

FastDDD 应明确区分契约层、能力实现层和聚合层，避免把“方便引入”与“全部能力耦合”混为一体。

#### 第一层：稳定契约层

契约层只保留领域和应用层基础类型、异常规范及稳定 SPI：

- Domain、Application 基础契约。
- CurrentUser、EventPublisher、Cache、Search 等扩展接口。
- 通用错误码和最小公共模型。
- 不引入数据库驱动、连接池、Web UI、消息中间件、P6Spy 或具体认证服务。

契约层应具备最高稳定性。1.0 GA 后，其公共 API 变更必须经过兼容性审查。

#### 第二层：可组合能力层

能力层提供基础设施实现和 Spring Boot 自动配置：

    fast-ddd-core
    ├── fast-ddd-mybatis-starter
    ├── fast-ddd-web-starter
    │   ├── fast-ddd-response-envelope-starter
    │   └── fast-ddd-openapi-starter
    ├── fast-ddd-security-resource-server
    ├── fast-ddd-security-auth-server
    ├── fast-ddd-event-core
    │   ├── fast-ddd-event-outbox-store
    │   ├── fast-ddd-event-outbox-dispatcher
    │   └── fast-ddd-event-rocketmq
    ├── fast-ddd-cache-starter
    ├── fast-ddd-search-sql
    ├── fast-ddd-search-meilisearch
    └── fast-ddd-monitor-sql

能力层必须可单独引入、单独关闭，并对业务已有实现退让。

#### 第三层：场景聚合层

聚合层面向快速开发和特定场景：

- fast-ddd-starter：明确标注为 all-in-one 聚合入口。
- 后台管理、AI、Analyzer、Studio 等场景 starter。
- 项目模板与 Archetype。

聚合层可以选择更有主张的默认值，但必须在依赖说明和配置元数据中明确列出，不得把这些默认值下沉到通用能力层。

### 5.2 自动配置统一规范

新增或重构自动配置时必须遵守下列规则：

| 规则 | 要求 |
|---|---|
| 依赖条件 | 使用 ConditionalOnClass 判断实现所需依赖，不直接引用可能缺失的 optional 类型 |
| 功能条件 | 跨切面和基础设施能力使用明确的 ConditionalOnProperty 或模式枚举 |
| 业务退让 | 默认 Bean 使用精确的 ConditionalOnMissingBean；框架实现不得无条件 Primary |
| 装配边界 | 自动配置类显式声明 Bean，禁止扫描整个框架实现包 |
| Bean 命名 | 使用模块专用名称，不占用 taskExecutor、cacheManager 等通用名称 |
| 失败语义 | 缺少关键依赖、Secret 或 SPI 时提供 Failure Analysis，不返回空集合掩盖问题 |
| 关闭语义 | enabled=false 或 disabled 模式必须关闭完整 Bean 图和对外端点 |
| 可观察性 | 启用、退让、降级和失败原因应可从 conditions 与启动日志确认 |
| 测试要求 | ApplicationContextRunner 之外，还要用独立消费者验证真实 classpath |

布尔开关只适用于单一能力。存在多种运行角色时，应使用模式枚举：

- Security：disabled、resource-server、auth-server。
- Event：local、outbox。
- Outbox Transport：none、rocketmq，未来可扩展其他实现。
- Search：sql、meilisearch。

### 5.3 消费者 API 契约边界

通用 starter 默认不得改变消费者已有契约，包括：

- 不默认包装全部 Controller 返回值。
- 不默认把全部 Long 转成 String。
- 不默认改变枚举、日期和时间序列化格式。
- 不默认接管 SecurityFilterChain。
- 不默认公开 Swagger、H2、WebSocket 或业务认证路径。
- 不覆盖业务 ObjectMapper、CacheManager、PasswordEncoder 或线程池。

响应 envelope、全局异常格式、Jackson 扩展、OpenAPI UI 等能力应独立 opt-in。特定场景聚合 starter 可以默认启用，但必须在生成结果和文档中明确说明。

### 5.4 安全默认规范

1. 框架、模板和示例不得提供默认管理员、固定密码或默认 JWT secret。
2. 通用安全 starter 使用最小白名单，业务路径由消费者声明。
3. Auth Server 与 Resource Server 分离，资源服务不自动暴露登录和刷新端点。
4. 生产环境缺少 Secret、限流器、Token 撤销存储等关键能力时 fail-fast。
5. 示例凭据、数据库密码和第三方 Key 纳入 CI 凭据扫描。
6. dev 默认值只能在显式开发 profile 生效，不能进入生产 SQL、镜像或发布包。

### 5.5 单一事实来源

建立机器可读的版本与配置 manifest，作为以下内容的唯一输入：

- 根 POM 和 fastddd-bom。
- 各模块版本及兼容矩阵。
- 单项目、多端模板和 Archetype。
- Studio 默认值。
- Configuration Metadata。
- FASTDDD_REFERENCE.md 配置表和发布说明。

配置名称、默认值和弃用信息以 Configuration Metadata 为准；模板不得手工维护另一套属性模型。

## 6. 改造任务与执行路线

### 6.1 阶段 A：1.0 GA 可靠性门禁

| 任务 | 内容 | 主要模块 | 估算 |
|---|---|---|---:|
| AUD-P0-01 | 修复全部模板属性契约，删除固定 secret 和未知属性 | templates、Studio | 2–3 人日 |
| AUD-P0-02 | 隔离 Web 对 application optional 类型的引用 | web | 1–2 人日 |
| AUD-P0-03 | 修复 L1 缓存命名空间与清理边界 | cache | 1–2 人日 |
| AUD-P0-04 | 修复 SQL 监控 Bean 与真实运行链路 | monitor | 2–4 人日 |
| AUD-P0-05 | 重构 event、outbox、dispatcher、transport 自动配置边界 | event | 5–8 人日 |
| AUD-P0-06 | 移除模板默认管理员并增加凭据扫描 | templates、CI | 1–2 人日 |
| AUD-P0-07 | 统一 JWT 模式条件与关闭语义 | security | 1–2 人日 |
| AUD-P0-08 | 建立独立消费者与生成项目真实启动门禁 | testing、CI | 3–5 人日 |

阶段 A 预计 16–28 人日。事件链路与 SQL 监控是波动最大的部分，应作为独立任务和独立回归处理。

### 6.2 阶段 B：1.0 GA 前的架构收敛

以下 P1 不应推迟到 GA 之后：

1. SecurityFilterChain、PasswordEncoder、CurrentUserProvider 与 CacheManager 完整退让。
2. 响应包装和全局 Jackson 修改改为显式启用。
3. 修复数据源模板覆盖用户输入以及 Druid/Hikari 错配。
4. 禁止生成 deprecated Controller 和固定凭据。
5. 建立单一版本 manifest，消除 BOM、模板、Studio 和 Archetype 漂移。

其余依赖拆分可在不影响 GA 可靠性的前提下分批完成，但必须形成明确路线图。

### 6.3 阶段 C：1.0.x 稳定化

1. 精简叶子模块传递依赖，保留 all-in-one 聚合入口。
2. 完成搜索、监控、OpenAPI 和响应包装的能力拆分。
3. 修复 ID 策略、DTO/Command 生成和数据权限扩展契约。
4. 持续补充消费者矩阵，不在 1.0.x 改变默认 API 契约。

### 6.4 阶段 D：1.1 架构演进

1. 完成契约层、能力层和聚合层的物理模块调整。
2. 引入新的稳定 SPI 与模式化自动配置。
3. 对确需改变的默认行为提供迁移指南和弃用周期。
4. 不在 1.0.x 以普通修复名义引入破坏性模块重命名或公共 API 变化。

## 7. 版本与发布策略

### 7.1 1.0 基线

- Java：JDK 21。
- Spring Boot：3.5.16。
- Spring AI：1.1.x 维护线。
- FastDDD Framework、BOM 和 Framework Starter：产品内锁步版本。
- Tooling、Studio：独立版本，通过机器可读兼容矩阵声明所依赖的 Framework/Tooling 版本范围。

当前 Framework 产品线统一保留 1.0.0-SNAPSHOT，完成门禁后依次进入 1.0.0-RC1 和 1.0.0 GA。Tooling 与 Studio 保留各自 revision，通过兼容矩阵跟随 Framework RC 验证。业务系统仍在开发，可在同一升级分支同步迁移，不需要为错误的 SNAPSHOT 配置契约维持长期兼容。

### 7.2 产品内锁步与跨产品兼容规则

1. Framework 根 revision 是 Framework 产品线发布版本的唯一输入。
2. fastddd-bom 与 Framework Starter 必须使用相同 revision，不能独立保留旧 SNAPSHOT。
3. Tooling 与 Studio 各自维护 revision，不通过根 reactor 隐式继承发布版本。
4. 模板、Archetype 和 Studio 不手写目标 Framework 版本，而是从兼容性 manifest 或构建参数获得。
5. CI 同时校验产品内部版本一致性，以及 Studio -> Tooling -> Framework 的兼容组合是否合法。
6. Framework、Tooling、Studio 可以独立发布，不要求同一发布批次使用相同版本号。

### 7.3 兼容性规则

- 1.0.x：只做向后兼容修复、性能优化和安全修复，不改变默认 API 契约。
- 1.1.x：允许经过迁移设计的能力拆分、SPI 扩展和默认行为调整。
- GA 前没有可比较的稳定制品，japicmp 不作为 1.0.0 的阻断项。
- 从首个 GA 制品开始保存二进制基线，在后续版本启用 japicmp 或等价检查。
- 配置废弃必须先告警、提供替代路径，再在下一个允许破坏性变化的版本移除。

### 7.4 Git 与发布分支建议

- 主开发分支继续承载 1.0.0-SNAPSHOT。
- 升级和整改在独立分支完成，经消费者门禁验证后合并。
- 进入 RC 后创建 release/1.0.x 维护线；主分支再进入下一开发版本。
- Framework RC 前先完成单仓逻辑解耦；RC 稳定后、Studio 收费试点前再执行物理仓库拆分。
- 物理拆分后，Framework、Tooling、Studio 各自在自己的仓库和发布分支独立演进。
- 禁止把临时旧坐标或仅为回滚准备的 SNAPSHOT 发布到正式私服。

## 8. 消费者验收矩阵

| 场景 | 必须验证的结果 |
|---|---|
| Web 不含 application | 可启动；基础异常处理可用；CQRS 异常处理自动缺席 |
| 两个 cache name 使用相同 key | 读取、清理和同步完全隔离 |
| 业务已有 CacheManager | 框架退让或按明确名称共存，无歧义 |
| Event 本地模式且无数据库 | 可启动并发布本地事件 |
| Outbox 无 store 或 transport | 启动失败并指出缺少的组件 |
| Outbox 两实例 | claim、重试、幂等、死信和恢复正确 |
| Monitor | 真实 SQL 产生指标，管理端点受 Actuator exposure 控制 |
| JWT disabled | 无 secret 可启动，且不注册 JWT 相关端点和过滤器 |
| 业务已有 SecurityFilterChain | 不冲突，框架能力可显式插入 |
| 最小 Web 项目 | 默认不改变响应 envelope 与全局 Jackson 契约 |
| SQL/Meilisearch 搜索 | 依赖、Mapper 或索引缺失时可诊断 |
| 单端/多端、贫血/充血生成 | Maven 编译、Spring 启动、属性绑定和健康检查通过 |
| 生成安全基线 | 无默认管理员、固定 secret、未知配置项和开发数据库口令 |
| BOM 对齐 | 生成项目 dependency tree 与当前 BOM 一致 |
| 升级兼容 | 1.0.x 公共 API、配置与默认行为保持兼容 |

验证必须在独立消费者 classpath 中执行，不能只依赖框架 reactor。GeneratedProjectCompileTest 必须执行真实 Maven 构建和 Spring 启动，或更名以准确表达其验证范围。

## 9. 文档治理建议

FASTDDD_REFERENCE.md 应从“必须背诵的避坑手册”回归为 API 与设计参考：

1. 保留模块选择、SPI、最小示例和兼容矩阵。
2. 将危险配置转换为启动期校验，不要求调用者记忆。
3. 配置项、默认值和版本由 Configuration Metadata 与版本 manifest 生成。
4. 每段可复制 Java、YAML 和 Maven 示例都由 CI 在独立消费者中编译和启动。
5. 历史兼容与升级差异进入独立迁移指南。
6. 文档不得描述代码中不存在的注解、配置项、端点或降级行为。

## 10. 最终架构验收与发布决策

### 10.1 最终验收标准

FastDDD 达到基础框架发布标准，需要同时满足：

1. 不依赖避坑文档：业务方只根据模块说明、配置元数据和 IDE 提示即可完成基本接入。
2. 正确配置可预测：选择一个能力后，只出现该能力声明的 Bean、端点和依赖。
3. 错误配置可诊断：启动期指出缺少的属性、依赖或 SPI，不静默返回空结果。
4. 业务实现可覆盖：消费者已有安全链、缓存、序列化器和线程池时，框架能够退让或明确组合。
5. 默认行为安全：不生成默认管理员、固定 secret、宽泛白名单或生产明文敏感日志。
6. 独立消费者可证明：最小项目和生成项目在真实 classpath 下完成编译、启动和关键行为验证。
7. 版本事实唯一：BOM、框架、模板、Studio、Archetype、文档和 dependency tree 完全对齐。

### 10.2 发布判断

当前不建议发布 1.0 GA。应先关闭全部 P0，完成阶段 B 中会导致启动冲突或全局契约变化的 P1，并把消费者验收矩阵纳入 CI，再进入 RC。

FastDDD 下一阶段最重要的工作不是继续增加能力，而是让已有能力具备清晰边界、可预测默认值、显式开关、可诊断失败和可验证接入方式。当业务方无需依赖额外避坑说明，只需选择模块并提供必要配置与 SPI 就能稳定启动时，FASTDDD_REFERENCE.md 才真正是参考手册，而不是不可缺少的防坑补丁。
