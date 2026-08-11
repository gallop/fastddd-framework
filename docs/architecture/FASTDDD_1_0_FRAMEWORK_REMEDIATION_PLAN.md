# FastDDD 1.0 基础框架架构整改实施方案

> 版本：1.3
>
> 制定日期：2026-08-11
>
> 代码基线：`upgrade/jdk21-boot3.5.16`，HEAD `740da39`
>
> 技术基线：JDK 21、Spring Boot 3.5.16、Spring AI 1.1.x
>
> 输入依据：[框架消费者体验与架构审计报告](../FASTDDD_FRAMEWORK_ARCHITECTURE_AUDIT.md)
>
> 执行对象：Claude Code、Codex 或其他具备仓库读写和测试能力的编码 Agent
>
> 项目属性：中长期分阶段架构整改与产品拆分项目，不是普通的 GA 前清理清单；固定工作量之外还包含开放式业务迁移工时

## 1. 文档定位

审计报告回答“框架有什么问题、风险多大、建议往哪里演进”；本实施方案回答：

1. 先改什么、后改什么。
2. 哪些工作可以并行，哪些必须串行。
3. 每个任务修改哪些模块和文件。
4. 每个任务如何测试和验收。
5. 哪些任务分别阻断 Framework 内部 RC、Framework 公开 GA 和 Studio 商业发布。
6. 如何把 ID 类型系统重构纳入整体整改，而不造成模板和业务系统重复迁移。
7. 如何在单仓库中先完成逻辑解耦，再在 Framework RC 后拆分 Studio 与 Tooling。

本方案不重复 JDK 21 与 Boot 3.5.16 升级工作。既有升级过程见 [JDK21_SPRING_BOOT_3_5_UPGRADE_PLAN.md](JDK21_SPRING_BOOT_3_5_UPGRADE_PLAN.md)。

## 2. Claude Code 执行入口

执行时必须遵守：

1. 从 `FRM-000` 开始，一次只执行一个任务。
2. 每次开始前读取本方案中的任务目标、依赖、禁止项和完成定义。
3. 检查 Git 分支、HEAD 和工作树，保护用户已有修改。
4. Maven 必须运行在 JDK 21；本机已验证路径为 `D:\usr\local\jdk\jdk-21.0.3`。
5. 不以“编译通过”代替验收；自动配置任务必须验证上下文，基础设施任务必须验证真实行为。
6. 高风险任务完成后必须做独立代码 review：ID、Security、Outbox、SQL 监控、默认凭据。
7. 每个任务只提交自身范围，不把无关格式化、依赖升级或重构混入。
8. 任务完成后记录：修改文件、验证命令、测试结果、遗留风险和提交号。
9. 未达到完成定义不得标记完成。
10. 如果实现与本方案的架构基线冲突，先停止实现并修订方案，不在代码中自行发明第三种设计。

推荐闭环：

    读取任务
      → 检查依赖
      → 标记进行中
      → 添加失败测试或消费者复现
      → 实现最小修复
      → 局部测试
      → 模块测试
      → 独立消费者验证
      → 代码 review
      → 修复 review 问题
      → 更新执行记录
      → 标记完成

## 3. 架构基线

### 3.1 本轮必须解决

- 审计报告中的 7 个 P0。
- ID 类型系统、BaseEntity、BasePO、CRUD 与 Controller 的架构重构。
- 会在 GA 后形成公共 API 锁定的高风险 P1。
- 独立消费者、生成项目和真实基础设施验证门禁。
- Framework 产品内版本单一来源，以及 Tooling/Studio 的机器可读兼容矩阵。
- Framework、Tooling、Studio 的单向依赖、独立版本和商业交付边界。

### 3.2 本轮明确不做

- 升级到 Spring Boot 4 或 Spring Framework 7。
- 引入完整 OAuth2 Authorization Server、OIDC、SAML 或多租户平台。
- 在 1.0 中引入 disabled、resource-server、auth-server 三模式以及 RSA/JWK 完整能力；这些安全模式演进推迟到 1.1。
- 为尚未发布的错误 SNAPSHOT 配置建立长期兼容层。
- 在没有业务需求的情况下新增消息中间件或搜索后端。
- 以大规模包重命名替代自动配置边界修复。
- 在 1.0 GA 前强行启用没有稳定 GA 基线的二进制比较门禁。

### 3.3 ID 架构决策基线

ID 改造遵循以下约束：

1. 领域模型只定义身份契约，不依赖 MyBatis 注解。
2. 贫血 CRUD 的具体 PO 显式声明 ID 类型与生成策略。
3. 充血模型允许使用 OrderId 等强类型值对象，PO 仍使用数据库友好的 Long、String 或 UUID。
4. 不再以 Serializable 作为全链路 ID 类型。
5. 不允许子类重新声明父类 id 进行字段隐藏。
6. Controller 根据明确的 IdCodec 或真实类型转换 ID，不根据 MyBatis IdType 猜测 Java 类型。
7. 雪花 Long 在 API DTO 中显式输出 String，不全局修改所有 Long 的 Jackson 行为。
8. 领域实体 ID 在创建时确定，避免 equals/hashCode 随持久化状态变化。
9. 审计字段、逻辑删除和乐观锁属于持久化能力，不强迫所有领域实体继承。
10. 生成器负责消除具体 PO 重复声明 ID 的样板成本。

### 3.4 自动配置基线

所有自动配置必须满足：

- ConditionalOnClass 控制依赖边界。
- ConditionalOnProperty 或模式枚举控制功能边界。
- ConditionalOnMissingBean 对消费者实现退让。
- 禁止通过宽泛 ComponentScan 装配整个模块。
- 禁止占用通用 `taskExecutor`、`cacheManager` 等 Bean 名称。
- 关闭开关必须关闭完整 Bean 图。
- 缺少关键依赖时 fail-fast，并提供可执行错误信息。
- 必须在独立消费者 classpath 验证 optional 依赖。

### 3.5 模块拆分与商业产品边界

模块拆分遵循“先逻辑解耦、后物理拆仓”：

1. Framework 是最底层稳定产品，不得依赖 Tooling 或 Studio。
2. Tooling 只能依赖 Framework 的公开 API 与 BOM，不得依赖 Studio。
3. Studio 是 Framework 与 Tooling 的普通消费者，不继承 Framework 根 POM。
4. FastDoc Controller 和自动配置从 Framework Web 移入 Tooling/FastDoc Starter。
5. Studio 只依赖稳定的 Tooling API，不直接导入 Generator 内部实现。
6. 逻辑解耦在当前仓库内完成并通过 reactor 测试。
7. Framework 进入 RC 后再执行物理拆仓，避免整改期间跨仓 SNAPSHOT 协调。
8. 物理拆仓保持已有 Maven groupId 和 artifactId，避免消费者再次迁移坐标。
9. Framework、Tooling、Studio 使用独立版本和发布流水线。
10. 物理拆仓与跨仓构建不阻断 Framework 内部 RC，但同时阻断 Framework 公开 GA 和 Studio 付费试点。

目标依赖方向：

    Studio Pro
        ↓
    Tooling API / Generator Engine
        ↓
    Framework / BOM

### 3.6 开源与公开发布边界

Framework 开源不是物理拆仓的副产品，而是一条独立的产品、法律、安全和供应链工作流：

1. Framework 计划采用 Apache-2.0 时，必须先由代码权利人确认版权归属、第三方代码来源和最终许可证选择；本方案不能替代法律意见。
2. Tooling 与 Studio 的源码开放范围、商业授权和商标策略由 SPLIT-001 单独决策，不因 Framework 开源自动采用相同许可证。
3. `1.0.0-RC1` 只发布到私有源码仓库和内部 Maven 仓库，不公开 Git 仓库、RC Tag、制品下载或面向外部的坐标说明。
4. 公共 API 在内部 RC1 前冻结；RC 后如需破坏性调整，必须重新发布 RC2，不得直接在 GA 中修改。
5. Framework 只有在物理拆仓、OSS-001、跨仓验证和 REL-002 全部通过后才能公开仓库并发布 1.0.0 GA。
6. 公开仓库和发布包不得包含 Studio Pro 私有源码、商业许可证实现、客户信息、密钥或仅供内部使用的部署资料。
7. 开源发布必须同时覆盖源码分发与二进制分发的 LICENSE、NOTICE、第三方许可证、SBOM、签名和安全披露入口。

## 4. 里程碑与依赖

### 4.1 里程碑

| 里程碑 | 目标 | Framework 内部 RC | Framework 公开 GA | Studio 商业发布 |
|---|---|---|---|---|
| M0 | 基线保护、ADR、产品边界和消费者测试骨架 | 是 | 是 | 是 |
| M1 | 关闭确定性 P0，消除 Framework 反向依赖 | 是 | 是 | 是 |
| M2 | 完成 ID 类型系统、Tooling API 和 Studio 消费者化 | 是（破坏性 ID 迁移受状态门禁约束） | 是 | 是 |
| M3 | 收敛公共契约，准备 Studio Server 依赖隔离 | 是（不含 SPLIT-005） | 是 | 是 |
| M4 | 生成项目、业务消费者、开源就绪和内部 RC 验收 | 是 | 是 | 是 |
| M5 | 物理拆仓、跨仓验证、Framework 公开 GA 与 Studio 商业交付 | 否 | 是 | 是 |

三套门禁必须分别记录，不能再用一个“Framework GA”字段混合表示：

- Framework 内部 RC：只验证框架公共契约和已知消费者，制品与源码保持私有。
- Framework 公开 GA：在内部 RC 稳定后，还必须完成物理拆仓、开源就绪最终核验和公开发布。
- Studio 商业发布：在 Framework 公开 GA 基础上，额外完成 UI、部署包、商业许可证和客户试点验证。

M2 中的 Tooling API、Studio 消费者化和 Framework 逻辑解耦始终是内部 RC 和公开 GA 门禁。破坏性 ID 替换是否必须在 RC1 前完成，由 FRM-001 的消费者生产状态关卡决定：

- 所有有效消费者均为 `DEVELOPMENT` 或 `TEST_UAT`，且负责人承诺在 RC1 前同步迁移：ID-002～ID-008 继续作为内部 RC 门禁。
- 存在 `PRODUCTION` 或 `UNKNOWN` 消费者：不得直接删除旧契约，必须增加隔离的兼容适配与弃用计划，或者将破坏性删除延后到 1.1。
- `RETIRED` 消费者不阻断 RC 或 GA，但必须记录下线证据。

### 4.2 主依赖图

```mermaid
flowchart TD
    A["FRM-000 基线保护"] --> B["FRM-001 ADR 与公共契约冻结"]
    A --> C["FRM-002 独立消费者测试骨架"]
    B --> S1["SPLIT-001 产品边界与依赖 ADR"]
    S1 --> S2["SPLIT-002 移除 Framework 到 FastDoc 反向依赖"]

    B --> I1["ID-001 当前行为特征测试"]
    I1 --> S3["SPLIT-003 抽取 Tooling API"]
    I1 --> I2["ID-002 领域身份契约"]
    I1 --> I3["ID-003 持久化 ID 模型"]
    I2 --> I4["ID-004 Repository/Service 泛型化"]
    I3 --> I4
    I4 --> I5["ID-005 Web ID Codec"]
    I5 --> I6["ID-006 框架内部迁移"]
    I6 --> S4["SPLIT-004 Studio 改为 Tooling API 消费者"]
    S3 --> S4
    S4 --> I7["ID-007 模板与 Studio 迁移"]
    I7 --> I8["ID-008 业务迁移与旧实现移除"]

    C --> P1["CFG-001 配置契约清单"]
    C --> P2["WEB-001 optional 依赖隔离"]
    C --> P3["CACHE-001 缓存命名空间"]
    C --> P4["MON-001/002 SQL 监控"]
    C --> P5["EVT-001~004 事件链路"]
    C --> P6["SEC-001/002 安全 P0"]
    C --> S2
    S2 --> P2

    P1 --> G1["GEN-001 配置模板修正"]
    I7 --> G2["GEN-002 CRUD 模板重构"]
    I7 --> S5["SPLIT-005 Studio Server 依赖隔离"]

    I8 --> Q["QA-001 独立消费者矩阵"]
    G1 --> Q
    G2 --> Q
    P2 --> Q
    P3 --> Q
    P4 --> Q
    P5 --> Q
    P6 --> Q
    Q --> R["QA-002 全量回归"]
    R --> O["OSS-001 开源就绪"]
    O --> RC["REL-001 内部 RC 决策"]
    RC --> S6["SPLIT-006 物理拆分三个仓库"]
    S5 --> S6
    S6 --> S7["SPLIT-007 跨仓发布与兼容验证"]
    S7 --> GA["REL-002 Framework 公开 GA"]
    O --> GA
    GA --> ST["STUDIO-001 Studio 商业发布工程"]
```

### 4.3 并行原则

可与 ID 重构并行：

- Web optional 依赖隔离。
- 缓存命名空间修复。
- SQL 监控修复。
- Event/Outbox 重构。
- 默认管理员移除。
- JWT 关闭语义。
- 配置前缀与未知配置清理。
- Framework 到 FastDoc 的反向依赖移除。

必须等待 ID 方案稳定：

- CRUD Service 和 Controller 公共泛型签名。
- 贫血/充血模型生成模板。
- Studio 的 ID 选项。
- BaseController 迁移。
- ID 文档、OpenAPI 类型和业务消费者迁移。
- Tooling API 的 ID 策略契约。
- Studio 对 Generator 内部类的替换。

物理拆仓必须等待：

- Framework 1.0 公共 API 和 Tooling API 冻结。
- P0、ID 和内部 RC 必做 P1 全部通过。
- QA-002 全量回归完成并形成 Framework RC。
- SPLIT-005 已使 Studio Server 脱离 Framework 根 parent 和源码 classpath。
- OSS-001 已形成可在拆仓后重放的开源就绪清单。

## 5. 一级任务总表

| ID | 任务 | 里程碑 | 依赖 | 内部 RC | Framework 公开 GA | Studio 商业发布 |
|---|---|---|---|---|---|---|
| FRM-000 | 基线保护与整改分支确认 | M0 | 无 | 是 | 是 | 是 |
| FRM-001 | 架构 ADR 与公共契约冻结 | M0 | FRM-000 | 是 | 是 | 是 |
| FRM-002 | 独立消费者测试骨架 | M0 | FRM-000 | 是 | 是 | 是 |
| SPLIT-001 | 产品边界、依赖方向、版本与许可证 ADR | M0 | FRM-001 | 是 | 是 | 是 |
| CFG-001 | 配置契约清单与 metadata 校验 | M1 | FRM-001、002 | 是 | 是 | 是 |
| CFG-002 | 生成配置模板修复 | M1 | CFG-001 | 是 | 是 | 是 |
| SPLIT-002 | 移除 Framework 对 FastDoc 的反向依赖 | M1 | SPLIT-001、FRM-002 | 是 | 是 | 是 |
| WEB-001 | Web optional application 隔离 | M1 | FRM-002、SPLIT-002 | 是 | 是 | 是 |
| CACHE-001 | L1 缓存命名空间隔离 | M1 | FRM-002 | 是 | 是 | 是 |
| MON-001 | SQL 监控属性 Bean 修复 | M1 | FRM-002 | 是 | 是 | 是 |
| MON-002 | SQL 监控真实链路闭环 | M1 | MON-001 | 是 | 是 | 是 |
| EVT-001 | 本地事件与 Outbox 装配边界 | M1 | FRM-001、002 | 是 | 是 | 是 |
| EVT-002 | Outbox Repository 与 Mapper 修复 | M1 | EVT-001 | 是 | 是 | 是 |
| EVT-003 | Dispatcher 与 Transport 契约 | M1 | EVT-002 | 是 | 是 | 是 |
| EVT-004 | Outbox 多实例可靠性验证 | M1 | EVT-003 | 是 | 是 | 是 |
| SEC-001 | 移除默认管理员与固定凭据 | M1 | FRM-002 | 是 | 是 | 是 |
| SEC-002 | JWT disabled 完整关闭与安全默认值 | M1 | FRM-001、002 | 是 | 是 | 是 |
| ID-001 | ID 当前行为特征测试与迁移清单 | M2 | FRM-001、002 | 是 | 是 | 是 |
| SPLIT-003 | 抽取稳定 Tooling API | M2 | SPLIT-001、ID-001 | 是 | 是 | 是 |
| ID-002 | 领域身份契约与相等性重构 | M2 | ID-001 | 是（条件） | 是 | 是 |
| ID-003 | PO 显式 ID 与审计模型重构 | M2 | ID-001 | 是（条件） | 是 | 是 |
| ID-004 | Repository/Service ID 泛型化 | M2 | ID-002、003 | 是（条件） | 是 | 是 |
| ID-005 | IdCodec 与 Controller ID 转换 | M2 | ID-004 | 是（条件） | 是 | 是 |
| ID-006 | 框架内部模块和示例迁移 | M2 | ID-005 | 是（条件） | 是 | 是 |
| SPLIT-004 | Studio 改为 Tooling API 消费者 | M2 | SPLIT-003、ID-006 | 是 | 是 | 是 |
| ID-007 | 模板、插件、Archetype、Studio 迁移 | M2 | ID-006、SPLIT-004 | 是（条件） | 是 | 是 |
| ID-008 | 在研业务迁移与旧 ID 实现移除 | M2 | ID-007 | 是（条件） | 是 | 是 |
| SEC-003 | 安全 Bean 退让、白名单和 CORS | M3 | SEC-002 | 是 | 是 | 是 |
| WEB-002 | 响应包装与 Jackson 显式启用 | M3 | WEB-001 | 是 | 是 | 是 |
| CACHE-002 | CacheManager 退让和组合 | M3 | CACHE-001 | 是 | 是 | 是 |
| SEARCH-001 | 搜索后端缺失时可诊断 | M3 | FRM-002 | 是 | 是 | 是 |
| GEN-001 | 数据源和 profile 模板修复 | M3 | CFG-002 | 是 | 是 | 是 |
| GEN-002 | CRUD、DTO、Command 生成契约 | M3 | ID-007、WEB-002 | 是 | 是 | 是 |
| VER-001 | 产品内版本 Manifest 与跨产品兼容矩阵 | M3 | CFG-001、ID-007、SPLIT-001 | 是 | 是 | 是 |
| SPLIT-005 | Studio Server 依赖隔离与独立构建 | M3 | SPLIT-004、ID-007、VER-001 | 否 | 是 | 是 |
| DOC-001 | 参考手册、AI 契约和迁移指南 | M4 | 上述公共契约完成 | 是 | 是 | 是 |
| QA-001 | 分阶段独立消费者验收矩阵 | M4 | M1、M2、M3 公共契约任务 | 是 | 是 | 是 |
| QA-002 | 全量回归、安全与依赖门禁 | M4 | QA-001、DOC-001 | 是 | 是 | 是 |
| OSS-001 | Framework 开源就绪与公开前安全门禁 | M4 | SPLIT-001、SEC-001、SEC-002、QA-002 | 是 | 是 | 是 |
| REL-001 | 内部 RC 候选与 API 冻结决策 | M4 | QA-002、OSS-001 | 是 | 是 | 是 |
| SPLIT-006 | Framework、Tooling、Studio 物理拆仓 | M5 | REL-001、SPLIT-005 | 否 | 是 | 是 |
| SPLIT-007 | 跨仓发布、兼容矩阵与可靠性验证 | M5 | SPLIT-006 | 否 | 是 | 是 |
| REL-002 | Framework 开源公开与 1.0.0 GA 发布 | M5 | OSS-001、SPLIT-007 | 否 | 是 | 是 |
| STUDIO-001 | Studio UI、部署包与商业发布工程 | M5 | REL-002 | 否 | 否 | 是 |

“内部 RC=否”不表示任务可删除，只表示它不得拖慢 Framework 私有 RC。SPLIT-005、SPLIT-006、SPLIT-007 和 REL-002 仍是 Framework 公开 GA 的硬门禁；STUDIO-001 只阻断 Studio 商业发布。

## 6. M0：治理与测试基础

### FRM-000：基线保护与整改分支确认

**目标**

确保架构整改与已有 JDK/Boot 升级成果、用户未提交文档修改相互隔离。

**步骤**

1. 记录当前分支、HEAD、工作树状态和未提交文件。
2. 确认是否继续使用 `upgrade/jdk21-boot3.5.16`，或创建 `refactor/fastddd-1.0-framework`。
3. 不覆盖当前已修改的审计报告。
4. 运行 JDK、Maven Wrapper 和根 revision 检查。
5. 保存当前全量测试、依赖树和生成项目结果作为整改前基线。

**验收**

- 基线记录包含分支、commit、JDK、Maven、测试总数和已知失败。
- 工作树中每个既有修改都有归属。
- 未执行 reset、checkout 覆盖或批量格式化。

**建议提交**

`docs: 建立 FastDDD 1.0 架构整改执行基线`

### FRM-001：架构 ADR 与公共契约冻结

**目标**

在实现前冻结 ID、自动配置、响应契约、安全模式和事件模式。

**步骤**

1. 新增 ID、自动配置、Event/Outbox、Security 四份 ADR。
2. ADR 明确现状、决策、替代方案、兼容影响和迁移方法。
3. 列出 1.0 GA 将发布的公共 Java API、配置前缀、默认行为和 SPI。
4. 把延后至 1.1 的 Framework 内部能力模块/包结构拆分写入 ADR；Framework、Tooling、Studio 的产品仓库拆分仍按 M5 执行。
5. 建立机器可读的 `docs/architecture/fastddd-consumers.yaml` 消费者清单，至少记录消费者名称、负责人、当前 FastDDD 版本、目标版本、ID 使用范围、迁移状态和验证证据。
6. 消费者生产状态只允许 `DEVELOPMENT`、`TEST_UAT`、`PRODUCTION`、`RETIRED`、`UNKNOWN`；未登记或无法确认的消费者一律按 `UNKNOWN` 处理。
7. 根据消费者状态形成 ID 改造 go/no-go 决策：全部为开发或测试消费者时允许同步破坏性迁移；存在生产或未知消费者时，选择兼容适配与弃用周期，或把破坏性删除推迟到 1.1。
8. 明确消费者状态关卡只约束破坏性 ID 删除，不取消 Tooling API、Studio 消费者化和 Framework 单向依赖的内部 RC/公开 GA 门禁。
9. 对 ADR 和消费者清单做一次架构 review 后冻结。

**验收**

- 后续任务能引用明确决策，不需要在实现中重新选择。
- ID 方案覆盖 Long 雪花、自增、UUID、业务 String 和领域值对象。
- 关闭/缺依赖/业务 Bean 覆盖语义都有定义。
- 每个已知消费者都有状态、负责人和迁移决策；`PRODUCTION`、`UNKNOWN` 不允许无兼容方案的破坏性升级。
- FRM-001 明确记录 ID-002～ID-008 对内部 RC 和公开 GA 的实际约束结果。

### SPLIT-001：产品边界、依赖方向、版本与许可证 ADR

**依赖**：FRM-001

**目标**

冻结 Framework、Tooling、Studio 的职责与依赖方向，防止后续整改继续扩大耦合。

**步骤**

1. 建立模块拆分 ADR，记录现状、目标、替代方案和拆仓时点。
2. 定义 Framework 公开 API、Tooling API、Generator Engine 和 Studio 产品边界。
3. 定义允许依赖：Studio → Tooling → Framework。
4. 定义禁止依赖：Framework → Tooling/Studio、Tooling → Studio。
5. 定义拆仓后 groupId、artifactId、版本和 BOM 归属。
6. 明确 Framework 使用语义化版本，Studio 可使用独立商业版本。
7. 列出 FastDoc、Templates、Analyzer、AI 和商业 License 的归属。
8. 分别决策 Framework、Tooling、Studio 的源码开放范围、许可证、商标和商业授权边界；Framework 暂以 Apache-2.0 为推荐方案，最终选择以权利人确认和 OSS-001 审核为准。
9. 明确内部 RC 不公开、Framework 公开 GA 才公开，以及 RC1 前冻结公共 API 的时序。
10. 增加 Maven/ArchUnit 依赖方向规则设计。

**验收**

- 所有现有模块都有唯一归属。
- 后续任务不需要重新判断代码应该放在哪个仓库。
- 物理拆仓不会要求业务系统修改既有 Maven 坐标。
- Framework 开源与 Studio 商业授权没有被混为同一种“许可证”。
- 开源范围、版权责任人和公开时点都有 ADR 结论。

**禁止**

- 本任务只冻结边界，不移动 Git 目录或创建新仓库。
- 不把未来 1.1 的 Framework 内部能力模块/包结构拆分提前到当前任务。

### FRM-002：独立消费者测试骨架

**目标**

建立不依赖 reactor 意外 classpath 的消费者验证设施。

**步骤**

1. 建立 Maven Invoker、独立 samples 或等价测试目录。
2. 支持复制最小 POM、编译、启动和断言启动结果。
3. 支持按场景排除 optional 依赖。
4. 提供上下文失败断言和日志捕获。
5. 预置 Web、Security、Cache、Event、Monitor、Search 六类空消费者。
6. 将公共构建命令接入 CI，但暂不强制所有场景通过。

**验收**

- 至少一个最小消费者能在独立 classpath 编译和启动。
- 能复现 Web 缺 application 的当前失败。
- 测试失败时保留可诊断日志。

## 7. M1：确定性 P0 修复

### CFG-001：配置契约清单与 Metadata 校验

**目标**

建立代码属性、metadata、模板和文档之间的逐项映射。

**步骤**

1. 枚举全部 ConfigurationProperties 前缀、字段、默认值和约束。
2. 扫描单项目、多端、Archetype、Studio 和文档中的 fastddd 配置。
3. 标记错误前缀、错误层级、错误名称和不存在属性。
4. 对属性类增加必要的 Validated 和约束注解。
5. 增加 metadata 快照测试。
6. 增加旧 fast-ddd 根前缀的启动期错误提示。

**验收**

- 不存在未登记的生产配置项。
- fast-ddd 旧根出现时明确失败。
- metadata 与属性类一致。

### CFG-002：生成配置模板修复

**依赖**：CFG-001

**步骤**

1. 修复单项目和多端模板根前缀与嵌套层级。
2. 修复 SQL 监控阈值属性名称。
3. 删除未实现的 event 和 cqrs 配置，或在对应实现完成后再生成。
4. 删除固定 JWT secret。
5. 添加 YAML 解析和 Environment 绑定测试。
6. 生成四种项目组合并验证零未知属性。

**验收**

- 模板中没有 fast-ddd 根、固定 secret 或未知配置。
- 真实启动时配置全部绑定。

### SPLIT-002：移除 Framework 对 FastDoc 的反向依赖

**依赖**：SPLIT-001、FRM-002

**目标**

消除 fast-ddd-web 对 fastddd-fastdoc-core 的依赖，使 Framework 可以独立构建和发布。

**步骤**

1. 添加依赖方向特征测试，确认当前 Framework → Tooling 依赖。
2. 在 Tooling 中新增 fastddd-fastdoc-spring-boot-starter 或等价模块。
3. 将 FastDoc Controller、FastDoc 自动配置和相关 Web 适配移动到该 Starter。
4. fast-ddd-web 删除 fastddd-fastdoc-core 依赖和 FastDoc 类型引用。
5. Studio 显式引入 FastDoc Starter。
6. 对 FastDoc 启用和未启用消费者分别验证。
7. 增加构建门禁：Framework POM 和 Java import 不得引用 Tooling/Studio 包。
8. 运行 Framework 独立 reactor 构建，不能依赖 Tooling 已编译 classpath。

**验收**

- fastddd-framework 可在不构建 Tooling 的情况下 clean verify。
- FastDoc 功能在 Studio 和显式消费者中保持可用。
- Framework 中不存在 com.gallop.fastddd.fastdoc、codegen、templates 或 studio 引用。

**禁止**

- 不通过把 FastDoc Core 移入 Framework 来维持反向依赖。
- 不删除 FastDoc 功能规避迁移。

### WEB-001：Web optional application 隔离

**目标**

fast-ddd-web 在不包含 fast-ddd-application 时可启动。

**步骤**

1. 添加独立消费者失败测试。
2. 将 CQRS 异常处理移动到独立配置类。
3. 对配置类和相关 Bean 添加 class 条件。
4. 检查所有 optional 依赖是否被非条件代码直接引用。
5. 分别验证有 application 和无 application 两种 classpath。

**禁止**

- 不以删除 CQRS 异常处理作为修复。
- 不把 application 改成必选依赖来掩盖边界问题。

**验收**

- 无 application 时启动成功。
- 有 application 时异常映射仍生效。

### CACHE-001：L1 缓存命名空间隔离

**步骤**

1. 添加两个 cache name 使用相同 key 的失败测试。
2. 为每个 cache name 创建独立 Caffeine Cache，或采用组合内部键。
3. 统一 L1、L2、evict、clear、同步通知和指标命名空间。
4. 增加并发创建、null value、clear 和 Redis 同步测试。
5. 验证不同 value 类型不会串值。

**验收**

- users 与 orders 相同 key 不串值、不误清理。
- 并发和多级缓存测试通过。

### MON-001：SQL 监控属性 Bean 修复

**步骤**

1. 添加最小消费者上下文失败测试。
2. SqlMonitoringProperties 只保留一种注册方式。
3. 移除对固定 Bean 名称的 DependsOn。
4. 对业务已有 MeterRegistry 和配置 Bean 验证退让行为。
5. 使用 conditions 报告确认 Bean 图。

**验收**

- 不再出现 NoUniqueBeanDefinitionException。
- 最小 monitor 消费者可以启动。

### MON-002：SQL 监控真实链路闭环

**依赖**：MON-001

**步骤**

1. 决定使用 Spring 管理的 DataSource Proxy，或实现可靠的 P6Spy bridge。
2. 移除 starter 内会全局生效的 spy.properties，或改为显式启用资源。
3. 将管理接口改为真正的 Actuator Endpoint。
4. 接入 exposure 和权限控制。
5. 默认关闭 SQL 参数明文。
6. 使用 H2/MySQL 执行真实 SQL，断言指标和慢查询记录。
7. 添加密码、Token 和敏感字段脱敏测试。

**验收**

- 真实 SQL 能产生 Micrometer 指标。
- 未暴露 Endpoint 时不可访问。
- 默认日志不包含敏感参数。

### EVT-001：本地事件与 Outbox 装配边界

**步骤**

1. 添加无 DataSource、无 MyBatis 的本地事件消费者测试。
2. 移除 EventAutoConfiguration 对整个 event 包的扫描。
3. 显式声明 event-core Bean。
4. Outbox 配置仅在模式为 outbox 且依赖满足时加载。
5. 使用模块专用 executor 和 scheduler 名称。
6. 本地模式不注册 Outbox Mapper、Repository、Dispatcher。

**验收**

- local 模式无数据库可启动并发布事件。
- conditions 中没有 Outbox Bean。

### EVT-002：Outbox Repository 与 Mapper 修复

**依赖**：EVT-001

**步骤**

1. 修复重载 Mapper statement id 冲突。
2. 为 insert、claim、mark-sent、retry、dead-letter 建立明确方法名。
3. 统一状态枚举和数据库约束。
4. 定义 eventId 唯一约束和幂等键。
5. 增加 MySQL/Testcontainers 映射测试。
6. 验证失败回滚不留下错误状态。

**验收**

- 无 mapper ignored 或 statement 冲突日志。
- 所有状态转换有数据库测试。

### EVT-003：Dispatcher 与 Transport 契约

**依赖**：EVT-002

**步骤**

1. 定义 OutboxTransport SPI。
2. Dispatcher 只负责 claim、调用 transport、状态转换和重试。
3. RocketMQ 实现只负责传输，不自行扫描或创建存储。
4. 无 transport 时启动失败并给出 Failure Analysis。
5. 定义 lease 超时、重试退避、最大次数和死信规则。
6. 修复 FOR UPDATE 未覆盖发送边界的问题。
7. 增加 transport 成功、失败、超时和重复调用测试。

**验收**

- Outbox 不再只入库不投递。
- 失败可重试，超过阈值进入死信。

### EVT-004：Outbox 多实例可靠性验证

**依赖**：EVT-003

**步骤**

1. 建立不依赖数据库、消息中间件和随机 sleep 的确定性竞争测试，使用 barrier/latch、可控时钟和测试 Repository 稳定复现两个 dispatcher 同时 claim。
2. 使用两个应用实例或两个 dispatcher 配合 MySQL Testcontainers 验证真实数据库竞争。
3. 验证同一事件只被一个实例 claim。
4. 验证实例崩溃后 lease 可恢复。
5. 验证 transport 重试不会重复改变业务状态。
6. 验证积压、失败、死信和延迟指标。
7. 形成 Outbox 运维说明和不能自动化验证的可靠性缺口清单。

**验收**

- 不依赖基础设施的确定性竞争测试在 CI 中稳定通过。
- MySQL Testcontainers 多实例竞争、恢复和幂等测试稳定通过，并能生成与提交号关联的测试报告。

### SEC-001：移除默认管理员与固定凭据

**步骤**

1. 删除模板中的 admin/admin 和全权限绑定。
2. 检查 Studio schema、RBAC 初始化脚本、示例配置和历史模板。
3. 开发演示账号移入显式 dev profile。
4. 如保留管理员初始化，要求部署 Secret 或一次性随机密码。
5. 添加模板和构建产物凭据扫描。
6. 文档删除“首次登录后修改即可”的默认口令方案。

**验收**

- 默认生成项目不创建已知管理员。
- 仓库当前模板和发布包无固定 JWT、数据库或 AI Key。

### SEC-002：JWT disabled 完整关闭与安全默认值

**步骤**

1. 添加 disabled 且无 secret 的失败复现。
2. 1.0 保留现有 `enabled` 开关及启用态行为，不新增安全模式枚举。
3. signer、provider、filter、Controller 和 SecurityFilterChain 使用一致的启用条件。
4. disabled 状态不创建 JWT Bean、认证端点和框架 SecurityFilterChain，也不要求 Secret。
5. enabled 状态移除内置 Secret 回退值；缺少 Secret、长度不足或明显为示例值时 fail-fast。
6. 使用 ApplicationContextRunner 和独立消费者覆盖 disabled、enabled、自定义安全链三个场景。
7. 将 resource-server/auth-server 模式、RSA/JWK、Token 撤销存储和认证端点模块化记录为 1.1 ADR 待办。

**验收**

- disabled 模式无 JWT Bean 和认证端点。
- disabled 且无 Secret 的最小消费者可以启动。
- enabled 缺少安全 Secret 时启动失败，并指出准确配置路径。
- 1.0 不发布 resource-server/auth-server 新模式，不改变现有启用态的外部认证契约。

**禁止**

- 不在本任务引入 OAuth2 Authorization Server、完整资源服务器抽象或新的 Token 协议。
- 不以三模式设计扩大 1.0 GA 范围。

## 8. M2：ID 类型系统重构

### ID-001：当前行为特征测试与迁移清单

**目标**

在破坏性修改前锁定当前依赖面和已知错误行为。

**步骤**

1. 扫描 BaseEntity、AggregateRoot、BasePO、VersionedPO 的全部子类。
2. 扫描 Serializable getId 强转、字段隐藏、TableId 和 id-type 配置。
3. 为当前 Long、AUTO、UUID、String ID 建立特征测试。
4. 为 equals/hashCode 可变问题建立失败测试。
5. 为 AbstractBaseController 的 IdType 推测建立边界测试。
6. 输出框架内部和业务系统迁移清单。

**验收**

- 每一种现有 ID 用法都有归类。
- 能明确指出哪些类需要源码迁移。

### SPLIT-003：抽取稳定 Tooling API

**依赖**：SPLIT-001、ID-001

**目标**

为 Studio、Maven Plugin 和未来模板扩展提供稳定契约，避免直接依赖 Generator 内部类。

**步骤**

1. 新建 fastddd-tooling-api，保持为轻量纯 Java 模块。
2. 定义 ProjectGenerator、GenerationRequest、GenerationResult。
3. 定义 TemplateCatalog、DatabaseIntrospector、GenerationProgressListener。
4. 将 IdStrategy、ModelType 等跨产品枚举移入 API，使用 ID ADR 中的最终语义。
5. API 不暴露 FreeMarker、MyBatis Generator、Spring MVC 或 Studio DTO。
6. Generator Engine 实现 Tooling API，并提供兼容适配层。
7. Maven Plugin 通过 Tooling API 调用 Generator。
8. 增加 API 二进制面清单和实现契约测试。

**验收**

- Studio 所需的生成能力都能通过 Tooling API 表达。
- Tooling API 不依赖 Studio、Web、数据库驱动或具体模板引擎。
- Generator 内部包可以变化而不影响 Studio 编译。

**禁止**

- 不把现有 GeneratorConfig 全量原样搬入 API。
- 不为未来未知插件设计复杂扩展协议。

### ID-002：领域身份契约与相等性重构

**依赖**：ID-001

**步骤**

1. 新增 Identifiable<ID>。
2. 新增轻量 DomainEntity<ID>，只负责稳定身份。
3. 移除 BaseEntity 中的 @Data 和基于全部可变字段的 equals/hashCode。
4. AggregateRoot 迁移到新的领域实体基类。
5. 审计字段、逻辑删除和持久化回调从领域基类移除。
6. 定义 ID 创建时机和非空约束。
7. 增加 OrderId 值对象示例。
8. 验证 HashSet、代理类型和相同 ID 的相等性。

**禁止**

- 不在 domain 模块引入 MyBatis 或 Jackson 注解。
- 不让持久化后补 ID 改变 hashCode。

**验收**

- 领域实体无需重复实现 getId/setId。
- 相等性只基于稳定身份和明确类型规则。

### ID-003：PO 显式 ID 与审计模型重构

**依赖**：ID-001

**步骤**

1. 新增 AuditedPO 或等价最小持久化基类，不包含 ID。
2. 评估逻辑删除是否单独由模板生成，而非强制继承。
3. 具体 PO 显式声明 Long、String 或 UUID ID 与 TableId。
4. 乐观锁字段由具体表或模板选择生成。
5. 禁止子类字段隐藏。
6. 删除 BasePO 对全局 id-type 可覆盖字段注解的错误承诺。
7. 准备旧 BasePO 的临时弃用适配，但不形成长期双模型。

**验收**

- PO 的 Java ID 类型与数据库列类型明确。
- 不需要 SerializableTypeHandler 才能读取主键。

### ID-004：Repository 与 Service ID 泛型化

**依赖**：ID-002、ID-003

**步骤**

1. 将 EntityRepository 统一为 E、ID 双泛型。
2. 将 CRUD IService、实现类和扩展接口增加 ID 泛型。
3. removeById、getById、listByIds 等方法使用 ID。
4. 清除框架公共 API 中不必要的 Serializable。
5. 为 Long、String、UUID 和领域 ID 编写编译测试。
6. 检查 MyBatis-Plus 适配层是否需要最小桥接。

**验收**

- 错误 ID 类型在编译阶段失败。
- 业务不再强转 getId。

### ID-005：IdCodec 与 Controller ID 转换

**依赖**：ID-004

**步骤**

1. 定义 IdCodec<ID> 的 parse 和 format 契约。
2. 提供 Long、String、UUID 内置实现。
3. Controller 基类增加 ID 泛型或显式 Codec。
4. 删除根据 MyBatis IdType 猜测 Java 类型的 resolveId。
5. 参数非法时返回 400，不包装成 500。
6. OpenAPI 输出真实 ID schema。
7. 雪花 Long 的 API DTO 显式使用 String。

**验收**

- Long、UUID、业务 String 路径参数转换正确。
- 不合法 ID 返回明确 400。
- 不全局改变普通 Long 字段的 JSON 类型。

### ID-006：框架内部模块和示例迁移

**依赖**：ID-005

**步骤**

1. 迁移 security、event、studio、examples 和测试实体。
2. 迁移 BaseReadOnlyController、BaseAdminCrudController 及其子类。
3. 迁移 EntityMapper 和 Repository 示例。
4. 移除 SerializableTypeHandler 自动注册。
5. 清理父子类重复 id 和 TableId。
6. 运行模块级编译和测试。

**验收**

- 全仓业务 Java 代码无 Serializable id 强转。
- 框架内部实体 ID 类型明确。

### SPLIT-004：Studio 改为 Tooling API 消费者

**依赖**：SPLIT-003、ID-006

**目标**

消除 Studio 对 Generator、Templates 内部实现类和显式内部自动配置的依赖。

**步骤**

1. 盘点 Studio 对 codegen、templates、fastdoc 的直接 import。
2. Studio Service 改为依赖 ProjectGenerator、TemplateCatalog 等 API。
3. 将 Studio DTO 转换为 Tooling API Request，不让 Tooling 依赖 Studio DTO。
4. 用进度监听契约替代对内部生成流程的直接访问。
5. 删除 Studio 对 CodeGeneratorAutoConfiguration 的显式 @Import。
6. Generator Engine 通过标准自动配置或显式 Adapter 注入。
7. 多端构建、AI 设计和数据库反向工程分别增加集成测试。
8. 增加禁止 Studio import Generator internal 包的 ArchUnit 规则。

**验收**

- Studio 只依赖 Tooling API 和公开 Starter。
- 替换 Generator Engine 实现不需要修改 Studio Controller。
- Studio 的生成、下载和进度反馈功能保持可用。

**禁止**

- 不在 Tooling API 中加入 Studio 专用 Web 或数据库实体。
- 不通过继续暴露 Generator 内部包来缩短迁移。

### ID-007：模板、插件、Archetype 与 Studio 迁移

**依赖**：ID-006、SPLIT-004

**步骤**

1. Studio 增加模型类型与 ID 策略的受控组合。
2. 贫血模型模板在具体 PO 生成 id 字段。
3. 充血模型生成领域 ID、PO ID 和 Mapper 转换。
4. 生成 Controller/Service 的 ID 泛型与 Codec。
5. DDL 与 TableId 策略保持一致。
6. 禁止生成父类字段隐藏和 Serializable ID。
7. 更新 Maven Plugin、Archetype 与多端生成链路。
8. 四种模型组合执行真实生成和编译。

**验收**

- 生成项目没有 ID 强转或重复字段。
- Java、DDL、OpenAPI 与选择的 ID 策略一致。

### ID-008：在研业务迁移与旧实现移除

**依赖**：ID-007

**工时口径**：开放式业务迁移项，按消费者逐个估算，不计入 Framework 固定整改总工时。

**步骤**

1. 读取 FRM-001 消费者清单，为每个有效消费者建立 ID 使用与迁移子清单。
2. 先迁移编译类型，再迁移 API DTO 和数据库验证。
3. 核对外部协议是否暴露了内部数据库主键。
4. 运行业务系统编译、测试和启动回归。
5. 对 `DEVELOPMENT`、`TEST_UAT` 消费者完成同步迁移，并记录提交和验收证据。
6. 对 `PRODUCTION`、`UNKNOWN` 消费者禁止直接删除旧契约；建立隔离的兼容适配、弃用版本和删除条件，或把破坏性删除推迟到 1.1。
7. 仅在消费者状态关卡允许时删除旧 BaseEntity、BasePO、SerializableTypeHandler 和旧 resolveId；否则将旧能力限制在明确标记的兼容模块中。
8. 将每个消费者的迁移差异、未关闭风险和后续删除版本记录到 ID 迁移指南。

**验收**

- 每个有效消费者均有“已迁移”或“已批准兼容/延后”的明确结论，不存在未登记消费者。
- 所有纳入 1.0 同步升级范围的业务系统通过编译、测试和启动回归。
- FastDDD 1.0 新公共 API 不再引入旧 ID 模型；若保留兼容层，必须隔离、标记废弃并声明删除版本。

## 9. M3：GA 前公共契约收敛

### SEC-003：安全 Bean 退让、白名单和 CORS

**步骤**

1. SecurityFilterChain、PasswordEncoder、CurrentUserProvider 增加精确退让。
2. 通用默认白名单删除 H2、WebSocket、Studio 等业务路径。
3. CORS 使用 Spring Environment 与 Profiles。
4. 增加业务自定义安全链消费者测试。
5. 验证管理端点和认证端点最小暴露。

**验收**

- 消费者已有安全 Bean 时无冲突。
- prod profile 不因读取方式错误进入开发策略。

### WEB-002：响应包装与 Jackson 显式启用

**步骤**

1. 添加最小 Web 消费者响应契约测试。
2. 响应 envelope 改为独立开关，通用 Web 默认关闭。
3. Long、枚举、日期时间扩展改为独立模块或显式 Customizer。
4. 业务 ObjectMapper 存在时框架退让。
5. 特定管理后台聚合 starter 可显式选择默认开启。

**验收**

- 仅引入 fast-ddd-web 不改变已有 JSON。
- 开启后响应和 OpenAPI 契约一致。

### CACHE-002：CacheManager 退让与组合

**步骤**

1. 添加业务已有 CacheManager 的失败复现。
2. 默认 CacheManager 使用 ConditionalOnMissingBean。
3. 必须并存时使用明确 bean name 和 CacheResolver。
4. 验证 Primary、Spring Cache 注解和多级缓存选择。

**验收**

- 自定义 CacheManager 消费者可启动。
- 缓存选择无歧义。

### SEARCH-001：搜索后端缺失时可诊断

**步骤**

1. 为 SQL、Meilisearch 和 disabled 建立模式。
2. SQL 模式缺少 Mapper 时启动失败并说明原因。
3. 不把 Mapper 配置错误转换为空搜索结果。
4. 修正文档中的“SQL 零配置”表述。
5. Framework 内部搜索能力的物理模块/包结构拆分登记到 1.1，不在本任务大改包结构。

**验收**

- 配置错误和真实零结果可以区分。

### GEN-001：数据源与 Profile 模板修复

**步骤**

1. 保留 Studio 中用户确认的数据源 URL、库名和账号。
2. profile 不重新拼装或覆盖用户输入。
3. 连接池类型显式选择 Druid/Hikari。
4. 生成配置只包含当前连接池有效属性。
5. 使用两种连接池分别生成和启动。

**验收**

- 输入配置与生成配置一致。
- 不出现 Druid 依赖配 Hikari 属性的错配。

### GEN-002：CRUD、DTO 与 Command 生成契约

**依赖**：ID-007、WEB-002

**步骤**

1. 禁止生成 deprecated BaseController。
2. 默认只读 Controller 或 DTO/Command 写接口。
3. PO 不直接作为外部写请求。
4. 生成类级和方法级权限扩展点。
5. 生成审计、校验和数据权限提示。
6. 对敏感示例不生成无权限写端点。

**验收**

- 生成项目无 deprecated API。
- 外部请求不能直接修改 id、deleted、version、审计字段。

### VER-001：产品内版本 Manifest 与跨产品兼容矩阵

**步骤**

1. 定义 Framework 平台 manifest，管理 BOM、Boot、Java、Spring AI、SpringDoc、Knife4j。
2. Framework、BOM 和 Framework Starter 保持锁步版本。
3. Tooling 与 Studio 使用独立 revision，不与 Framework 强制同号发布。
4. 定义机器可读兼容矩阵：Studio → Tooling → Framework。
5. 模板与 Archetype 从兼容矩阵获得目标 Framework 版本，不复制 Studio 自身版本。
6. CI 校验各产品内部版本一致，以及跨产品组合已经进入兼容矩阵。
7. Framework 根 revision 与 Framework BOM revision 同步。
8. Framework 保留 1.0.0-SNAPSHOT，RC 时只统一切换 Framework 产品线。

**验收**

- Framework 产品线不存在多个默认版本来源。
- Tooling 与 Studio 能独立升级，但不兼容组合会被 CI 阻断。
- 生成 dependency tree 与目标 Framework BOM 一致。

### SPLIT-005：Studio Server 依赖隔离与独立构建

**依赖**：SPLIT-004、ID-007、VER-001

**目标**

在仍处于单仓库时，让 Studio Server 具备迁出 reactor 后独立构建的能力，为安全物理拆仓和公开 Framework 仓库做准备；UI、部署包和商业发布工程不属于本任务。

**步骤**

1. 为 Studio 建立独立 parent POM，不再继承 FastDDD 根 parent。
2. Studio 通过 fastddd-bom 管理 Framework 依赖，通过 Tooling BOM 或明确版本管理 Tooling。
3. Studio 使用独立 revision，不与 Framework 强制锁步。
4. 从空本地 Maven 仓库验证 Studio Server 只依赖已发布 Framework/Tooling 制品即可构建、测试和启动。
5. 建立兼容矩阵：Studio → Tooling → Framework。
6. 在根 reactor 中保留临时聚合方式，但禁止 parent 继承和源码 classpath 泄漏。

**验收**

- Studio Server 可在隔离 Maven 本地仓库独立构建和启动。
- Studio 不依赖 Framework 根 POM 属性。
- 删除根 reactor 聚合后，Studio Server 仍能通过已发布的内部 RC 制品构建。

**禁止**

- 不在本任务创建正式商业仓库。
- 不在本任务处理 UI lockfile、镜像、离线包、商业许可证或客户部署流程。
- 不使用 Maven 动态版本范围替代兼容矩阵。

## 10. M4：文档、质量门和发布

### DOC-001：参考手册、AI 契约与迁移指南

**产物**

1. 修订 FASTDDD_REFERENCE.md。
2. 新增 FASTDDD_AI_CONTRACT.md，控制在 AI 可快速读取的范围。
3. 新增 ID_MIGRATION_GUIDE.md。
4. 更新兼容矩阵和 Release Checklist。

**AI 契约必须包含**

- 贫血/充血模型选择。
- ID 策略标准写法。
- 禁止 Serializable id、字段隐藏、固定 secret 和直接 PO 入参。
- 推荐 SPI 和覆盖方式。
- 每次开发必须执行的验证。

**验收**

- 所有 Java、YAML、Maven 示例可编译或解析。
- 不包含代码中不存在的注解、属性和端点。

### QA-001：分阶段独立消费者验收矩阵

必须覆盖：

| 场景 | 预期 | 首次阻断门禁 |
|---|---|---|
| Web 无 application | 启动成功 | 内部 RC |
| JWT disabled | 无 secret 启动成功 | 内部 RC |
| 自定义 SecurityFilterChain | 框架退让 | 内部 RC |
| 自定义 CacheManager | 框架退让 | 内部 RC |
| 两个 cache name 同 key | 完全隔离 | 内部 RC |
| Event local 无数据库 | 启动并发布 | 内部 RC |
| Outbox 无 transport | 明确启动失败 | 内部 RC |
| Outbox 两实例 | claim、重试、恢复正确 | 内部 RC |
| Monitor | 真实 SQL 产生指标 | 内部 RC |
| Search 配置缺失 | 明确诊断 | 内部 RC |
| Long/UUID/String ID | 编译、路由、OpenAPI 正确 | 内部 RC |
| 强类型领域 ID | Domain/PO 映射正确 | 内部 RC |
| 贫血/充血、单端/多端生成 | 编译和启动成功 | 内部 RC |
| 生成安全基线 | 无默认账号、固定 secret、未知配置 | 内部 RC |
| Framework 独立构建 | 不需要 Tooling 或 Studio reactor | 内部 RC |
| Tooling API/Engine | 只依赖候选 Framework/BOM | 内部 RC |
| `fastddd-studio-server` 已知消费者 | 使用候选 Framework/Tooling 完成编译、测试和上下文启动 | 内部 RC |
| Studio Server 隔离构建 | 只依赖已发布 Framework/Tooling 制品 | Framework 公开 GA |
| `fastddd-studio-server` 真实跨仓消费者 | 使用隔离 Maven 本地仓库完成编译、测试、可执行 Jar 启动和健康检查 | Framework 公开 GA |

QA-001 定义完整矩阵；QA-002 在内部 RC 阶段执行“内部 RC”行，SPLIT-007 在拆仓后执行“Framework 公开 GA”行。后置行尚未到门禁时不得伪造通过，也不得因此阻塞内部 RC。

### QA-002：全量回归、安全与依赖门禁

**步骤**

1. JDK 21 下 clean verify。
2. 运行 QA-001 中“内部 RC”消费者矩阵。
3. 在 CI 运行不依赖基础设施的确定性 Outbox 竞争测试。
4. 在本地或发布验证环境运行 MySQL Testcontainers Outbox 多实例测试并保存报告。
5. 运行真实 MySQL、Redis、RocketMQ 可用场景；不可用项进入可靠性缺口清单。
6. 检查 dependency tree、BOM 对齐和 optional 边界。
7. 运行凭据、依赖和静态安全扫描。
8. 生成单端和多端项目并执行 Maven 构建与启动。
9. 以 `fastddd-studio-server` 作为具名已知消费者，使用候选 Framework/Tooling 制品完成构建、测试和上下文启动。
10. 检查文档链接和示例。
11. 对全部高风险任务执行最终对抗性 review。

**验收**

- 无跳过本轮新增的关键测试。
- 无通过文档规避的已知内部 RC 阻断问题。
- Outbox CI 确定性测试和本地 Testcontainers 报告均可追溯到当前候选提交。
- 每个仅本地验证或未自动化场景均记录风险、负责人、补测期限、是否阻断内部 RC/公开 GA 和接受人。
- QA-001 中标记为“Framework 公开 GA”的后置场景已有 SPLIT-007 责任人和执行入口，未被误报为内部 RC 已通过。

### OSS-001：Framework 开源就绪与公开前安全门禁

**依赖**：SPLIT-001、SEC-001、SEC-002、QA-002

**目标**

把 Framework 开源所需的版权、许可证、社区治理、安全披露、供应链和 Maven 发布元数据变成独立可验收工作流；本任务完成的是开源就绪，实际公开动作由 REL-002 执行。

**步骤**

1. 盘点代码权利人、提交历史、复制或改写的第三方代码、文档、图片、模板和测试资源，记录来源与许可证明。
2. 由权利人确认 Framework 最终开源许可证；Apache-2.0 是推荐方案，不以 Agent 或扫描工具结论替代法律确认。
3. 增加并审核 `LICENSE`、`NOTICE`、`CONTRIBUTING.md`、`SECURITY.md` 和 `CODE_OF_CONDUCT.md`。
4. 根 POM 补充 `name`、`description`、`url`、`licenses`、`scm`、`developers` 或组织信息，并确保 flatten 后发布 POM 保留必要元数据。
5. 建立 Maven 依赖许可证扫描和第三方许可证清单；分别核对源码分发、二进制分发和容器中实际包含的内容，不只检查直接依赖。
6. 对 Hutool/MulanPSL-2.0 及所有双许可证依赖明确选择的许可分支和必要归属信息；未知、冲突或缺失许可证必须人工处置。
7. 生成 Framework SBOM、源码包、Javadoc、签名与校验和，并检查 LICENSE/NOTICE 是否进入相应发布物。
8. 扫描当前工作树和 Git 历史中的密码、Token、私钥、AI Key、客户信息与内部地址；发现真实凭据时先轮换，再决定历史清理方式。
9. 在 SEC-001、SEC-002 和全部安全 P0 关闭后执行独立公开前安全 review，输出不含可利用细节的脱敏结论和未关闭风险。
10. 定义 SECURITY.md 中的支持版本、私密报告渠道、响应流程、披露时限和 CVE/公告责任人。
11. 建立开源发布检查清单，但不在本任务公开仓库、Tag 或 Maven 制品。

**验收**

- 权利人、许可证选择和所有第三方内容来源可追溯，没有未处置的未知许可证。
- 源码包、二进制包和发布 POM 的许可证、NOTICE、SCM 与 SBOM 信息一致。
- SEC-001、SEC-002、安全 P0、凭据扫描和公开前安全 review 全部有证据。
- SECURITY.md 提供可用的私密漏洞报告流程，不要求研究者公开披露未修复漏洞。
- 开源检查清单能由 REL-002 在物理拆仓后重放。

**禁止**

- 不把许可证扫描报告当作法律意见或版权归属证明。
- 不在安全问题修复前公开攻击细节、真实凭据或内部部署信息。
- 不因 OSS-001 完成而提前公开仍包含 Studio Pro 源码的当前仓库。

### REL-001：内部 RC 候选与 API 冻结决策

**步骤**

1. 确认 7 个 P0、ID 架构门禁和内部 RC 必做 P1 全部关闭。
2. 确认在研业务系统升级结果。
3. 确认 Framework、BOM、Starter 版本一致，Studio/Tooling 兼容矩阵已经冻结。
4. 确认 OSS-001 已达到开源就绪，但当前仓库、Tag 和制品仍保持私有。
5. 将 Framework 发布 revision 统一切换到 1.0.0-RC1；Studio 保持独立版本。
6. 确认不依赖基础设施的确定性 Outbox 竞争测试已作为 CI 阻断门禁通过。
7. 确认本地或发布验证环境的 MySQL Testcontainers 多实例测试通过，并记录提交号、JDK、Docker/Testcontainers 和数据库镜像版本及测试报告位置。
8. 汇总“仅本地验证/可靠性缺口清单”；每项必须包含影响、剩余风险、负责人、补测期限、公开 GA 阻断结论和风险接受人。
9. 执行内部 RC Release Checklist，只发布到私有 Maven 仓库。
10. 冻结公共 Java API、配置、SPI、Maven 坐标和 ID 契约；RC 后任何破坏性变化都必须产生新的 RC，不得直接进入 GA。
11. 输出包含消费者状态关卡、OSS 就绪、Outbox 三层证据和未关闭缺口的内部 RC go/no-go 记录。
12. RC 稳定后进入 M5，不在本任务公开源码或发布 1.0.0 GA。

**go/no-go 规则**

- 确定性 Outbox 竞争测试未进入 CI 或结果不稳定：no-go。
- MySQL Testcontainers 证据无法追溯到当前候选提交：no-go。
- 可靠性缺口被标记为内部 RC 阻断，或缺少负责人、期限、接受人：no-go。
- “仅本地验证”是暂时的证据分类，不得作为跳过验证的理由。
- OSS-001 未完成，或公共 API 未形成可追溯冻结清单：no-go。

**禁止**

- 不因临近发布时间跳过消费者测试。
- 不把旧 SNAPSHOT 临时坐标发布到正式私服。
- 不把内部 RC 发布到公开 Maven 仓库、公开 Git 仓库或公开下载地址。
- 不在 RC1 之后直接修改 GA 公共 API；必须回到 RC2 重新验证。

## 11. M5：物理拆仓、公开 GA 与商业交付

M5 在 Framework 1.0.0-RC1 形成后执行。它不阻断 Framework 内部 RC，但同时阻断 Framework 公开 GA 和 Studio 商业发布。

### SPLIT-006：Framework、Tooling、Studio 物理拆仓

**依赖**：REL-001、SPLIT-005

**目标**

在公共契约稳定后，将逻辑边界转换为独立 Git 仓库和独立发布单元。

**步骤**

1. 创建 fastddd-framework、fastddd-tooling、fastddd-studio-pro 三个目标仓库；初始均保持私有，Framework 只由 REL-002 执行公开。
2. 使用保留历史的迁移方式拆分目录；记录原 commit 与新仓 commit 映射。
3. Framework 仓包含 Framework、BOM、消费者测试和基础文档。
4. Tooling 仓包含 Tooling API、Generator Engine、Templates、FastDoc Starter 和 Maven Plugin。
5. Studio 仓包含 Server、UI、数据库迁移、部署和商业产品文档。
6. 保持既有 groupId 和 artifactId；只改变源码仓库与发布流水线。
7. 为三个仓库分别建立 parent POM、revision、CI 和发布配置。
8. 删除跨仓 relativePath、源码目录引用和 reactor 隐式依赖。
9. 原仓库进入只读归档或转换为聚合说明仓库，避免双写。
10. 为拆仓过程准备可恢复备份和回滚点。
11. 检查 Framework 仓不含 Studio Pro 私有源码、商业许可证实现、客户数据、密钥或内部部署资料。

**验收**

- 三个仓库分别从空工作区 clone 后可以独立构建。
- 没有跨仓 relativePath、复制源码或依赖未发布 SNAPSHOT。
- Git 历史和许可证归属可追溯。
- 原 Maven 坐标保持不变。
- Framework 目标仓已经满足“可公开但尚未公开”，Studio/Tooling 的可见性符合 SPLIT-001 ADR。

**禁止**

- 不在拆仓时同时重命名包、artifactId 或公共 API。
- 不用复制后长期双写代替正式迁移。
- 不在客户使用中的 release 分支上直接进行不可恢复拆分。

### SPLIT-007：跨仓发布、兼容矩阵与可靠性验证

**依赖**：SPLIT-006

**目标**

证明三个独立仓库可以按照真实发布顺序构建，并完成 Framework 公开 GA 前的跨仓消费者、供应链和可靠性验证；Studio 商业交付由 STUDIO-001 负责。

**步骤**

1. 发布 Framework RC 和 BOM 到测试 Maven 仓库。
2. Tooling 仅使用已发布 Framework 制品构建、测试和发布。
3. Studio Server 仅使用已发布 Framework/Tooling 制品构建、测试、启动和执行健康检查。
4. 建立跨仓集成流水线和机器可读兼容矩阵。
5. 从空 Maven 缓存执行 Framework、Tooling 和 Studio Server 可重复构建，完成 QA-001 的“Framework 公开 GA”消费者场景。
6. 在跨仓 CI 中继续执行不依赖基础设施的确定性 Outbox 竞争测试，不因拆仓降级为手工验证。
7. 有 Docker runner 时执行 MySQL Testcontainers 多实例验证；暂无 runner 时，关联当前 Framework RC 提交的本地报告，并把“仅本地验证”项原样带入公开 GA 风险清单。
8. 逐项复核 REL-001 可靠性缺口的负责人、期限、证据和阻断结论；跨仓迁移不得重置或隐去旧缺口。
9. 生成并检查三个仓库各自的 SBOM 和第三方许可证清单；Framework 最终 LICENSE/NOTICE 只反映其实际源码与发布物。
10. 对 Framework 公开候选执行空环境 clone、构建、源码包、Javadoc、签名、校验和和发布 POM 核验。

**验收**

- 跨仓发布顺序可自动执行并可重放。
- 兼容矩阵能阻止不兼容组合进入发布。
- `fastddd-studio-server` 作为具名跨仓消费者通过隔离 Maven 仓库验证。
- Outbox 确定性 CI、本地 Testcontainers 证据和可靠性缺口清单在拆仓后仍连续可追溯。
- Framework 公开候选不依赖其他源码仓库或开发机本地缓存。

**禁止**

- 不把未发布 SNAPSHOT 或本地仓库制品带入公开 GA 候选。
- 不用“仅本地验证”掩盖缺失的 CI 能力或清空可靠性风险。
- 不在本任务执行 Studio 商业许可证、镜像或客户试点。

### REL-002：Framework 开源公开与 1.0.0 GA 发布

**依赖**：OSS-001、SPLIT-007

**目标**

将已经稳定的内部 RC 从独立 Framework 仓库提升为公开 1.0.0 GA；公开动作本身必须可审计、可停止、可重放。

**步骤**

1. 比较 GA 候选与最后一个通过验证的内部 RC；除版本提升和批准的发布元数据外，不允许公共 API、依赖或行为差异。
2. 在物理拆分后的 Framework 仓重放 OSS-001 开源清单、许可证扫描、凭据历史扫描、公开前安全 review 和发布物检查。
3. 确认 LICENSE、NOTICE、CONTRIBUTING.md、SECURITY.md、CODE_OF_CONDUCT.md、SBOM、第三方许可证和 Maven POM 元数据完整。
4. 确认公开 Git 历史不包含 Studio Pro 源码、客户信息、有效密钥或不能公开的内部资料。
5. 将 revision 从最后一个 RC 提升为 1.0.0，执行全量构建、消费者矩阵和签名验证。
6. 先创建受保护的 1.0.0 Tag 和发布候选记录，再按批准顺序公开 Git 仓库、发布 Maven 制品和发布说明。
7. 发布兼容矩阵、迁移指南、支持版本、安全报告渠道和已知限制。
8. 记录公开时间、Tag、制品校验和、仓库权限、Maven 发布结果、审批人和回滚/补丁决策。
9. 将 1.0.0 GA 制品保存为后续二进制兼容基线；从公开时刻起外部消费者按 `UNKNOWN` 存在，1.0.x 只允许兼容修复，不再删除公共契约。

**验收**

- 公开 Git Tag、源码、Maven POM、二进制制品和 SBOM 指向同一提交与版本。
- GA 公共契约与已验证 RC 一致，没有在 GA 中直接引入破坏性 ID 或配置变化。
- 1.0.0 GA 二进制基线已归档，后续 1.0.x 兼容门禁具备可比较输入。
- Framework 仓库和制品不包含 Studio Pro 私有内容或有效凭据。
- LICENSE/NOTICE、漏洞报告入口、第三方许可证和签名可由外部用户访问并验证。

**go/no-go 规则**

- OSS-001 或 SPLIT-007 任一证据过期、缺失或无法对应当前提交：no-go。
- GA 候选相对已验证 RC 存在未重新走 RC 的公共契约变化：no-go。
- 存在未处置许可证、有效凭据、公开安全 P0 或无法接受的可靠性阻断项：no-go。

**禁止**

- 不先公开仓库再补 LICENSE、NOTICE、SECURITY 或清理历史凭据。
- 不以删除公开 Tag 或覆盖制品作为常规回滚手段；发布后问题通过补丁版本和安全公告处理。
- 不在 1.0.x 中以“清理旧代码”为名删除 GA 已公开的 Java API、配置、SPI 或 Maven 坐标。

### STUDIO-001：Studio UI、部署包与商业发布工程

**依赖**：REL-002

**目标**

在 Framework 公开 GA 和跨仓链路稳定后，完成 Studio Pro 自身的 UI、部署、商业许可证和付费试点交付；本任务不得反向改变 Framework 公共契约。

**步骤**

1. UI 从 0.0.0 切换为正式产品版本，建立独立 lockfile、构建和 CI。
2. 明确 UI 静态资源由独立镜像还是 Studio Server 打包交付。
3. 建立 Studio Server、UI、数据库迁移、镜像/离线包和文档的统一发布清单。
4. 生成并检查 Studio 自身的 SBOM、第三方 NOTICE、商业组件清单和客户授权边界。
5. 构建并签名 Studio Docker 镜像或离线交付包，不包含未发布 SNAPSHOT。
6. 验证数据库初始化、升级、备份、恢复和回滚。
7. 验证商业许可证缺失、有效、过期和宽限期行为；许可证到期不得阻止客户读取、导出或备份已有数据。
8. 使用 1 个内部环境和至少 1 个隔离试点环境完成安装、升级和回滚演练。
9. 输出 Studio 付费试点 go/no-go 记录。

**验收**

- Studio Server 与 UI 可独立构建，客户交付物不依赖源码仓库或开发机缓存。
- 镜像/离线包、数据库迁移、备份恢复、许可证和数据导出验证通过。
- Studio 兼容矩阵指向已公开的 Framework 1.0.0 GA 和已发布 Tooling 版本。
- Studio 付费试点 go/no-go 通过。

**禁止**

- 不以代码混淆代替许可证、合同和供应链治理。
- 不让 Studio 发布失败阻塞或回滚已经公开的 Framework GA。
- 不为 Studio 商业需求在 1.0.x 中破坏 Framework 公共 API。

## 12. 工作量估算

| 工作流 | 估算 |
|---|---:|
| M0 治理、产品边界与测试骨架 | 4–6 人日 |
| M1 确定性 P0 与反向依赖清理 | 16–27 人日 |
| M2 ID、Tooling API 与 Studio 消费者化（不含 ID-008） | 15–25 人日 |
| ID-008 在研业务迁移 | 开放式；按消费者单独估算，不计入固定合计 |
| M3 公共契约与 Studio Server 依赖隔离 | 9–15 人日 |
| M4 文档、消费者验证、OSS 就绪与内部 RC | 10–18 人日 |
| M5 物理拆仓、跨仓验证、公开 GA 与 Studio 商业交付 | 14–26 人日 |
| 固定整改合计 | 68–117 人日，另加 ID-008 |

说明：

- 可由 2～3 名执行者并行，但 ID 与 Tooling API 必须保持单一架构决策。
- Event/Outbox、SQL 监控、业务系统 ID 迁移和拆仓发布流水线是工期波动最大的部分。
- 固定估算包含框架测试和 review，不包含 ID-008 各业务系统的迁移与功能回归工期。
- ID-008 在 FRM-001 消费者清单冻结后，按“编译类型迁移 + API/DTO 迁移 + 数据验证 + 功能回归”逐系统估算和排期，不预设总上限。
- 这是中长期分阶段项目，不应作为一次普通“GA 前清理”整体并行开工；每个里程碑必须独立 go/no-go。
- M5 不阻断 Framework 内部 RC，但阻断 Framework 公开 GA 和 Studio 商业试点。

## 13. 提交与 Review 策略

### 13.1 提交原则

- 一任务一提交或一组紧密子任务一提交。
- 使用 Conventional Commits，描述使用中文。
- 不把测试关闭、临时兼容和正式实现混在同一提交。

示例：

- `test: 建立 FastDDD 独立消费者测试骨架`
- `refactor(model): 重构领域实体身份契约`
- `refactor(crud): 将 CRUD 服务改为强类型 ID`
- `fix(cache): 隔离不同缓存名称的本地缓存空间`
- `fix(event): 拆分本地事件与 Outbox 自动配置`
- `fix(security): 完善 JWT 关闭语义与安全默认值`
- `docs: 新增 FastDDD ID 迁移与 AI 开发契约`

### 13.2 强制 Review 点

以下任务不得实现后直接标记完成：

- ID-002～ID-008。
- EVT-001～EVT-004。
- SEC-001～SEC-003。
- MON-002。
- GEN-002。
- REL-001。
- OSS-001、REL-002、STUDIO-001。
- SPLIT-001～SPLIT-007。

Review 至少检查：

- 公共 API 是否意外扩大。
- 自动配置是否对业务 Bean 退让。
- 是否用静默降级掩盖配置错误。
- 是否新增固定凭据或宽泛白名单。
- 是否把数据库主键错误暴露为跨系统业务身份。
- 是否存在只测框架内部、不测消费者 classpath 的假阳性。
- 是否重新引入 Framework → Tooling/Studio 反向依赖。
- 是否依赖 reactor、relativePath 或本地 SNAPSHOT 才能构建。
- 是否把内部 RC、Framework 公开 GA 和 Studio 商业发布三套门禁混为一谈。
- 是否遗漏许可证、版权、凭据历史、SBOM、签名或公开安全披露要求。

## 14. Task Master 导入建议

当前仓库未初始化 .taskmaster。本方案可直接交给 Claude Code 按任务执行；如果后续需要 Task Master 持久化状态，建议：

1. 将第 5 节一级任务作为父任务导入。
2. 将每个任务的“步骤”展开为子任务。
3. 将“验收”写入 testStrategy。
4. 保留本方案中的依赖关系，不让生成工具重新推断架构顺序。
5. 导入后先执行依赖校验和架构 review，再开始第一个任务。
6. 每个高风险父任务增加独立 review 子任务。

Task Master 状态不得超前于代码证据：只有骨架、空接口或字符串测试时不能标记 done。

## 15. 完成定义

### 15.1 Framework 内部 RC 完成

必须同时满足：

1. 审计报告 7 个 P0 全部关闭并有回归测试。
2. 新公共 API 不再以 Serializable 表达 ID；如果消费者状态门禁要求兼容层，旧契约必须隔离、标记废弃并声明删除版本。
3. BaseEntity 不再混合持久化审计和逻辑删除职责。
4. PO、DDL、Service、Controller、OpenAPI 的 ID 类型一致。
5. 模板和 Studio 不生成旧 ID 模式。
6. 自动配置可关闭、可退让、可诊断。
7. 生成项目无默认管理员、固定 secret、未知配置和 deprecated Controller。
8. QA-001 中“内部 RC”消费者矩阵通过，`fastddd-studio-server` 作为具名已知消费者完成候选制品回归。
9. 消费者生产状态关卡完成；所有纳入 RC1 同步升级范围的业务系统通过迁移验证，生产或未知消费者具有已批准的兼容/延后决策。
10. AI 只需读取简短开发契约，不需要依赖长篇避坑说明才能编写业务代码。
11. Framework 产品内版本一致，Tooling/Studio 与 Framework 的兼容矩阵可验证。
12. Framework 不再依赖 Tooling 或 Studio，Studio 只通过 Tooling API 使用生成能力。
13. Outbox 的 CI 确定性竞争测试、本地 Testcontainers 报告和可靠性缺口清单满足 REL-001 go/no-go 规则。
14. OSS-001 已完成开源就绪和公开前安全门禁，但仓库、Tag 和 RC 制品仍保持私有。
15. REL-001 go/no-go 通过，公共 API、配置、SPI、坐标和 ID 契约已经冻结。

### 15.2 Framework 公开 GA 完成

在内部 RC 完成基础上还必须满足：

1. Framework、Tooling、Studio 已物理拆仓，Framework 公开仓不包含 Studio Pro 私有内容。
2. Framework、Tooling 和 Studio Server 从空环境只使用已发布制品完成跨仓构建。
3. QA-001 中“Framework 公开 GA”消费者矩阵通过，`fastddd-studio-server` 完成隔离 Maven 仓库构建、启动和健康检查。
4. 跨仓 CI 保留 Outbox 确定性测试；本地 Testcontainers 报告和可靠性缺口连续可追溯。
5. OSS-001 在物理拆分后的 Framework 仓重放通过，LICENSE、NOTICE、SECURITY、CONTRIBUTING、SBOM、第三方许可证和 Maven 元数据与实际发布物一致。
6. GA 候选与最后一个已验证 RC 公共契约一致；若有破坏性变化，已经重新进入 RC 验证。
7. Git Tag、源码、发布 POM、二进制制品、签名、校验和和 SBOM 指向同一版本与提交。
8. REL-002 go/no-go 通过，Framework 仓库和 1.0.0 制品已经按批准顺序公开。

### 15.3 Studio 商业交付完成

在 Framework 公开 GA 完成基础上还必须满足：

1. Studio Server 与 UI 具备独立版本、parent、lockfile、构建和发布流水线，不依赖原根 reactor。
2. 跨仓发布只使用已发布 Framework/Tooling 制品，无本地 SNAPSHOT 泄漏。
3. Studio 的兼容矩阵、SBOM、第三方 NOTICE、签名镜像或离线包完整。
4. 安装、数据库升级、备份、恢复、回滚、商业许可证和数据导出验证通过。
5. 许可证到期不阻止客户读取、导出或备份已有数据。
6. STUDIO-001 付费试点 go/no-go 通过。

## 16. 执行记录

| 日期 | 任务 | 状态 | 提交/分支 | 验证结果 | 遗留风险 |
|---|---|---|---|---|---|
| 2026-08-11 | FRM-000 | ✅ 已完成 | 分支 `refactor/fastddd-1.0-framework` ← `upgrade/jdk21-boot3.5.16` @ 740da39；提交 `docs: 建立 FastDDD 1.0 架构整改执行基线` | reactor 27/27 SUCCESS；测试 364 run / 0 failures / 0 errors / 6 skipped；archetype(ANEMIC, 3.5.16) 生成 + 生成项目 `clean compile` BUILD SUCCESS（详见 §16.1） | 3 个 carry-over 文件随基线纳入版本控制（§16.1.E）；invoker javadoc 子构建噪声非致命（§16.1.D） |
| 2026-08-11 | FRM-001 | ✅ 已完成 | 提交 `docs(arch): 冻结 FastDDD 1.0 四域 ADR 与消费者清单 (FRM-001)` | 4 份 ADR + consumers.yaml 冻结；ID go/no-go=GO（详见 §16.2） | 方案 §6 验收"ID-002～ID-008"笔误（实际只到 ID-006），已登记；go/no-go 对"UNKNOWN 不阻塞"的偏离已显式记录，待用户 review 可回退 |
| 2026-08-11 | FRM-002 | ✅ 已完成 | 提交 `test(consumer): 独立消费者测试骨架 + P0-2/P0-4 隔离复现 (FRM-002)` | 新增 `fastddd-consumer-tests` 模块（maven-invoker 3.9.0）；默认 gated `web-*` Passed:2/Failed:0；全量 `-Dinvoker.test=*` Passed:4/Failed:3；monitor-minimal 隔离复现审计 P0-4（详见 §16.3） | `-Pconsumer-tests` 装饰性警告（profile 在子模块非根，已实证实际生效）；3 个 ungated 失败登记到 CACHE-001/EVT/MON-001 |
| 2026-08-11 | SPLIT-001 | ✅ 已完成 | 提交 `docs(arch): 冻结三层产品边界与依赖方向 ADR (SPLIT-001)` | ADR-005 冻结 Framework/Tooling/Studio 三层边界 + 依赖方向 + 坐标/版本/许可证归属（详见 §16.4） | `fast-ddd-web→fastdoc-core` 反向依赖移交 SPLIT-002；Tooling/Studio 许可证为推荐结论待 OSS-001 终审 |
| 2026-08-11 | CFG-001 | ✅ 核心完成 | 提交 `docs(cfg): 配置契约清单与错配分析 (CFG-001 步骤1-3)` f04e793；`feat(cfg): fast-ddd.* 旧根启动期 fail-fast guard (步骤6)` 94ef8a4；`feat(cfg): 优先属性类补声明式数值约束 + 绑定期校验单测 (步骤4)` ab187e3 | 配置契约单一事实源（16 前缀/14 类字段/错配分析）；`fast-ddd.*` 旧根启动期 fail-fast guard（fast-ddd-config，经 starter 传递，3 单测）；2 优先类补 `@Validated` 数值约束 + 绑定期校验实证（cache 4 / code 5 用例）（详见 §16.5） | 步骤4 残留扩展：Duration 字段需自定义 `@PositiveDuration`（独立小任务）+ 其余类按需补；步骤5 metadata 快照低优先；未绑定配置（cqrs/event）+ id-type 移交 CFG-002 |
| 2026-08-11 | CFG-002 | ✅ 已完成 | 提交 `fix(cfg): 修复生成模板配置契约 + 渲染期校验 (CFG-002)` eb3ef0c | 3 生成模板（单项目/多端/archetype）修复：根前缀 `fast-ddd→fastddd`、删除未绑定 cqrs/event、JWT secret 改环境变量、`slow-threshold→slow-query-threshold`、多端 http/cors 嵌套修正、`id-type auto→assign_id`；新增 `ProjectConfigContractTest`（snakeyaml 渲染期键路径契约，3 用例）+ archetype 生成冒烟实证（详见 §16.6） | DB `your_password` 占位符（dev 便利，非功能凭据）保留；event/cqrs 真实绑定待 EVT/ADR-003；CFG-002 步骤6 的"4 组合"以单组合冒烟 + 契约测试（覆盖 ANEMIC/RICH）替代，全组合留 QA-001 |
| 2026-08-11 | SPLIT-002 | ✅ 已完成 | 提交 `refactor(split-002): 移除 Framework 对 FastDoc 反向依赖，FastDoc HTTP 适配上移至 Tooling Starter (SPLIT-002 #38)` 2c1da8a | 新建 `fastddd-fastdoc-spring-boot-starter`（Tooling）；fast-ddd-web 移除 fastdoc-core 依赖 + 删除嵌套 FastDocConfiguration/Controller；框架级源码扫描门禁 `FrameworkLayeringDirectionTest`；fastdoc-minimal 消费者实证装配（详见 §16.7） | 框架子模块 parent=root 致聚合器级 enforcer 无法继承，门禁改用测试级源码扫描（持久 per-submodule enforcer 门禁留 OSS-001）；顺带修正原 web 嵌套配置的 `@ConditionalOnBean(DataSource)` 排序存量缺陷 |
| 2026-08-11 | WEB-001 | ✅ 已完成 | 提交 `refactor(web-001): 隔离 fast-ddd-web 对 fast-ddd-application 的 optional 硬依赖 (WEB-001 #39)` fad9661 | 新建 `CqrsExceptionAdvice`（@ConditionalOnClass）抽离 CommandValidationException 处理；GlobalExceptionHandler 瘦身为 application-free；web-without-application 特征测试翻转为正向 contextLoads；web-minimal 加强 CqrsExceptionAdvice 装配断言（详见 §16.8） | CommandController/QueryController（abstract）仍 import application 包，属组件扫描 ASM 不触发加载的软耦合（实证可启动）；GlobalExceptionHandlerTest 类名历史遗留未改（行为已验证） |
| 2026-08-11 | CACHE-001 | ✅ 已完成 | 提交 `fix(cache-001): L1 缓存按 cacheName 命名空间隔离 + Redis 类泄漏修复 (CACHE-001 #40)` 76e34a4 | CacheRegistry 每 cacheName 独占 CaffeineLocalCache（对象级命名空间隔离）；抽离 CacheSyncPublisher 核心接口解除核心层对 RedisCacheSyncListener→MessageListener 的硬引用；新增 CacheNamespaceIsolationTest（5 用例）；cache-minimal 消费者升级为正向验收（详见 §16.9） | Redis 跨节点同步完整集成测试（需 broker）留 QA-001；defaultLocalCache 单例降级为启用标记（内部缓存不再共享）；metrics 层面为空白（模块无指标类，非缺陷） |
| 2026-08-11 | MON-001 | ✅ 已完成 | 提交 `fix(monitor-001): SqlMonitoringProperties 单一注册 + 移除 @Component/@DependsOn (MON-001 #41)` | 移除 SqlMonitoringAutoConfiguration 的 `@Bean sqlMonitoringProperties()`（P0-4 双注册根因，保留 @EnableConfigurationProperties 单一方式）；SqlMetricsCollector/SqlMonitoringMessageFormatter/SqlMonitoringService 移除 @Component/@Service 刻板注解（消除"组件扫描+AutoConfig"双注册同模式隐患）；移除 @DependsOn 固定 Bean 名；三个 @Bean 补 @ConditionalOnMissingBean；新增 SqlMonitoringAutoConfigurationTest（4 用例 ApplicationContextRunner）；monitor-minimal 翻转为正向验收（详见 §16.10） | MeterRegistry 多候选精细退让（@ConditionalOnSingleCandidate）经实证会令 actuator+prometheus 消费者整体退让、SQL 监控永不装配，故不采用，移交 MON-002；SqlMonitoringController 仍为 @RestController（无 AutoConfig @Bean），暴露链路留 MON-002 |
| 2026-08-12 | MON-002 | ✅ 已完成 | 提交 `fix(monitor-002): SQL 监控真实链路闭环 — Spring 管理 JDK DataSource 代理 + 真正 Actuator Endpoint + 脱敏 (MON-002 #43)` | 移除 p6spy 依赖/spy.properties/SqlMonitoringMessageFormatter（双实例根因）/SqlMonitoringController（伪 @RestController）；改为 Spring 管理 JDK `DataSource` 动态代理（BeanPostProcessor 包装 → SqlMonitoringJdbcProxy → SqlMonitoringExecutionRecorder → SqlMetricsCollector → Micrometer）；管理接口转真正 Actuator `@Endpoint(id="sqlmonitoring")` 由 exposure 治理；`log-parameters` 默认 true→false + SqlMaskingUtils 脱敏；monitor 模块 24 用例（6 脱敏 + 5 autoconfig + 1 exposure + 2 集成 + 10 legacy）/ 0 failures；monitor-minimal 翻转为正向验收（真实 SQL → sql.queries.total Micrometer 增长 + DataSource 代理断言 + Endpoint 装配）（详见 §16.12） | 多 DataSource 消费者监控代理不装配（@ConditionalOnSingleCandidate 退让）；ResultSet size 指标未采集（代理不包 ResultSet）；batch 计时为整批；INSERT...VALUES 位置脱敏未覆盖（logParameters 默认 false 兜底） |
| 2026-08-12 | EVT-001 | ✅ 已完成 | 提交 `fix(evt-001): 移除 EventAutoConfiguration 宽扫描，Outbox 按 mode 门禁 (EVT-001 #42)` | EventAutoConfiguration 移除宽 @ComponentScan（此前覆盖 outbox 子包致 OutboxEventServiceImpl 缺 ObjectMapper 装配失败）；新增 EventProperties（fastddd.event.mode，默认 local）；OutboxEventConfiguration 补 @ConditionalOnProperty(mode=outbox)+@ConditionalOnClass(BaseMapper)+@ConditionalOnBean(DataSource)；OutboxEventServiceImpl 移除 @Service、改 OutboxEventConfiguration 显式 @Bean（service+transactionalEventPublisher）；executor 由通用 taskExecutor→eventTaskExecutor；新增 EventAutoConfigurationBoundaryTest（4 用例）；event-minimal 翻转为无 DB local 模式正向验收（详见 §16.11） | mybatis-plus-spring-boot3-starter 为 fast-ddd-event 硬依赖（非 optional），其 AutoConfig 强制要 DataSource；event-minimal 以 spring.autoconfigure.exclude 刻画"无 DB"消费者，根因依赖边界（mybatis 改 optional）移交 EVT-002；RocketMQ @Component(DLQ/Listener) 注册为既有状态未改；outbox 正向装配端到端留 EVT-002 |

### 16.1 FRM-000 基线快照

整改前绿基线，后续任何 red 须归因到整改改动而非既有缺陷。

**A. 工具链与坐标**

| 项 | 值 |
|---|---|
| 分支 | `refactor/fastddd-1.0-framework`（自 `upgrade/jdk21-boot3.5.16` HEAD `740da39` 切出，二者不混历史） |
| JDK | 21.0.3 LTS（硬前置：`export JAVA_HOME="D:/usr/local/jdk/jdk-21.0.3"`） |
| Maven | 3.9.5（wrapper pin） |
| revision | `1.0.0-jdk21-SNAPSHOT`（CI-friendly 单一入口） |
| 全量构建 | `clean verify -Ptest` → reactor 27/27 SUCCESS、BUILD SUCCESS、5:18 min |

**B. 测试与生成基线**

- 测试：364 run / 0 failures / 0 errors / 6 skipped（与 UPG-005/009 及安全审查基线一致，0 回归）。
- 生成冒烟：`install -DskipTests` 刷新本地仓库后，`archetype:generate`（默认参数 `springBootVersion=3.5.16`、`modelType=ANEMIC`、`databaseType=mysql`、`enableCache/Event/Monitor=true`）生成成功；生成项目 `clean compile` BUILD SUCCESS（1 源文件，release 21）。冒烟使用系统 mvn 3.9.5 编译生成项目；FRM-002 将以独立 Maven Invoker harness 固化该回归基线。

**C. 依赖基线（studio-server 聚合点 `dependency:tree` 实证）**

- spring-boot（autoconfigure/boot/starter-*）：3.5.16
- spring-ai（starter-model-openai）：1.1.8
- jackson-databind：2.21.4（annotations 2.21 / core 2.21.4）
- mybatis-plus（jsqlparser/generator/extension）：3.5.12
- micrometer（observation/commons）：1.15.12
- jjwt（api/impl/jackson）：0.11.5
- hutool-all：5.8.34（MulanPSL-2.0，开源 NOTICE 项）
- druid-spring-boot-3-starter：1.2.28
- knife4j-openapi3-jakarta-spring-boot-starter：4.5.0

> 备注：已核实各模块 `jackson-databind` 均为无 `<version>` 声明（依赖块 `<artifactId>` 后紧跟 `</dependency>`），根 pom 与 fastddd-bom 均无 jackson 版本覆盖——**2.21.4 即 spring-boot-dependencies 3.5.16 BOM 的管理版本，项目侧零覆盖**。无需 OSS-001/CFG-001 针对 jackson 复核。

**D. 已知噪声（非致命，不阻塞整改）**

- `fastddd-maven-plugin` 的 maven-invoker 集成测试中，子 Maven 构建 `maven-javadoc-plugin` 报 `MavenInvocationException`、javadoc `Error fetching link ... Ignored it`——reactor BUILD SUCCESS，UPG-009 基线已存在，归 invoker 测试沙箱，不影响制品。

**E. 工作树既有修改归属（验收：每个修改有归属）**

3 个 carry-over 文件均随分支切换带入，均为 GPT「审计 + 整改方案」产物，**FRM-000 仅登记、不修改**（审计报告受验收"不覆盖审计报告"约束）：

| 文件 | 状态 | 归属 |
|---|---|---|
| `docs/FASTDDD_FRAMEWORK_ARCHITECTURE_AUDIT.md` | M（+434/-235） | GPT 审计报告扩充：加"审计基线 HEAD 740da39"header、重写 §1 结论、补整改方案交叉引用。注：header 写"复核时工作树干净"与当前实况（3 个 carry-over）不符，属 GPT 文本，仅登记。 |
| `docs/README.md` | M（+3/-1） | GPT 文档索引更新：登记审计报告 + 整改方案两行、"文档最后更新" 08-10→08-11。 |
| `docs/architecture/FASTDDD_1_0_FRAMEWORK_REMEDIATION_PLAN.md` | ??（未跟踪） | GPT 整改方案本体；FRM-000 填入本 §16 执行记录后纳入版本控制。 |

**F. 本次执行四项承重决策（已落项目记忆 `refactor-program-decisions`）**

1. M2 ID 门禁走"删除旧契约"分支——前提 FRM-001 实证无 PRODUCTION 消费者，否则回退兼容层延后 1.1。
2. 新建 `refactor/fastddd-1.0-framework` 改造，与 upgrade 不混历史。
3. Framework 开源许可证 = Apache-2.0；Hutool(MulanPSL-2.0) 需 NOTICE。
4. 采纳 review 修正：补 OSS-001、SPLIT-005 降为 Studio 门禁、写死"RC 对内、GA 才公开"。

### 16.2 FRM-001 冻结快照

四域架构决策记录（ADR）+ 消费者清单 + ID go/no-go 的冻结产物。

**A. 交付物（新增文件）**

| 文件 | 内容 | 决策摘要 |
|---|---|---|
| `docs/architecture/adr/ADR-001-id-type-system.md` | ID 类型系统契约 | go/no-go=GO（删除 Serializable-ID）；新强类型契约 + 五种 ID 形态覆盖；备选 B（兼容层）作回退 |
| `docs/architecture/adr/ADR-002-auto-configuration.md` | 自动配置契约 | 每模块 `FastDddModuleMode`(ENABLED/DISABLED/AUTO) + class 级类存在性门禁 + 每模块 FailureAnalyzer + @ConditionalOnMissingBean back-off |
| `docs/architecture/adr/ADR-003-event-outbox-transport.md` | 事件/Outbox 传输 SPI | `OutboxTransport` SPI 传输/调度分离；即时修 FOR UPDATE 无 @Transactional（P0-3）+ countPendingEvents 语句碰撞 |
| `docs/architecture/adr/ADR-004-security-jwt-model.md` | 安全/JWT 模型 | 彻底关 P0-7（class 级门禁）；`JwtTokenRevocationStore` SPI + Redis 多实例实现；令牌类型隔离冻结 |
| `docs/architecture/fastddd-consumers.yaml` | 消费者清单（机读） | studio/examples/archetype=DEVELOPMENT；在研业务系统=TEST_UAT；external=UNKNOWN（无 GA 制品，实质性不阻塞） |

**B. ID go/no-go 决策**

- **决策**：GO（删除 Serializable-ID 旧契约，M2 执行）。
- **前提核验**：所有已核实消费者均为 DEVELOPMENT；在研业务系统 TEST_UAT 且迁移由 UPG-011 承载；框架从未发布 GA 制品（`has_ga_artifact=false`）→ 不存在对稳定 API 的 PRODUCTION 消费者。满足用户预决策 #1 的前提。
- **对方案 §6 验收"PRODUCTION、UNKNOWN 不允许无兼容方案的破坏性升级"的偏离**：该条款针对"已发布稳定制品被外部生产依赖"场景；本框架无 GA 制品，SNAPSHOT 消费者按定义接受破坏性变更，"UNKNOWN 外部消费者"在无 GA 可消费时实质性不成立。偏离理由显式记录于 ADR-001 §2.1 与 consumers.yaml，便于审计回溯。
- **回退条件**：M2 执行前若发现真实 PRODUCTION 消费者，立即回退 ADR-001 备选 B（保留兼容层、破坏性删除延后 1.1）。

**C. 即时非破坏修复建议（不依赖 M2 删除，建议 M1 独立小任务先行）**

- 修正 `BasePO.java:36-38`、`FASTDDD_REFERENCE.md:89`、`UPGRADE_GUIDE_1.1.md:234` 关于"全局配置可覆盖字段 `@TableId`"的错误表述（字段级注解优先级高于全局配置）。
- 模板 `application.yml.ftl:49` 把 `id-type: auto` 改为 `assign_id` 以匹配 `BasePO` 实际 ASSIGN_ID 策略。
- 这两项立即关闭审计 P1-7/P1-10 的"矛盾"部分。

**D. 自审发现并已修正**

- 交叉核验中确认：整改方案编号只到 **ID-006**，验收 §6 行 356"ID-002～ID-008"为方案笔误（ID-007/008 不存在），ADR-001 §6 已据实记录限到 ID-006，本快照登记为待修。
- 确认审计用 P0/P1/P2 编号（非 SEC-/ID-/EVT-/CFG-），四份 ADR 统一以审计 P 编号 + 整改方案任务编号双引用，避免混用。

**E. 遗留/待用户 review**

- go/no-go 的"UNKNOWN 不阻塞"偏离为承重判断，虽已显式记录并设回退条件，仍提请用户在下一 review 点确认或回退。
- 事件多实例 claim 一致性（EVT-004）、真实 MQ（QA-002）等 Testcontainers 验证只能本地跑（无 CI runner，见 [[ci-infrastructure]]），列为 RC 验证项。

### 16.3 FRM-002 独立消费者测试骨架快照

用 maven-invoker-plugin 3.9.0 在**隔离 forked Maven 进程**里构建最小消费者工程，验证 `fast-ddd-*` 作为**外部依赖**被消费的真实边界——而非 reactor classpath 掩盖 optional 依赖问题（审计 P0-2 正是被 reactor classpath 掩盖的典型）。

**A. 交付物（新增模块 `fastddd-consumer-tests`）**

| 文件 | 内容 |
|---|---|
| `fastddd-consumer-tests/pom.xml` | packaging=pom；maven-invoker 3.9.0；`<invoker.skip>true</invoker.skip>` 默认 + profile `consumer-tests` 翻转；`<invoker.test>web-*</invoker.test>` 默认 gated 选择器；execution `consumer-integration-test` 绑 integration-test phase；`cloneProjectsTo=target/its`、`streamLogsOnFailures=true`、`writeJunitReport=true` |
| `fastddd-consumer-tests/README.md` | 骨架说明 + 隔离原理 + 两步 CI 命令 + 7 场景刻画表 + 实测根因（build.log 证据逐条 grep 比对） |
| `src/it/web-minimal/` | gated 正向：starter + application + H2，`ContextLoadsTest` 验上下文启动 |
| `src/it/web-without-application/` | gated 特征测试：无 application 依赖，`ReproducesP02Test` 断言上下文抛 `CommandValidationException`（P0-2） |
| `src/it/{security,cache,event,monitor,search}-minimal/` | 5 个 ungated scaffold，各挂对应任务（SEC-001 / CACHE-001 / EVT / MON-001 / SEARCH-001） |
| 根 `pom.xml` | `<modules>` 增 `fastddd-consumer-tests`；`<properties>` 增 `<maven-invoker-plugin.version>3.9.0</maven-invoker-plugin.version>` |

**B. 验收证据（命令级，2026-08-11）**

1. **验收①最小消费者独立 classpath 启动**：`web-minimal` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`（`ContextLoadsTest`）。
2. **验收②复现 Web 缺 application 失败**：`web-without-application` 特征测试通过；build.log 因果链 `BeanCreationException: Error creating bean with name 'globalExceptionHandler'` → `NoClassDefFoundError: com/gallop/fastddd/application/exception/CommandValidationException` → `ClassNotFoundException`。
3. **验收③诊断日志保留**：每场景 `target/its/<scenario>/build.log`。
4. **验收④六类空消费者预置**：7 场景（web ×2 + security/cache/event/monitor/search 5 scaffold）。
5. **验收⑤公共命令接入 CI、不强制全绿**：默认 gated `web-*` → `Passed: 2, Failed: 0`（BUILD SUCCESS）；全量 `-Dinvoker.test=*` → `Passed: 4, Failed: 3`。
6. **标准门禁不回归**：`clean verify -Ptest` → BUILD SUCCESS；consumer-tests 模块 invoker 默认 skip（日志 `invoker:3.9.0:run ... Skipping invocation per configuration`），0 surefire 单测；基线 364 run 不变（§16.1.B）。

**C. 全量场景实测刻画（Passed 4 / Failed 3）**

| 场景 | 结果 | 根因 / 备注 |
|---|---|---|
| web-minimal | ✅ | 上下文独立 classpath 启动 |
| web-without-application | ✅ | 特征测试通过（P0-2 存在）；WEB-001 修复后翻转为正向 `contextLoads` |
| security-minimal | ✅ | 启动（给 ≥64B dummy JWT secret）；SEC-001 待刻画 |
| search-minimal | ✅ | 启动（无 ES 后端不崩）；SEARCH-001 待刻画 |
| cache-minimal | ❌ | `NoClassDefFoundError: org/springframework/data/redis/connection/MessageListener`——cache 硬引用 Redis 未 `@ConditionalOnClass` 门禁（P0-2 同类 optional 边界泄漏）；CACHE-001 |
| event-minimal | ❌ | bean `outboxEventServiceImpl` 构造器参数缺 `ObjectMapper`——`OutboxEventServiceImpl` 假设 web starter 提供；ADR-002 自动配置门禁 / EVT |
| monitor-minimal | ❌ | bean `sqlMetricsCollector` 参数 `expected single matching bean but found 2: sqlMonitoringProperties, fastddd.monitor.sql-...SqlMonitoringProperties`——**审计 P0-4 直接、隔离复现**；MON-001 |

> monitor-minimal 的失败是对审计 P0-4（SQL 监控 `@ConfigurationProperties` bean 重复注册）的独立隔离复现——无需额外单测即锁定根因，证实骨架已具备暴露此类 consumer-boundary 缺陷的能力。

**D. 自审发现并已修正**

- README 初稿写"标准 `./mvnw verify`（27 模块全量门禁）保持 27/27 绿"——`clean verify -Ptest` 实证 reactor summary 现含 consumer-tests 模块（29 行含根 parent），"27/27"为过期基线数。已改为陈述机制 + 日志实证（`Skipping invocation per configuration` + 0 surefire），不写过期的硬编码模块数。
- `clean verify` 会清掉 `target/its/`（含先前 build.log 证据）。自审发现证据丢失后，已用 `install -DskipTests` + `-Dinvoker.test=*` 复跑重建证据，README 所列根因字符串均经 grep 逐条比对 build.log 原文确认。
- `-Pconsumer-tests` 从 reactor 根调用时 Maven 打印 `profile "consumer-tests" could not be activated` 装饰性警告（profile 在子模块而非根 pom）。已用反证 + 实测证明 profile 实际生效：若未生效则 invoker 被默认 skip、跑 0 场景，与实测跑出 2（gated）/ 7（全量）相悖；README 显式说明该警告无害。

**E. 遗留**

- 3 个 ungated 失败（cache / event / monitor）登记到 M1 任务 CACHE-001 / EVT / MON-001；本任务仅刻画、不修（修复属各任务范围）。
- consumer-tests 模块为 packaging=pom + 0 surefire，不贡献 reactor 测试计数，但使 reactor 模块数 +1；README 已以机制陈述取代硬编码模块数，避免再次失真。

### 16.4 SPLIT-001 产品边界与依赖方向 ADR 快照

冻结 Framework / Tooling / Studio 三层边界、依赖方向、坐标/版本/BOM 归属、许可证与开源时点。纯 ADR 任务，不移动 Git 目录、不创建新仓库（SPLIT-001 禁止项）。

**A. 交付物**

- `docs/architecture/adr/ADR-005-product-boundary-dependency-direction.md`

**B. 命令级证据（2026-08-11）**

- 模块归属：26 叶模块逐一归属（Framework 14 / Tooling 7 / Studio 2 / BOM 1 / Examples 1 / Consumer-tests 1），grep 全部 pom 实证；4 个聚合 pom（根 + framework + tooling + studio）只聚合无代码。
- 依赖图：跨层依赖**仅** `fast-ddd-web → fastddd-fastdoc-core`（optional，`fast-ddd-web/pom.xml:28-31`）一条违规；其余全为允许方向（Studio→Tooling→Framework；examples/consumer-tests→Framework）。**无** Tooling→Studio、Framework→Studio。
- `fastdoc-core` 归属三证一致：物理目录 `fastddd-tooling/`、声明于 tooling 聚合 pom、artifactId `fastddd-fastdoc-core` → 归属 Tooling，无歧义；问题是 web（Framework）跨层上依赖它。
- groupId：29 模块 `com.gallop.fastddd`（唯一）；另 1 个 `${groupId}` 是 archetype 模板占位符。
- 许可证文件：全仓无 `LICENSE`/`NOTICE`；根 pom 无 `<license>` 声明 → Apache-2.0 决策（承重记忆）尚未物化。

**C. 冻结结论**

- 三层边界 + 依赖方向规则：允许 Studio→Tooling→Framework、消费者→Framework（经 BOM）；禁止 Framework→Tooling/Studio、Tooling→Studio。
- 坐标/版本：groupId + artifactId 冻结（拆仓不改业务坐标）；Framework=语义化版本（`${revision}`）；Studio=独立版本；`fastddd-bom`=Framework 消费者契约。
- 许可证：Framework + Tooling = Apache-2.0（推荐，待权利人确认 + OSS-001 终审）；Studio = 专有/商业；开源与商业授权不混同。
- 时点：内部 RC 对内不公开；Framework GA 才公开；RC1 前冻结公共 API（本 ADR + ADR-001~004 即冻结产物）。
- 强制规则设计：Maven Enforcer `banDependencies`（POM 声明级）+ ArchUnit（字节码级），接线排在 SPLIT-002 之后（否则当前 web→fastdoc 违规使 enforcer 立即失败）。

**D. 移交**

- `fast-ddd-web → fastdoc-core` 反向依赖消除 → **SPLIT-002**（方案 A 接口下沉 / 方案 B 控制器上移，二选一）。
- `LICENSE`/`NOTICE` 物化 + 权利人终审 + Hutool(MulanPSL-2.0) NOTICE → **OSS-001 / 发布前任务**。

**E. 遗留**

- Tooling=Apache-2.0、Studio=商业为**推荐结论**，最终以权利人确认 + OSS-001 终审为准（plan 步骤 8 明示 Framework 许可证"最终选择以权利人确认和 OSS-001 审核为准"，本 ADR 同标准对待 Tooling/Studio）。

### 16.5 CFG-001 配置契约清单与硬化快照

建立 FastDDD 配置契约单一事实源，并落地两项关键硬化：旧根 fail-fast guard、优先属性类声明式数值约束。任务分三提交（步骤1-3 清单 / 步骤6 guard / 步骤4 约束），核心验收①②③ 全部满足。

**A. 交付物**

- `docs/architecture/CFG_001_CONFIGURATION_INVENTORY.md`（配置契约单一事实源：§1 前缀总览、§2 字段明细、§3 错配分析、§4 metadata 一致性、§5 验收对照与残留、§6 步骤4 已落地约束）
- `fast-ddd-config`：`FastDddLegacyRootValidator`（EnvironmentPostProcessor）+ `META-INF/spring.factories` 注册 + 3 单测
- `fast-ddd-security-starter`：`VerificationCodeProperties` 补 `@Validated`+`@Min(4)@Max(10)`/`@Min(1)` + 5 单测
- `fast-ddd-cache`：`CacheProperties` 补 `@Validated`+`@Valid`+`@Min(1)` + 4 单测
- `fastddd-templates`：wechat-mini 认证模板 3 处 `fast-ddd.security.wechat-mini` → `fastddd.security.wechat-mini`（旧根 guard 前置修复）

**B. 命令级证据（2026-08-11）**

- 前缀总览：16 个 `@ConfigurationProperties` 前缀（grep 全仓实证）——14 Framework + `fastddd.codegen.database`/`fastddd.doc`(Tooling) + `rocketmq.publisher`/`rocketmq.consumer`(偏离约定，登记)。
- 字段约束现状（取证前）：14 类**无一 `@Validated`、无一字段带 bean-validation 注解**；仅 2 处程序式校验（`JwtProperties.validateHmacSecret` ≥64B、`CorsProperties.validateAndLog` 仅告警）。
- 错配高危：`fastddd.application.cqrs.*` / `fastddd.event.*` 全仓**无绑定**，archetype 却生成 → 消费者"配置看似生效实则忽略"（outbox 幻觉最危险），移交 CFG-002 删模板。
- 旧根 guard 注册链：`spring.factories` → `fast-ddd-config` → `fast-ddd-starter`(pom:29) → 所有消费者；构建产物 `target/classes/META-INF/spring.factories` 实证存在（109B）。3 单测：旧根失败 / 正确根通过 / artifactId 风格(`fast-ddd-common`)不误判。
- 声明式约束下限取证（全仓配置值扫描，Explore 子代理）：`maxAttempts` 测试设 `2`→下限须 ≤2 取 1；`maxSize` archetype/template 设 `1000`→下限须 ≤1000 取 1；现配与默认全部满足新约束。`ApplicationContextRunner`+`ValidationAutoConfiguration` 单测实证绑定期 `ConfigurationPropertiesBindException` 真实触发（非装饰）。
- 门禁：`clean verify -Ptest` 29 模块 SUCCESS；测试 **376 run / 0 failures / 0 errors / 6 skipped**（基线 364 → +step6 3 → +步骤4 9），0 回归。

**C. 验收对照**

- 验收①（不存在未登记的生产配置项）：✅ §1 全部 16 前缀登记 + §3 标记全部错配。
- 验收②（fast-ddd 旧根出现时明确失败）：✅ `FastDddLegacyRootValidator` 启动期 fail-fast，3 单测 + 注册链实证。
- 验收③（metadata 与属性类一致）：✅ §4——`spring-boot-configuration-processor` 自动派生，结构一致；快照测试为低价值残留。

**D. 移交与残留**

- 移交 **CFG-002**：§3.1 未绑定配置（cqrs/event，archetype 生成死配置）+ §3.4 `id-type: auto` 模板错配，由 CFG-002 从模板/archetype 删除或对接真实绑定。
- 残留 **CFG-001-step4 扩展**：`Duration` 字段（`ttl`/`resendInterval`/`expireAfterWrite*`/`jwt.expiration`/`refreshExpiration`/`meilisearch.timeout`）无内置 bean-validation 约束，需自定义 `@PositiveDuration` 校验器（独立小任务；`resendInterval` 注意用 `@PositiveOrZero` 语义——测试用 `Duration.ZERO`）；其余约 10 类按需补，不批量加空约束；`JwtProperties.secret` 维持程序式校验（更强，不重复）。
- 残留 **CFG-001-step5**（低优先）：metadata 快照测试。

### 16.6 CFG-002 生成配置模板修复快照

按 CFG-001 清单（§3.1 未绑定配置、§3.4 id-type）修复全部生成模板的配置契约，并落地渲染期校验。单提交 `eb3ef0c`。

**A. 交付物（3 模板 + 1 测试 + 1 pom）**

- `templates/project/application.yml.ftl`（单项目，FreeMarker）：根前缀、删 cqrs/event、JWT secret 环境变量化、`slow-query-threshold`、`assign_id`。
- `templates/project/multi-channel/sub-app-application.yml.ftl`（多端，FreeMarker）：根前缀、http/cors 嵌套修正、`assign_id`。
- `archetype-resources/.../application.yml`（archetype，Velocity）：删 cqrs/event、`slow-query-threshold`。
- `fastddd-templates`：新增 `ProjectConfigContractTest` + `snakeyaml`（test-scope，BOM 管理版本）。

**B. 命令级证据（2026-08-11）**

- 取证（Explore 子代理）：全仓 6 个配置发射文件，缺陷定位到行——`application.yml.ftl:63` 根前缀、`:74` 固定 secret、`:106` slow-threshold、`:64-67/97-102` 未绑定 cqrs/event；`sub-app:61` 根前缀、`:70/74` http/cors 错嵌套（绑定到不存在的 `fastddd.http/cors`）；archetype `:63` slow-threshold、`:40-44/54-59` 未绑定。
- id-type 决策：`BasePO` 声明 `@TableId(type=IdType.ASSIGN_ID)`（snowflake），全局 `id-type` 仅影响无注解实体；生成 PO 均继承 BasePO → 已强制雪花。故 `auto→assign_id` 零运行期风险，仅使配置诚实（对齐 CFG-001 §3.4）。
- wechat-mini 决策：无 `@ConfigurationProperties` 类，但 `WechatMiniAuthStrategy.java.ftl` 经 `@Value("${fastddd.security.wechat-mini.*}")` 绑定（CFG-001 step6 已修）→ 非死配置，保留。
- 渲染期契约测试（步骤5）：`ProjectConfigContractTest` 3 用例——单项目 RICH 全模块 / 单项目 ANEMIC 最小 / 多端 subApp；SnakeYAML 解析渲染产物遍历键路径，断言无 `fast-ddd.*`、无未绑定 `fastddd.application.*`/`fastddd.event.*`、二级域 ⊆ 白名单、`slow-query-threshold` 存在、JWT secret=`${JWT_SECRET}`、多端 `fastddd.security.http.*`/`fastddd.security.cors.*` 嵌套正确。
- 生成冒烟（步骤6）：`archetype:generate`（ANEMIC+MYSQL+cache/event/monitor）生成 application.yml 实证无 `fast-ddd`/`slow-threshold`/`cqrs`/`event`（即便 enableEvent=true），`slow-query-threshold` 正确。
- 静态 grep：3 模板四类缺陷（fast-ddd 根 / slow-threshold / 未绑定 cqrs·event / 固定 JWT secret）全部清零。
- 门禁：`clean verify -Ptest` 29 模块 SUCCESS；测试 **379 run / 0 failures / 0 errors / 6 skipped**（基线 376 + 本步 3），0 回归。

**C. 验收对照**

- 验收①（模板无 fast-ddd 根/固定 secret/未知配置）：✅ 静态 grep + 渲染契约测试 + 生成冒烟三重实证。
- 验收②（真实启动配置全部绑定）：✅ 结构性保证——生成的每个 `fastddd.*` 键均落在绑定白名单内（契约测试遍历断言）；`fast-ddd.*` 旧根由 CFG-001 step6 guard 启动期拒绝。

**D. 移交与残留**

- 移交 **EVT/ADR-003**：删除的 `fastddd.event.*`（outbox/async）待 OutboxTransport SPI 决定补真实绑定后再恢复生成。
- 残留：DB `your_password` 占位符（dev 便利，非功能凭据，prod 模板已用 `${DB_PASSWORD:}`）；CFG-002 步骤6 的"4 组合"以单组合冒烟 + 覆盖 ANEMIC/RICH 的契约测试替代，全组合矩阵留 **QA-001**。
- 过程教训（已规避）：`archetype:generate` 在 reactor 根目录内生成会回写父 pom `<module>`——后续生成冒烟须用 `-DoutputDirectory` 指向仓库外临时目录（本次已 `git restore pom.xml` 回退误改）。

### 16.7 SPLIT-002 移除 Framework→FastDoc 反向依赖快照

消除 ADR-005 违禁的 `fast-ddd-web（Framework）→ fastddd-fastdoc-core（Tooling）` 反向依赖，将 FastDoc HTTP 适配整体上移至 Tooling 层新建 starter。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| 新建模块 | `fastddd-tooling/fastddd-fastdoc-spring-boot-starter`：`FastDocController`（13 端点 `/api/fastdoc/*`，包改为 `com.gallop.fastddd.fastdoc.web`）+ `FastDocWebAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnClass(FastDocService.class)` + `@AutoConfigureAfter(DataSourceAutoConfiguration.class)`，4 个 @Bean：fastDocService/fastDocTemplateService/fastDocExportService/fastDocController）+ `META-INF/spring/...AutoConfiguration.imports` + README |
| fast-ddd-web 瘦身 | 删除 `fastdoc-core` 依赖（pom 留说明注释）；删除 `web/fastdoc/` 包（FastDocController + README）；`FastDddWebAutoConfiguration` 移除 fastdoc import、`excludeFilters` 正则、嵌套 `FastDocConfiguration`，瘦身为纯 web 职责（ComponentScan + Import + GlobalExceptionHandler @Bean） |
| 模块注册 | tooling 聚合器 `<module>` + 根 pom `<dependencyManagement>` + `fastddd-bom` + `fastddd-studio-server` 消费依赖 |
| 框架级门禁 | `fast-ddd-common/FrameworkLayeringDirectionTest`：从 common basedir 遍历同级 `fast-ddd-*` 各模块主源码树，断言无 import 命中 `com.gallop.fastddd.{fastdoc,codegen,doc,templates,maven.plugin,studio}.` 前缀 |
| 消费者验证 | `fastddd-consumer-tests/src/it/fastdoc-minimal`：forked 构建（spring-boot-starter-parent 3.5.16 + import fastddd-bom + fastdoc-starter + jdbc + h2），`@SpringBootTest` 断言 FastDocService + FastDocController bean 装配 |

**B. 命令级证据**

- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，380 tests / 0 failures / 0 errors**（新 starter 入 reactor；Studio 46 tests 含 FastDoc 已激活仍 PASS）。
- Framework 独立构建（显式列出 14 个 `fast-ddd-*` 模块 `-pl ... -am`，无 Tooling）：**15/15 SUCCESS，320 tests / 0 failures**；Reactor Summary 仅 framework 模块，无 tooling/studio。
- fastdoc-minimal 消费者（maven-invoker forked）：**Tests run: 1, Failures: 0**（FastDocService + FastDocController 双 bean 注入成功）。
- 14 个 framework 模块 `dependency:list` 对禁忌自模块（fastdoc/codegen/doc-generator/templates/maven-plugin/doc-maven-plugin/archetype/studio）命中数 **均为 0**。
- framework 源码 `import com.gallop.fastddd.{fastdoc,codegen,doc,templates,maven.plugin,studio}.` grep **零命中**。
- `FrameworkLayeringDirectionTest` 经负向探针证明非空（植入违禁 import → 测试失败；移除 → 通过）。

**C. 验收对照（方案三验收标准）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| ① | fastddd-framework 可在不构建 Tooling 下 `clean verify` | ✅ | 14 模块独立 reactor 320 tests / 0 fail，无 tooling/studio 入 reactor |
| ② | FastDoc 在 Studio + 消费者可用 | ✅ | fastdoc-minimal Tests run:1 pass；Studio 全量 reactor 46 tests pass |
| ③ | Framework 无 fastdoc/codegen/templates/studio 引用 | ✅ | 14 模块 dependency:list 0 命中 + 源码 import 0 命中 + 门禁测试守卫 |

**D. 移交与残留**

- **门禁形态决策（持久化登记）**：framework 各子模块 `<parent>` 为根 pom（非 `fastddd-framework` 聚合器），故聚合器级 maven-enforcer `bannedDependencies` **不被 14 个子模块继承**——实证仅在聚合器自身运行（trivially pass）。本任务改用 `FrameworkLayeringDirectionTest`（源码级 Java import 扫描，一次覆盖全部 framework 模块）作为 `verify` 阶段失败即阻断的门禁。**持久 per-submodule enforcer 门禁（POM 声明级）移交 OSS-001**：需将 enforcer 下沉到根 pom `<build><plugins>`（被全部子模块继承）并配合 `<dependencyManagement>` 收敛，属独立改造范围。
- **顺带修正的存量缺陷**：原 fast-ddd-web 嵌套 `FastDocConfiguration` 的 `fastDocService` @Bean 标注 `@ConditionalOnBean(DataSource.class)` 但**未声明 `@AutoConfigureAfter(DataSourceAutoConfiguration.class)`**——条件在 DataSource 注册前求值被跳过，该配置**从未在真实消费者中激活**（FRM-002 取证佐证）。新 starter 补排序注解，消费者测试实证 FastDoc 首次真正装配。此为 SPLIT-002 的附带收益，非新增风险。
- **消费者门禁纳入**：fastdoc-minimal 已纳入 `fastddd-consumer-tests`，默认 `invoker.skip=true`，需 `-Pconsumer-tests -Dinvoker.test=fastdoc-*` 触发（与 FRM-002 骨架一致）。
- WEB-001（Web optional application 隔离）依赖项 FRM-002 + SPLIT-002 均已完成，**已解锁**，作为下一 M1 任务。

### 16.8 WEB-001 Web optional application 隔离快照

消除 fast-ddd-web 对 optional 依赖 fast-ddd-application 的硬耦合，使 web 可在无 application 时启动（P0-2）。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| 新建 advice | `web/exception/CqrsExceptionAdvice`：`@RestControllerAdvice` + `@ConditionalOnClass(CommandValidationException.class)`，承载 `handleCommandValidationException`（自 GlobalExceptionHandler 抽离，逻辑原样） |
| GlobalExceptionHandler 瘦身 | 移除 `CommandValidationException` import 与 handler 方法，仅保留 common/web 异常处理（BusinessException/SystemException/校验/绑定/类型/运行时等），application-free |
| web-without-application 消费者 | FRM-002 特征测试 `ReproducesP02Test`（缺陷存在时通过）翻转后删除；改为正向 `WebWithoutAppStartsTest`（contextLoads + GlobalExceptionHandler 装配断言）；invoker.properties/pom/app/yml 注释同步 |
| web-minimal 消费者 | `ContextLoadsTest` 加强：新增 `cqrsExceptionMappingIsActiveWhenApplicationPresent` 断言 CqrsExceptionAdvice 装配（证明 application 在 classpath 时 CQRS 异常映射仍生效） |

**B. 命令级证据**

- **翻转验证（TDD 闭环）**：修复前 web-without-application 特征测试 Passed:1（断言启动抛 CommandValidationException）；修复后翻转 Failed:1（`Expected Throwable to be thrown, but nothing was thrown`——上下文成功启动）；改为正向后 Passed:1。
- web-* 双消费者：**Passed:2 / Failed:0**（web-without-application 无 application 启动成功 + web-minimal 有 application CqrsExceptionAdvice 装配）。
- fast-ddd-web 单测：**36 tests / 0 failures**，含 `GlobalExceptionHandlerTest` 6 用例（`shouldHandleCommandValidationException` 经 CqrsExceptionAdvice 仍生效，HTTP 响应不变）。
- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，380 tests / 0 failures / 0 errors**。
- GlobalExceptionHandler 源 application import grep **零命中**。

**C. 验收对照（方案步骤验收）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| ① | 无 application 时启动成功 | ✅ | web-without-application contextLoads（WebWithoutAppStartsTest） |
| ② | 有 application 时异常映射仍生效 | ✅ | web-minimal CqrsExceptionAdvice 装配断言 + GlobalExceptionHandlerTest shouldHandleCommandValidationException |

**D. 移交与残留**

- **软耦合保留（设计决策）**：`CommandController`/`QueryController`（abstract CQRS 基类）仍 import `com.gallop.fastddd.application.{command,query}`。二者为抽象类，Spring 组件扫描经 ASM `MetadataReader` 读取注解元数据时不触发类加载，且 abstract 类非候选 bean；仅在被消费者子类化（消费者必有 application）时才加载。web-without-application 成功启动实证此软耦合不阻塞验收。**未删 CQRS 异常处理、未把 application 改必选**（遵守禁止条款）。
- **门禁形态**：P0-2 回归防护由 web-without-application 消费者（正向 contextLoads）+ FrameworkLayeringDirectionTest（SPLIT-002，防 application 包 import 回流至非 advice 类）共同覆盖；@ConditionalOnClass 缺失可在该消费者立即暴露（启动失败）。
- **测试命名遗留**：`GlobalExceptionHandlerTest` 类名含历史 "CommandValidationException 测试" 注释，行为已迁移至 CqrsExceptionAdvice 但 MockMvc 端到端验证仍归属"全局异常处理"语义，未改名（避免无谓 churn）；如需精确可在 QA-001 统一重命名。
- **下一 M1 任务**：CACHE-001（L1 缓存命名空间隔离，FRM-002 已隔离复现 P0-x）、MON-001（SQL 监控属性 Bean 修复，FRM-002 已隔离复现 P0-4）。

### 16.9 CACHE-001 L1 缓存命名空间隔离快照

修复 L1（Caffeine）缺命名空间隔离（P0-3）与 Redis 类泄漏（cache-minimal 启动失败）两个缺陷。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| L1 命名空间隔离 | `CacheRegistry.createCache`：每个 cacheName 独占 `new CaffeineLocalCache(merged)`（对象级隔离，零方法签名改动）；`defaultLocalCache` 单例降级为 L1 启用标记 + 全局配置来源 |
| Redis 类泄漏修复 | 新建 `CacheSyncPublisher` 核心接口（`com.gallop.fastddd.cache`，无 Redis 依赖，publishEvict/publishClear）；`RedisCacheSyncListener implements CacheSyncPublisher, MessageListener`；`CacheRegistry`/`MultiLevelCache`/`FastDddCacheAutoConfiguration` 改引用 `CacheSyncPublisher`；`CacheRegistry.getSyncListener` 对 null provider 加 guard |
| 单测 | `CacheNamespaceIsolationTest`（5 用例）：相同 key 跨 cacheName 不串值 / evict 隔离 / clear 隔离 / 跨类型不串值 / 并发 getOrCreate 单实例 |
| 消费者 | `cache-minimal`：升级为正向验收——无 Redis 启动（context loads + CacheService 装配）+ L1 命名空间隔离（users/orders 相同 key 不串值 + evict 隔离） |

**B. 命令级证据**

- cache 单测：**9 tests / 0 failures**（5 命名空间隔离 + 4 属性校验）。
- **cache-minimal 翻转**：修复前 Failed（`NoClassDefFoundError: org/springframework/data/redis/connection/MessageListener`，cacheRegistry bean 装配期）→ 修复后 **Passed:1**（context loads + 命名空间隔离双断言）。
- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，385 tests / 0 failures / 0 errors**（+5 新增命名空间用例，0 回归）。
- 核心层 `CacheRegistry`/`MultiLevelCache` 对 `RedisCacheSyncListener` 硬引用 grep **零命中**；该类仅出现在 `@ConditionalOnClass(RedisTemplate)` 门禁的内层配置 @Bean。

**C. 验收对照（方案步骤验收）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| ① | users 与 orders 相同 key 不串值、不误清理 | ✅ | CacheNamespaceIsolationTest 3 用例 + cache-minimal 消费者端到端 |
| ② | 并发和多级缓存测试通过 | ✅ | 并发 getOrCreate 单实例用例 + 全量 reactor 0 回归 |

**D. 移交与残留**

- **L2/Pub/Sub 命名空间本就正确**：`RedisCache.buildKey` 已带 `keyPrefix + cacheName + "::" + key`；`SyncMessage` 协议已带 cacheName 且经 `cacheRegistry.getIfPresent(cacheName)` 定向。本次未改二者。L1 改对象级隔离后，`evictLocal`/`evictAllLocal`（pub/sub 回调）天然落在目标 cacheName 的独占实例上，集群范围串值随之消除。
- **Redis 跨节点同步完整集成测试留 QA-001**：需真实 Redis broker 验证多节点失效广播；本次以核心接口解耦 + L1 单测 + 无 Redis 消费者实证命名空间不变量。
- **defaultLocalCache 单例语义变化**：原作共享存储，现仅作启用标记；其内部 Caffeine 实例不再被各 cacheName 使用（ negligible 内存，保留是为维持 `@ConditionalOnProperty(local.enabled)` 门禁与最小 diff）。
- **metrics 空白（非缺陷）**：模块无任何指标类，命名空间处理在指标层不适用；方案步骤3"统一指标命名空间"对此模块无落点，登记为 N/A。
- **下一 M1 任务**：MON-001（SQL 监控属性 Bean 修复，FRM-002 已隔离复现 P0-4）。

### 16.10 MON-001 SQL 监控属性 Bean 修复快照

修复 SQL 监控属性 Bean 双注册导致的 `NoUniqueBeanDefinitionException`（审计 P0-4），并清理同模式的双重注册与固定名 `@DependsOn`。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| P0-4 根因修复 | `SqlMonitoringAutoConfiguration` 删除 `@Bean sqlMonitoringProperties()`（与 `@EnableConfigurationProperties(SqlMonitoringProperties.class)` 重复注册的根因）；属性类现仅由 `@EnableConfigurationProperties` 单一方式注册 |
| 同模式双注册清理 | `SqlMetricsCollector`（删 `@Component`）、`SqlMonitoringMessageFormatter`（删 `@Component`）、`SqlMonitoringService`（删 `@Service`）移除刻板注解——消除"组件扫描 + AutoConfig @Bean"双重注册隐患（业务方宽 `@ComponentScan` 时不再 NoUnique）；三类的 AutoConfig `@Bean` 方法补 `@ConditionalOnMissingBean`（业务自定义时框架退让） |
| 固定名 DependsOn 移除 | `sqlMonitoringMessageFormatter` 上 `@DependsOn({"sqlMonitoringProperties","sqlMetricsCollector"})` 移除——引用已删除的 `sqlMonitoringProperties` Bean 名且方法签名已声明依赖，Spring 自动排序，固定名引用冗余脆弱 |
| 单测 | `SqlMonitoringAutoConfigurationTest`（4 用例 ApplicationContextRunner）：单 properties bean 无 NoUnique / 业务自定义 Service 框架退让 / 条件满足时 Bean 图完整 / disabled 时整体退让 |
| 消费者 | `monitor-minimal`：由 FRM-002 预置 shell 翻转为正向验收——无 NoUnique 启动（context loads + SqlMonitoringService 装配）+ SqlMonitoringProperties 单一实例断言 |

**B. 命令级证据**

- monitor 单测：**14 tests / 0 failures**（4 ApplicationContextRunner + 10 原有 POJO 单测）。
- **monitor-minimal 翻转**：修复前 Failed（`NoUniqueBeanDefinitionException: expected single matching bean but found 2: sqlMonitoringProperties, fastddd.monitor.sql-...SqlMonitoringProperties`，sqlMetricsCollector bean 装配期）→ 修复后 **Passed:1**（context loads + 单 properties bean 双断言）。
- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，0 failures / 0 errors**（+4 新增 ApplicationContextRunner 用例，0 回归）。
- monitor 模块内 `@Component`/`@Service` 刻板注解 grep：`SqlMonitoringAutoConfiguration` 的 4 个 `@Bean` 对应类**零刻板注解命中**；`@DependsOn` 全模块 grep **零命中**。

**C. 验收对照（方案步骤验收）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| ① | 不再出现 NoUniqueBeanDefinitionException | ✅ | monitor-minimal Passed；ApplicationContextRunner `singlePropertiesBean_noNoUniqueException` 用例 |
| ② | 最小 monitor 消费者可以启动 | ✅ | monitor-minimal Passed（context loads + SqlMonitoringService 装配） |
| 步骤2 | SqlMonitoringProperties 只保留一种注册方式 | ✅ | 仅 `@EnableConfigurationProperties`；`@Bean sqlMonitoringProperties()` 已删 |
| 步骤3 | 移除对固定 Bean 名称的 DependsOn | ✅ | `@DependsOn` 全模块零命中 |
| 步骤4 | 业务已有配置 Bean 验证退让行为 | ✅ | 三 @Bean 补 `@ConditionalOnMissingBean`；`customSqlMonitoringService_frameworkBacksOff` 用例实证 |
| 步骤5 | conditions 报告确认 Bean 图 | ✅ | `beanGraphFullyAssembled_whenConditionsMet` + `beanGraphEmpty_whenDisabled` 以 bean 在场性实证条件求值 |

**D. 移交与残留**

- **MeterRegistry 多候选退让未采用 `@ConditionalOnSingleCandidate`**：经 monitor-minimal 消费者实证，fast-ddd-monitor 硬依赖 `micrometer-registry-prometheus` + actuator，消费者 classpath 下 `MeterRegistry` 非单候选（simple + prometheus + composite）；类级加 `@ConditionalOnSingleCandidate(MeterRegistry.class)` 会令整个 SQL 监控在标准消费者下**整体退让、永不装配**（实证：SqlMonitoringService bean 缺席）。而原 P0-4 失败仅源于 SqlMonitoringProperties 双注册，MeterRegistry 注入在标准消费者下可解析。故 MON-001 不改 MeterRegistry 注入契约；多 MeterRegistry 精细退让（业务同时启用 Prometheus + Atlas 等场景）移交 **MON-002**。
- **SqlMonitoringController 未改**：仍为 `@RestController`（无 AutoConfig `@Bean`），在消费者未 `@ComponentScan` 该包时不装配；暴露/Endpoint 化属 MON-002（"将管理接口改为真正的 Actuator Endpoint"）范畴。
- **P6Spy 真实 SQL 链路**：MessageFormatter 的 Spring `@Bean` 与 P6Spy 反射实例化的桥接、Endpoint exposure、敏感参数脱敏均为 MON-002 范畴，本次不动。
- **下一 M1 任务**：EVT-001（本地事件与 Outbox 装配边界，依赖 FRM-001/002 ✅）/ SEC-001（依赖 FRM-002 ✅）/ SEC-002（依赖 FRM-001/002 ✅）三选一按依赖序推进。

### 16.11 EVT-001 本地事件与 Outbox 装配边界快照

修复 `EventAutoConfiguration` 宽 `@ComponentScan` 无条件拾取 outbox 子包导致的 `local` 场景装配失败（审计 EVT-001），以 `fastddd.event.mode` 显式门禁 Outbox 装配边界。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| EVT-001 根因修复 | `EventAutoConfiguration` 删除宽 `@ComponentScan(basePackages="com.gallop.fastddd.event", excludeFilters=[rocketmq])`——此前无条件拾取 outbox 子包，local 场景下 `OutboxEventServiceImpl` 缺 ObjectMapper/DataSource 装配失败（`UnsatisfiedDependencyException`） |
| 模式门禁 | 新增 `EventProperties`（`@ConfigurationProperties(prefix="fastddd.event")`，`mode=local|outbox`，默认 local）；`springEventPublisher` `@Bean` 补 `@ConditionalOnProperty(mode=local, matchIfMissing=true)`；Outbox 链路改由 `@Import(OutboxEventConfiguration.class)` 显式引入 |
| Outbox 边界三重门禁 | `OutboxEventConfiguration` 补 `@ConditionalOnProperty(mode=outbox)` + `@ConditionalOnClass(BaseMapper)` + `@ConditionalOnBean(DataSource)`——local 模式整体退让，零 Outbox Bean |
| 刻板注解清理 + 显式 @Bean | `OutboxEventServiceImpl` 删 `@Service`；OutboxEventConfiguration 改为显式 `@Bean outboxEventService(...)` + `@Bean transactionalEventPublisher(...)`（均 `@ConditionalOnMissingBean`） |
| 模块专用 executor | `eventTaskExecutor` `@Bean`（`@ConditionalOnMissingBean(name="eventTaskExecutor")`，线程名前缀 `event-async-`）替代通用 `taskExecutor` |
| 单测 | `EventAutoConfigurationBoundaryTest`（4 用例 ApplicationContextRunner）：local 默认启动 + 零 Outbox Bean / 显式 local 等价 / outbox 无 DataSource 退让 / 模块专用 executor 名 |
| 消费者 | `event-minimal`：由 FRM-002 预置 shell 翻转为正向验收——local 模式无 DB 启动 + 发布领域事件被监听 + 零 Outbox Bean |

**B. 命令级证据**

- event 单测：**7 tests / 0 failures**（4 ApplicationContextRunner 边界 + 2 原有集成 + 1 SpringEventPublisher 简单用例）。
- **event-minimal 翻转**：修复前 Failed（`UnsatisfiedDependencyException`：OutboxEventServiceImpl 缺 ObjectMapper，源于宽扫描拾取 outbox 子包）→ 修复后 **Passed:2**（local 启动 + 事件发布 latch 接收 / 零 Outbox Bean 断言）。
- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，0 failures / 0 errors**（+4 新增 ApplicationContextRunner 用例，0 回归）。
- event 模块内 `@ComponentScan`/`@Service`/`name="taskExecutor"` grep：**零实际注解命中**（仅 Javadoc 文本提及）；`@AutoConfigureAfter(DataSourceAutoConfiguration.class)` 确保 `@ConditionalOnBean(DataSource)` 求值顺序正确。
- `RocketMQEventAutoConfiguration` 经 Read 实证为独立 `@AutoConfiguration`（自带 `@ConditionalOnClass(RocketMQTemplate.class)` + `@ConditionalOnProperty("rocketmq.enabled")` 与 3 个 `@Bean`），与移除的宽扫描无关（原扫描本就 exclude rocketmq 包）——移除宽扫描对 RocketMQ 注册**零影响**。

**C. 验收对照（方案步骤验收）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| 步骤1 | 添加无 DataSource、无 MyBatis 的本地事件消费者测试 | ✅ | event-minimal：application.yml 排除 DataSourceAutoConfiguration + MybatisPlusAutoConfiguration |
| 步骤2 | 移除 EventAutoConfiguration 对整个 event 包的扫描 | ✅ | 宽 `@ComponentScan` 已删；grep 零命中 |
| 步骤3 | 显式声明 event-core Bean | ✅ | `springEventPublisher`/`eventTaskExecutor` 显式 `@Bean` + `@ConditionalOnMissingBean` |
| 步骤4 | Outbox 配置仅在模式为 outbox 且依赖满足时加载 | ✅ | OutboxEventConfiguration 三重门禁（mode=outbox + BaseMapper + DataSource） |
| 步骤5 | 使用模块专用 executor 和 scheduler 名称 | ✅ | `eventTaskExecutor`（grep 通用 `taskExecutor` 零命中） |
| 步骤6 | 本地模式不注册 Outbox Mapper、Repository、Dispatcher | ✅ | `localMode_noOutboxBeansInConditions` 消费者断言 + 单测 `localMode_noDataSource_startsAndNoOutboxBeans` |
| 验收① | local 模式无数据库可启动并发布事件 | ✅ | event-minimal Passed（context loads + publishEvent latch 接收） |
| 验收② | conditions 中没有 Outbox Bean | ✅ | 消费者 + 单测双断言 OutboxEventService/Repository/Configuration/MybatisPlusInterceptor 缺席 |

**D. 移交与残留**

- **mybatis-plus 硬依赖（EVT-002）**：`fast-ddd-event/pom.xml` 中 `mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser` 为**硬依赖（非 optional）**，其 `MybatisPlusAutoConfiguration` 在所有消费者强制要求 DataSource。event-minimal 以 `spring.autoconfigure.exclude` 刻画"无 DB local 消费者"；根因依赖边界（mybatis 改 optional 或拆 outbox 为独立模块）移交 **EVT-002**。
- **outbox 正向装配端到端（EVT-002）**：outbox 模式正向装配需完整 MyBatis 基础设施（DataSource + SqlSessionFactory + Mapper 扫描），本任务仅刻画装配边界（退让语义）；outbox 正向映射/持久化测试属 EVT-002。
- **RocketMQ @Component 注册（既有状态）**：`DeadLetterQueueHandler`/`RocketMQEventListener` 的 `@Component` 注册为既有行为（原宽扫描 exclude rocketmq，本就未扫到），EVT-001 未改、非回归；是否补显式 `@Bean` 留 EVT-002/003 评估。
- **@EnableScheduling 全局保留**：EventAutoConfiguration 类级 `@EnableScheduling` 为 RocketMQ outbox 调度所需，EVT-001 保留；调度边界精细化留后续任务。
- **下一 M1 任务**：SEC-001（依赖 FRM-002 ✅）/ SEC-002（依赖 FRM-001/002 ✅）/ MON-002（依赖 MON-001 ✅）/ EVT-002（依赖 EVT-001 ✅，已解锁）按依赖序推进。

### 16.12 MON-002 SQL 监控真实链路闭环快照

修复 MON-001 移交的 p6spy"双实例"根因（MessageFormattingStrategy 经反射实例化绕过 Spring DI，指标记录器字段恒 null，真实 SQL 不产生 Micrometer 指标），改为 **Spring 管理的 JDK `DataSource` 动态代理**，使监控链路成为真实闭环。

**A. 交付物**

| 类别 | 产物 |
|---|---|
| 双实例根因清除 | 移除 `fast-ddd-monitor/pom.xml` 的 `p6spy:p6spy:3.9.1` 依赖 + 删除 `spy.properties`（starter 全局生效资源）+ 删除 `SqlMonitoringMessageFormatter`（反射实例化绕过 DI 的缺陷类） |
| 伪 Controller 清除 | 删除 `SqlMonitoringController`（@RestController 伪 `/actuator/sql-monitoring/**`，无 AutoConfig @Bean，不经 exposure 治理） |
| Spring 管理 JDK 代理 | 新增 `SqlMonitoringJdbcProxy`（`java.lang.reflect.Proxy` 工厂，wrapDataSource/wrapConnection/wrapStatement；Statement 执行类方法计时，Connection 的 createStatement/prepareStatement/prepareCall 捕获 SQL）+ `SqlMonitoringProxied` 标记接口（代理类加载器以此 CL 为准，兼容平台 CL 的 JDBC 接口）+ `SqlMonitoringExecutionRecorder`（普通 Spring bean，record(sql,elapsed,url,success,err,rows) → metricsCollector + 脱敏结构化日志） |
| BeanPostProcessor | 新增 `SqlMonitoringDataSourcePostProcessor`（构造仅取 `ConfigurableListableBeanFactory` 早期可用，Recorder 双检锁惰性解析；`postProcessAfterInitialization` 包装非已代理的 DataSource；`getOrder()`=LOWEST_PRECEDENCE） |
| 真正 Actuator Endpoint | 新增 `SqlMonitoringEndpoint`（`@Endpoint(id="sqlmonitoring")`：`@ReadOperation` 输出 statistics+config+health，`@WriteOperation` 配置 enabled/prometheus/elk/slowQuery/reset）；由 `management.endpoints.web.exposure.include` 治理，默认 health-only 不可访问 |
| 默认关参数 + 脱敏 | `SqlMonitoringProperties.logParameters` 默认 true→false（Javadoc 注明）；新增 `SqlMaskingUtils.mask`（password/passwd/pwd/secret/token/api_key/access_key/credential/private_key/authorization 的赋值与 IN 列表正则脱敏为 `***`）；仅 `logParameters=true` 时日志携带 SQL 文本且经脱敏，**Micrometer 指标不含 SQL 文本** |
| 重置能力 | `SqlMetricsCollector.reset()` + `SqlMonitoringService.resetStatistics()` + Endpoint `@WriteOperation(reset=true)` 三路复位 |
| autoconfig 重构 | `SqlMonitoringAutoConfiguration` 改 `@AutoConfiguration @AutoConfigureAfter(DataSourceAutoConfiguration) @ConditionalOnClass({MeterRegistry,DataSource}) @ConditionalOnProperty(matchIfMissing=true)`；@Bean：collector/recorder/service/endpoint（均 @ConditionalOnMissingBean）+ 静态 `sqlMonitoringDataSourcePostProcessor`（`@ConditionalOnSingleCandidate(DataSource) @ConditionalOnBean(recorder)`） |
| 日志路由 | logback-spring.xml / logback-elk.xml / application-sql-monitoring.yml：`p6spy` logger → `com.gallop.fastddd.monitor.sql` |
| 单测 | `SqlMaskingUtilsTest`（6 用例）/ `SqlMonitoringAutoConfigurationTest`（5 用例 ApplicationContextRunner，含无 DataSource 退让 + 自定义 Service 退让 + 禁用空 Bean 图）/ `SqlMonitoringIntegrationTest`（2 用例）/ `SqlMonitoringExposureEnabledTest`（1 用例，包私有 MockMvc） |
| 消费者 | `monitor-minimal`：补 `spring-boot-starter-jdbc`（DataSourceAutoConfiguration 需 HikariCP+spring-jdbc 才创建 DataSource bean）；5 用例——MON-001 回归（contextLoads + 单一 Properties Bean）+ MON-002 真实链路（DataSource 代理断言 + 真实 SQL→Micrometer 指标增长 + Endpoint 装配） |

**B. 命令级证据**

- monitor 模块单测：**24 tests / 0 failures**（6 脱敏 + 5 autoconfig + 1 exposure-enabled + 2 集成 + 10 legacy）。
- **monitor-minimal 翻转**：修复前真实 SQL 不产生指标（p6spy 双实例）→ 修复后 **Passed:1**（5 用例全绿，含 `realSql_producesMicrometerMetrics` 断言真实 H2 SQL 后 `sql.queries.total` ≥ +3）。
- 全量 reactor `clean verify -Ptest`：**29/29 SUCCESS，0 failures / 0 errors**（0 回归）。
- 源码 grep：`p6spy`/`spy.properties`/`SqlMonitoringMessageFormatter`/`SqlMonitoringController` **零源码命中**（仅 README/yml/logback 注释与 Java Javadoc 解释迁移历史，保留作可追溯性说明）。
- JDK Proxy 类加载器坑实证：初用 `DataSource.class.getClassLoader()`（平台 CL，因 `javax.sql.DataSource` 属 java.sql 模块）→ `SqlMonitoringProxied referenced from a method is not visible from class loader: 'platform'`；改用 `SqlMonitoringProxied.class.getClassLoader()`（应用 CL 兼见两者）解决。
- 代理接口完整性实证：Statement 代理初仅实现 `Statement.class` → `(PreparedStatement) conn.prepareStatement(...)` ClassCastException；改 `proxyInterfaces(target)` 递归收集目标全部接口 + 追加标记接口（覆盖 PreparedStatement/CallableStatement/Wrapper/AutoCloseable）解决。
- datasource-proxy 库方案：本环境 API 核验受阻（Context7/zread/raw-GitHub 均报错），方案本就允许"Spring-managed DataSource Proxy"，故采手写 JDK 代理。

**C. 验收对照（方案步骤验收）**

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| 步骤1 | 决定 Spring 管理 DataSource Proxy 或可靠 P6Spy bridge | ✅ | 选 Spring 管理 JDK DataSource 动态代理（p6spy 双实例不可靠，datasource-proxy API 核验受阻） |
| 步骤2 | 移除/显式化 starter 全局 spy.properties | ✅ | spy.properties 已删 + p6spy 依赖移除，源码 grep 零命中 |
| 步骤3 | 管理接口改真正 Actuator Endpoint | ✅ | `@Endpoint(id="sqlmonitoring")` + @ReadOperation/@WriteOperation |
| 步骤4 | 接入 exposure 和权限控制 | ✅ | 默认 health-only 不可访问；`SqlMonitoringExposureEnabledTest`：未 include→404 / include=sqlmonitoring→200 |
| 步骤5 | 默认关闭 SQL 参数明文 | ✅ | `logParameters` 默认 false；指标不含 SQL 文本 |
| 步骤6 | 真实 SQL 断言指标与慢查询记录 | ✅ | `realSql_producesMicrometerMetrics`（模块集成测 + 消费者双断言 sql.queries.total/sql.execution.time Timer/sql.operations.total Counter） |
| 步骤7 | 密码/Token/敏感字段脱敏测试 | ✅ | `SqlMaskingUtilsTest`（6 用例：单引号 password / 大小写多键 / api_key+authorization / IN 列表 / 非敏感保留 / null 安全） |
| 验收① | 真实 SQL 能产生 Micrometer 指标 | ✅ | monitor-minimal + 模块集成测双证 |
| 验收② | 未暴露 Endpoint 时不可访问 | ✅ | ExposureEnabledTest 未 include→404 |
| 验收③ | 默认日志不包含敏感参数 | ✅ | logParameters 默认 false；启用时经 SqlMaskingUtils.mask |

**D. 移交与残留**

- **多 DataSource 消费者（未覆盖）**：`@ConditionalOnSingleCandidate(DataSource.class)` 在多候选场景整体退让，监控代理不装配。多数据源逐库代理化（含 RoutingDataSource）留后续任务；本任务仅刻画单候选主路径真实闭环。
- **ResultSet size 指标未采集**：JDK 代理当前包装 DataSource/Connection/Statement，未包装 ResultSet，故 `sql.resultset.size` 分布指标本任务不产生；ResultSet 包装（影响大、需处理游标生命周期）留后续任务。
- **Batch 计时为整批**：`executeBatch`/`executeLargeBatch` 计时为整批耗时，非逐条；逐条 batch 指标留后续。
- **INSERT...VALUES 位置脱敏未覆盖**：SqlMaskingUtils 覆盖 `key=value`/`key IN (...)` 命名脱敏，INSERT 位置参数（VALUES 第 N 个 `?`）未按列名脱敏；以 `logParameters` 默认 false 兜底，仅显式启用日志时依赖命名脱敏。
- **datasource-proxy 库替代**：本环境 API 核验受阻故采手写 JDK 代理；若后续环境可核验，datasource-proxy（成熟、覆盖 ResultSet）可作为增强替代项评估。
- **下一 M1 任务**：SEC-001（依赖 FRM-002 ✅）/ SEC-002（依赖 FRM-001/002 ✅）/ EVT-002（依赖 EVT-001 ✅，已解锁）/ EVT-003（依赖 EVT-002）按依赖序推进。
