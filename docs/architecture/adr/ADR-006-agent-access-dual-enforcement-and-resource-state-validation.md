# ADR-006：Agent 访问的双端策略执行、权威事实归属与资源状态校验

| | |
|---|---|
| **状态** | 已接受（Accepted）—— 2026-08-16 随 FDD-AA-05 Ratification 批准（owner gallop 签署，待签清单 R1）；十二项待决问题已由 AA-01 关闭，并采纳 AA-04 §3.3 登记的 Q7 修订（Agent Access PEP 对主线依赖收紧为仅 `fast-ddd-application` 一个 compile 接缝，`CallerIdentityBridge` 承载人工/SystemJob 桥接、Agent 路径禁用） |
| **日期** | 2026-08-15 |
| **适用范围** | FastDDD 业务宿主、FastAgent 治理运行时及二者在同进程/跨进程组合时的访问边界 |
| **关联决策** | FastDDD ADR-004；FastAgent ADR-009、ADR-010、ADR-011、ADR-012；FastAgent v0.4 FastDDD Egress 纵向样板 |
| **源码核对基线** | FastDDD `47157fe8c9c68c075af223e1c94c46fac693092a`（`v1.0.0-jdk21-rc1-4-g47157fe`） |

## 1. 背景

FastDDD 与 FastAgent 都是基础框架，最终会被组合进实际业务系统：

- FastAgent 管理 Agent 的意图、委托、风险、审批、执行绑定、调度、恢复和治理证据；
- FastDDD 承载业务应用服务、领域聚合、Repository、事务、数据权限和业务 Outbox；
- 实际部署既可能是同一个 Spring Boot 进程内组合，也可能是 FastAgent Gateway/Egress 与 FastDDD 业务宿主跨进程调用；
- 同一个 FastDDD 业务系统还会同时接受人工端、后台任务、服务间调用和 Agent 调用，不能假设所有入口永远经过 FastAgent。

需要回答的核心问题是：FastAgent 已经完成授权后，FastDDD 是否还需要本地授权和资源状态校验；如果两端都检查，如何避免策略重复、事实漂移和互相依赖。

本 ADR 冻结原则和职责边界，不定义完整 Capability Package/Profile，不选择具体 OAuth Authorization Server，也不在当前任务中修改代码、DDL 或发布范围。

## 2. 术语

| 术语 | 本 ADR 含义 |
|---|---|
| `Subject` | 最终承担业务责任的人、组织或服务主体 |
| `Actor` | 代表 Subject 行动的逻辑 Agent；不等同于模型进程或登录用户名 |
| `Workload` | 实际发出受治理调用的 Gateway/Egress/服务实例 |
| PDP | 根据权威事实做出策略决定的逻辑组件；可本地，也可为受治理的外部服务 |
| PIP | 提供身份、租户、资源状态、风险、委托等事实的权威来源或投影 |
| PEP | 在受保护边界执行允许/拒绝决定的本地策略执行点 |
| 治理授权 | FastAgent 判断某 Agent 在给定委托、目的、风险、审批和执行绑定下是否可以尝试一个 Action |
| 目标访问授权 | FastDDD 业务宿主判断当前调用主体是否可在本 tenant/environment 对指定 action/resource 执行 READ/WRITE |
| 资源前置条件 | 审批和执行所依赖的资源标识、状态、版本、金额、归属等最小事实 |
| 领域不变量 | 只有业务系统能权威判断的业务规则，例如已支付发票不得创建催收跟进 |

“本地授权”在本文中首先表示**本地 PEP 必须存在**，不表示两端都复制一份用户、角色、策略和业务数据。PDP/PIP 可以按权威归属位于不同组件，但每个受保护资源边界都必须执行自己的验证并 fail-closed。

## 3. 决策摘要

采用“**双端 PEP、单一事实权威、分层判定、目标端事务校验**”模型：

```text
允许产生业务效果
  = FastAgent 治理授权通过
  ∧ FastDDD 目标访问授权通过
  ∧ FastDDD 当前资源状态与领域不变量成立
  ∧ FastDDD 幂等、版本与事务提交成功
```

任一条件拒绝、未知、过期、无法验证或超时，都不能产生新的业务效果。

具体决策：

1. FastAgent 与 FastDDD 两端都保留本地 PEP，但不维护两份同名策略源；
2. FastAgent 的 `ALLOW` 是产生业务效果的必要条件，不是 FastDDD 必须执行的命令；
3. FastDDD 必须独立认证调用工作负载，并按 action、tenant、resource、purpose 和数据范围执行目标侧授权；
4. FastAgent 在审批前读取并绑定资源事实快照；该快照用于授权和审批，不成为资源当前状态的权威副本；
5. FastDDD 在实际业务事务中重新读取资源、检查当前状态和领域不变量，并用 expected version/CAS/乐观锁关闭 TOCTOU；
6. FastDDD 不复制 FastAgent 的 Agent 生命周期、风险、审批、委托编排和 Attempt 状态机；FastAgent 不复制 FastDDD 的业务 RBAC、数据权限、聚合状态机和领域规则；
7. 二者通过协议中立的调用信封、受限执行许可、稳定业务操作 ID、结果回执和对账契约组合，核心模块不形成相互依赖；
8. 同进程部署可以省略网络编码和远程 Token，但不得省略逻辑 PEP、主体校验、资源校验或旁路防护；
9. 跨进程部署必须使用短期、audience-restricted、必要时 sender-constrained 的工作负载凭据；不能仅凭内网、IP、共享 Secret 或 evidence reference 信任请求；
10. 所有受控业务 READ 和 WRITE 均适用本决策；READ 不能因“无副作用”而绕过租户、数据范围、用途和输出最小化；
11. `LOCAL_OBJECT` 只在 FastAgent Egress 与 FastDDD Agent Access Gateway 属于同一受信计算基（TCB）时成立；不可变对象、包可见性和 ArchUnit 只能防误接线与未授权依赖，不能抵御同 JVM 任意代码执行。若威胁模型要求抵御被攻陷的同进程组件，必须升级为进程、密钥和数据库凭证隔离的远程资源服务器 Profile。

## 4. 权威事实与责任矩阵

| 事实/决定 | 权威所有者 | FastAgent 行为 | FastDDD 行为 | 禁止做法 |
|---|---|---|---|---|
| Subject/Workload 身份真实性 | 企业 IdP/STS 或宿主身份设施 | 验证调用 Agent/Runtime/Egress；申请目标受限凭据 | 每次入口本地验证 issuer、audience、subject/actor/workload、TTL 和证明绑定 | FastDDD 信任请求体自报主体；匿名降级为 `system` |
| Agent 生命周期、Agent Grant、委托链 | FastAgent security domain | 权威读取、epoch/TTL/深度校验并在线性化点断言 | 验证许可中必要的受限结论/引用；不维护第二套委托图 | FastDDD 重建完整 Agent 治理；FastAgent 把委托明文全部发送给业务系统 |
| 风险、审批、职责分离、用途和 DataHandlingPolicy | FastAgent | PDP/Approval 权威；绑定 ActionIntent 和执行组合 | 验证 action/resource/purpose/有效期是否与目标许可一致；执行本地数据最小化 | FastDDD 只因存在 approvalId 就放行；FastDDD 复制审批工作流 |
| Action 是否对 Agent 开放及其业务契约 | FastDDD 业务宿主声明；FastAgent 可进一步收窄 | 只允许注册且已批准的 ActionDefinition/binding | 显式 allowlist；未暴露的 Controller/Handler/Repository 不可由 Agent 调用 | 自动暴露全部 CRUD、Controller 或 CommandHandler |
| tenant、资源归属、当前状态、当前版本 | FastDDD 业务数据/领域模型 | 审批前获取最小版本化快照并绑定 preconditions | 执行时权威重读，按 tenant/resource/version 检查 | FastAgent 保存一份资源镜像并作为最终权威 |
| 业务数据权限与字段范围 | FastDDD 业务授权/数据权限 | 约束预期数据分类、用途和下游处理 | 行/列/字段级校验与响应投影 | 仅由 FastAgent 过滤；FastDDD 返回全量后再脱敏 |
| 领域不变量 | FastDDD 聚合/Application Service | 不复制，只把必要事实用于规划和审批 | 业务事务内强制执行 | 在 FastAgent 中重写业务状态机并假定结果长期有效 |
| businessOperationId 与目标副作用唯一性 | FastAgent 生成稳定 ID；FastDDD 业务端持久去重 | 重试、重授权、恢复保持稳定 ID 和原 binding | tenant + businessOperationId 唯一；同 ID 同 digest 返回稳定结果，异 digest 冲突 | 以网络调用次数代表业务 exactly-once |
| Operation/Attempt/Receipt/Outcome | FastAgent Java Kernel | 唯一权威写者 | 只返回 invoke/query 事实，不写 FastAgent 权威表 | FastDDD/Egress 直接更新 FastAgent 状态 |
| 业务聚合、去重记录、业务 Outbox | FastDDD | 通过结果/对账观察 | 在同一业务事务提交或回滚 | 与 FastAgent runtime outbox 合表或 XA 双写 |
| Agent kill-switch | FastAgent | 阻止新授权/dispatch，推进治理 revision | 不把 Registry 变化误当成已发调用撤销 | 只改 Adapter binding 就宣称撤回在途调用 |
| 目标凭据撤销和本地业务封禁 | IdP/STS/FastDDD security | 获取短期凭据并响应失效 | 本地拒绝后续调用，必要时接受撤销事件/内省 | FastDDD 必须同步回调 FastAgent 才能拒绝 |

任何共享事实必须有一个权威 owner。另一端只能保存带版本、epoch、TTL、摘要或证据引用的最小投影，不能形成可独立修改的第二事实源。

## 5. 授权与执行时序

### 5.1 标准路径

```text
1. Agent/Runtime 产生 ActionProposal
2. FastAgent 从 FastDDD/业务 PIP 获取最小 ResourceFactSnapshot
3. FastAgent 规范化 Intent，执行 Risk/Policy/Approval
4. FastAgent 授权线性化点原子固定：
   Attempt + binding + preconditions + authorization digest + dispatch intent
5. Credential Broker/本地 PermitIssuer 产生目标受限 ExecutionPermit
6. FastDDD Agent Access PEP 验证主体、许可、tenant、action、resource、purpose、TTL
7. FastDDD 业务事务内：
   持久幂等检查 → 权威资源重读 → expectedVersion/CAS
   → 领域不变量 → 聚合修改 → business outbox
8. FastDDD 返回稳定 BusinessActionReceipt
9. FastAgent 追加 Receipt/Outcome；响应丢失时按原 tenant + businessOperationId 对账
```

步骤 2 的快照和步骤 7 的权威读取不是无意义重复：前者保证“批准的对象和风险明确”，后者保证“提交时业务事实仍然成立”。二者共同关闭授权后到业务提交前的状态漂移。

### 5.2 资源状态变化

例如 FastAgent 批准了：

```text
tenant=acme
resource=invoice-1001
status=OVERDUE
version=12
action=ar.createCrmFollowup
```

调用到达 FastDDD 前，发票被人工支付并变为 `PAID/version=13`。FastDDD 必须在事务内返回 `PRECONDITION_CHANGED` 或 `RESOURCE_VERSION_CONFLICT`，业务变更为零。FastAgent 收到后重新获取事实、重新评估风险并按需要重新审批，不能把它当普通瞬时错误盲目重试。

### 5.3 UNKNOWN 与事务边界

FastAgent 授权/dispatch 事务与 FastDDD 业务事务保持分离，不用 XA/JTA：

| 边界 | 权威内容 |
|---|---|
| A. FastAgent LP/dispatch | 治理事实断言、Attempt、binding、证据、dispatch intent |
| B. FastDDD 业务事务 | 持久去重、资源状态检查、聚合、业务 Outbox |
| C. FastAgent 结果收敛 | Receipt、Outcome、Attempt/Operation 条件迁移 |

边界 B 已提交但响应丢失时，FastAgent 进入/保持 UNKNOWN/MAY_HAVE_DISPATCHED，通过原 binding 和 businessOperationId 对账；不能重新授权第二 Attempt 后盲目执行。

### 5.4 两段式目标授权

目标授权必须区分两个执行点：

1. **事务外准入预检**：只使用已验证 Caller/Permit、action、tenant、purpose、静态资源约束和本地可安全读取的策略版本，拒绝未开放动作、错误租户/用途或无效许可；
2. **事务内资源授权终检**：tenant-scoped 权威读取资源后，使用当前资源归属、行/字段权限、业务封禁、entitlement revision 和领域状态再次判定。

远程 PDP 调用不得持有业务事务长锁；若目标权限来自外部 PDP/PIP，事务内必须能够验证其受限决定的 revision/epoch 仍未变化，否则 fail-closed。仅有 expected resource version 不能替代 ACL、归属和数据权限 revision。

### 5.5 Receipt、Status 与恢复授权

- Receipt/Status 是会驱动 FastAgent 权威 Outcome 的目标事实，必须绑定 issuer、audience、tenant、businessOperationId、action/version、业务效果摘要和原 binding；
- 跨信任边界必须使用目标签名/MAC，或由 Java Kernel 经认证的目标查询再次确认；验证失败只能保持 `UNKNOWN`，不能收敛成功；
- Status 使用独立的 `ReconciliationPermit`/`STATUS_QUERY` scope，不复用可能已经过期的执行 Permit；当前恢复 Workload 必须重新认证，并绑定原 tenant、subject/actor、action、audience、binding 和 businessOperationId；
- 本地 Profile 若把 Egress 与目标 Gateway 放在同一 TCB，必须把 TCB 内 Egress 被攻陷列为剩余风险，不能同时宣称 Receipt 可抵御同进程任意代码伪造。

## 6. ExecutionPermit 最小语义

FastAgent 与 FastDDD 之间需要一个协议中立的受限许可语义。具体承载可以是同进程不可变对象、签名 JWT、opaque token + introspection，或部署环境认可的等价机制。

建议最小字段：

| 字段 | 目的 |
|---|---|
| `schemaVersion` / `permitId` | 版本化解析和防重放标识 |
| `issuer` / `audience` | 只允许被点名发行方为指定 FastDDD 资源服务器签发 |
| `subject` / `actor` / `workload` | 区分责任主体、逻辑 Agent 和实际调用进程 |
| `tenantId` / `environment` | 绑定租户和环境，禁止 prod/non-prod 串用 |
| `actionId` / `actionVersion` | 绑定一个明确业务动作及版本 |
| `resourceConstraints` | 资源 ID、类型、数据范围或必要的授权细节 |
| `purpose` | 防止取得数据后改变用途 |
| `businessOperationId` / `idempotencyKey` | 跨重试、恢复和对账保持业务唯一性 |
| `expectedResourceVersions` / `preconditionsHash` | 将审批时事实与执行时 CAS/重检关联 |
| `authorizationEvidenceRef` / `authorizationDigest` | 引用治理证据，不复制完整 Approval/Prompt |
| `effectDigest` / `authorizationBindingDigest` | 分离稳定业务效果幂等语义与本次 Caller/Permit/binding/授权 revision 绑定 |
| `notBefore` / `expiresAt` | 限定最短必要生命周期 |
| `confirmation`/sender binding（如适用） | 将凭据绑定到 mTLS/DPoP/工作负载密钥 |

安全规则：

1. tenant、actor、workload、scope 等受保护字段必须来自已验证许可/安全上下文；若请求信封也携带，FastDDD 必须比对一致，不能信任请求体覆盖；
2. `authorizationEvidenceRef` 只是调查引用，不能单独充当授权凭据；
3. 不传完整 Prompt、Approval 内容、委托图、企业凭据或非必要业务 Payload；
4. permit 过期、未知版本、签名/issuer/audience/sender 不匹配、必要字段缺失均 fail-closed；
5. permit 验证成功不跳过 FastDDD 本地资源授权、数据权限、领域不变量和幂等校验；
6. 同进程对象也不能由 Agent/Controller 任意构造，应由受控 Issuer/Verifier 产生并验证；
7. Verifier 必须比较 caller、effect、actionVersion、businessOperationId、externalIdempotencyKey、preconditionsHash、expectedResourceVersions、authorizationDigest 和两个 digest，不能只比较 tenant/action/resource/purpose；
8. WRITE/TASK 只允许同 operation + 同 effectDigest 的受控重试；READ 必须显式冻结一次性或有限次数、时间窗、数据预算和速率限制；Status 不复用执行 Permit。

`effectDigest` 只覆盖稳定的业务效果语义，不包含 permitId、认证时间、证据引用、worker 实例、attempt/dispatch、lease/fencing、短期凭据或可轮换 binding；`authorizationBindingDigest` 绑定 Caller/Workload、Permit、原 binding、preconditions 和授权 revision。两者都必须使用版本化 canonical 规则和跨语言 golden vectors。

## 7. 部署拓扑

### 7.1 同一个业务应用进程

```text
GatewayExecutor
  → Captured Binding + verified ExecutionPermit
  → FastDDD AgentAccessInterceptor/Gateway
  → Application Service / CommandHandler
  → Aggregate / Repository / Business Outbox
```

要求：

- 可以使用本地接口替代 HTTP/JWT，但保留主体、许可和 PEP 语义；
- 校验必须位于应用服务/Agent Access Gateway 边界，而不只在 Controller；
- Agent、Controller、Listener、Scheduler 和普通业务 Bean 不能绕过 Agent Access Gateway 直接进入 Agent 专用调用路径；
- 普通人工和服务调用仍使用 FastDDD 自身本地授权，不能依赖 FastAgent 在线；
- 不把线程本地变量或调用方可写 Map 当作可信许可；异步、虚拟线程和消息恢复必须显式传播受限上下文。
- `LOCAL_OBJECT` 的 TCB 必须显式列出；模块可见性、sealed/package-private carrier、非通用 Spring Issuer、Bean 图和 ArchUnit 是误接线防护，不是对同 JVM 任意代码执行的强隔离证明。

### 7.2 跨进程业务宿主

要求：

- FastDDD 是受保护资源服务器，必须验证每次请求的 audience、action/resource、tenant、TTL 和工作负载身份；
- 优先使用短期、最小权限、audience-restricted 凭据；高风险场景按威胁模型采用 mTLS 或 DPoP 等 sender constraint；
- FastDDD 不以同步回调 FastAgent 作为所有请求的唯一授权方式，避免形成循环可用性依赖；
- 需要实时撤销时，可在独立 ADR 中选择短 TTL、token introspection、共享撤销存储或安全事件，但必须有明确失效上界；
- 网络位置、同一 Kubernetes namespace、IP allowlist 或共享数据库都不构成身份和授权。
- Receipt/Status 必须经目标侧完整性保护并绑定原请求；恢复查询使用单独的只读授权，不能仅凭 `(tenantId,businessOperationId)` 放行。

## 8. 失败分类与上游处置

FastDDD 应返回稳定、机器可判定且不泄露跨租户存在性的结果，而不是只返回自由文本异常。

| 结果码 | 语义 | 是否自动重试 | FastAgent 处置 |
|---|---|---|---|
| `TARGET_AUTHENTICATION_FAILED` | issuer/signature/audience/sender/TTL 等验证失败 | 否 | 安全事件；不得降级静态身份 |
| `TARGET_AUTHORIZATION_DENIED` | 当前主体无 action/resource/data 权限 | 否 | 拒绝或重新授权；不得换主体绕过 |
| `PRECONDITION_CHANGED` | 审批时资源事实与当前事实不一致 | 否 | 重新读取事实、重新 Risk/Policy/Approval |
| `RESOURCE_VERSION_CONFLICT` | expected version/CAS 失败 | 否，除非重新授权后 | 不用旧审批盲重放 |
| `BUSINESS_RULE_VIOLATION` | 领域不变量不允许操作 | 否 | 作为可解释业务结果返回 Agent |
| `IDEMPOTENCY_CONFLICT` | 同业务键对应不同 canonical digest | 否 | 完整性告警和人工调查 |
| `ALREADY_COMPLETED` | 同业务键同 digest 已完成 | 不需要 | 使用稳定业务引用收敛成功 |
| `TARGET_TEMPORARILY_UNAVAILABLE` | 明确未进入业务事务或可证明未提交 | 受冻结 retry policy 约束 | 原 Attempt、原 binding、原幂等键重试 |
| `UNKNOWN` | 可能已提交，无法证明成功或失败 | 禁止盲重试 | 只读对账；必要时 MANUAL_REVIEW |

所有拒绝都必须保证业务聚合、持久去重成功状态和业务 Outbox 没有新增提交；`UNKNOWN` 不能伪装成失败。

## 9. FastDDD 当前差距（改造参考）

以下结论基于本 ADR 顶部冻结的源码 SHA，仅说明差距，不在本 ADR 中授权实现：

1. `SecurityIdentityService` 只提供 `loadUserByUsername` / `getUserInfo(userId)`，主体模型偏人工用户，不能表达 Subject/Actor/Workload；
2. `SecurityPermissionService` 只有 `userId → roles/permissions`，缺 tenant/resource/purpose/delegation/action 级输入；
3. `SecurityCurrentUserProvider` 在未认证时返回字符串 `system`，Agent/受治理 WRITE 场景必须禁止匿名降级；显式系统任务应使用可审计的 `SYSTEM_JOB`/`WORKLOAD` 主体；
4. `Command.CommandMetadata` 虽包含 `userId`/`tenantId`，但缺完整、安全、不可变的构造契约；`getCommandId()` 在 metadata 缺失时每次可生成新 UUID，不适合稳定幂等和审计；
5. `AuditLogEntity` 以 `username`、原始 `requestParams`/`result` 为中心，缺 actor chain、tenant、action/version、businessOperationId、permit/evidence ref、digest、结果分类和数据最小化；
6. 当前没有协议中立的 Agent Access Gateway/Interceptor、ExecutionPermit verifier、BusinessActionReceipt、目标对账端口和 Agent 接入 Contract Test Kit；
7. 当前 `CommandHandler` 可以继续作为内部应用执行接缝，但不能自动扫描并全部暴露为 Agent tool/action；
8. FastDDD `docs/FASTDDD_AI_CONTRACT.md` 是 AI 辅助开发契约，不是运行时 Agent 访问契约，后续应增加范围声明或改为更不易混淆的名称。

## 10. 建议的 FastDDD 演进边界

### 10.1 基础语义层

优先冻结、但不急于绑定具体传输：

- `CallerContext`/`ActorChain`：区分 HUMAN、AGENT、WORKLOAD、SYSTEM_JOB 及责任主体；
- `ExecutionPermit` + `ExecutionPermitVerifier`：验证受限执行许可；
- `BusinessActionDescriptor`：显式声明哪些业务动作可被机器调用及其版本化输入/输出、READ/WRITE/TASK、幂等和对账语义；
- `BusinessActionEnvelope` / `BusinessActionReceipt` / `BusinessActionStatusQuery`；
- `TargetAdmissionAuthorizationPort` + `ResourceAuthorizationPort`：分别负责事务外 tenant/action/purpose 准入预检和事务内当前资源/data scope/entitlement revision 终检；
- `ResourcePrecondition`：资源版本和必要业务事实引用；
- 稳定错误分类、数据最小化和审计投影；
- Contract Test Kit 与架构门禁。

这些是业务访问契约，不等于动态 Capability Package/Profile、Marketplace、自动插件加载或 Agent 平台。

### 10.2 Adapter 层

建议顺序：

1. 先用真实 FastAgent → FastDDD 纵向样板验证本地/HTTP 契约；
2. 再提供可选 Web/OAuth Resource Server Adapter；
3. MCP 只作为 BusinessActionDescriptor 的可选工具适配层，其 tool annotations 只用于客户端提示，不作为服务器授权事实；
4. 只有 FastDDD 业务应用本身要表现为自治 Agent 时才引入 A2A Adapter；普通业务 Command 不需要 A2A；
5. 不自动暴露 Controller、CRUD、Repository、SQL 或全部 CommandHandler。

### 10.3 框架独立性

- FastDDD 不依赖 FastAgent：没有 FastAgent 时，人、服务和后台任务仍按 FastDDD 本地安全模型访问业务系统；
- FastAgent 不依赖 FastDDD：它仍可治理 CRM、邮件、MCP、数据库或其他业务系统；
- 可共享的是协议制品或 Adapter，不是相互反向依赖的 core；
- 若建立共享协议模块，其所有权、版本兼容和依赖方向需单独 ADR 冻结。

## 11. 与 FastAgent 现有决策的对齐

| FastAgent 决策 | 本 ADR 对齐方式 |
|---|---|
| ADR-009：所有受控企业 READ/WRITE 经 Gateway | 保留；FastDDD 本地授权不构成旁路，而是 Gateway 之后的目标侧 PEP |
| ADR-010：AuthorityContext、资源/金额/时间/网络/用途约束 | ExecutionPermit 只携带目标所需的最小受限投影，FastDDD 不复制完整治理模型 |
| ADR-011：完整 Capability 元模型延迟 | 本 ADR 只冻结业务访问和执行许可，不引入 Package/Profile/签名/SBOM/动态加载 |
| ADR-012：唯一授权线性化点、资源前置条件复验 | FastAgent LP 仍唯一；FastDDD 事务重检关闭 LP 后到业务提交之间的资源状态漂移 |
| v0.4 FastDDD 纵向样板：三事务、tenant + businessOperationId、真实 Outbox、UNKNOWN 对账 | 保留；增加目标访问授权、expected resource version/CAS 和明确失败分类验收 |

现有纵向样板中“FastDDD Security 不替代 FastAgent 授权”应保留并补上对偶表述：

> FastAgent 治理授权不能替代 FastDDD 的目标访问授权、租户/数据权限和事务内领域状态校验；两端任一 ALLOW 都不是单独的充分条件。

## 12. 必选验证矩阵

### 12.1 授权与旁路

1. FastAgent ALLOW、FastDDD 本地权限 DENY：业务变更为零；
2. FastDDD 本地角色允许、但无 FastAgent 有效许可的 Agent 调用：拒绝；
3. 人工 Controller、Listener、Scheduler、Runtime 或普通 Bean 直调 Agent 专用 Handler/Repository 路径：架构测试或运行时 PEP 拒绝；
4. forged/expired/unknown-version permit：拒绝；
5. issuer/audience/sender/action/resource/purpose/environment 任一不匹配：拒绝；
6. 错 tenant 请求：拒绝且不泄露资源是否存在；
7. FastDDD 授权依赖不可用且无法验证：fail-closed；
8. FastAgent 不可用时，FastDDD 非 Agent 正常业务入口仍按自身授权工作。

### 12.2 资源状态与事务

1. 审批后、调用前资源版本变化：`PRECONDITION_CHANGED`/`RESOURCE_VERSION_CONFLICT`，零副作用；
2. 资源已关闭/支付/冻结：领域拒绝，零副作用；
3. 同 tenant + businessOperationId + 同 digest 并发：聚合变化一次，返回稳定引用；
4. 同业务键异 digest：`IDEMPOTENCY_CONFLICT`，不覆盖原记录；
5. Outbox insert 失败：聚合、去重和 Outbox 全部回滚；
6. 业务事务 commit 后响应丢失：FastAgent UNKNOWN，经只读对账收敛，不重复业务效果；
7. READ Action 验证行/字段范围、用途和输出数据分类，禁止全量返回后再依赖 FastAgent 脱敏。

### 12.3 审计和数据最小化

1. 可按 tenant、businessOperationId、action、subject/actor/workload 和 trace 关联两个框架证据；
2. 不记录原始 access token、企业凭据、完整 Prompt/Approval/委托链或非白名单业务数据；
3. permit/evidence 只记录 ID、版本、摘要和必要结果，不把授权材料复制进业务 Outbox；
4. 同 ID 不同 digest 产生完整性事件，不 last-write-wins；
5. 审计拒绝结果不泄露其他 tenant 的资源存在性。

## 13. 分阶段建议

| 阶段 | 交付 | 明确不做 |
|---|---|---|
| Phase 0：设计冻结 | 本 ADR 评审；Agent Access Contract v0.1；威胁模型；字段/错误/测试矩阵 | 生产代码、DDL、通用 MCP/A2A |
| Phase 1A：FastDDD Integration Ready | CallerContext、Permit verifier、两段式目标授权 SPI、Descriptor/Gateway、Receipt/Status 契约、审计投影、Contract Test Kit 和可消费候选制品 | 具体业务聚合、宿主 DDL、自动暴露 Handler、完整 Capability 元模型 |
| V04-09：Named Host Vertical Slice | 被点名宿主实现 `ar.createCrmFollowup`、真实业务事务/dedup/Outbox/Status Provider，并通过双端 PEP 与故障矩阵 | 在集成任务中临时发明第二套安全 API |
| Phase 2：传输适配 | 可选 Web/OAuth Resource Server、MCP Adapter；按真实需求评估 A2A | 把 MCP annotations/Agent Card 当授权真相 |
| Phase 3：生态触发后 | 多 Provider/租户能力组合、签名/SBOM/发布状态机另立 ADR | 借 Agent Access 顺手实现 Marketplace/动态插件 |

FastDDD 当前仍处于 1.0 RC 阶段，公共主体、Command Context、错误和审计语义若要进入 1.x 长期兼容面，应在 GA 前完成是否冻结的独立评审；这不等于要求在 1.0 GA 前交付 OAuth2 Resource Server 或完整 Agent Access Adapter。FastDDD ADR-004 对 OAuth2 资源服务器的延后结论继续有效，除非新的发布决策明确覆盖。

## 14. 被拒绝的方案

### 14.1 只在 FastAgent 授权，FastDDD 完全信任上游

拒绝。FastDDD 还有人工、服务和消息入口；上游可能配置漂移、被绕过或被攻陷。更关键的是 FastAgent 无法在业务事务提交时权威判断资源当前状态。

### 14.2 两端复制相同 RBAC、委托、审批和业务规则

拒绝。会形成双写、版本漂移、冲突 ALLOW/DENY 和不可解释故障；无法回答哪个决定是权威。

### 14.3 FastDDD 每次同步回调 FastAgent 做最终授权

拒绝作为默认架构。它形成循环可用性依赖，且仍不能替代 FastDDD 本地数据权限和领域不变量。高风险 token introspection 可以作为单独、显式的部署选择。

### 14.4 同进程部署省略 FastDDD PEP

拒绝。同 JVM 不是信任证明，且 Bean、Controller、Listener、Scheduler 和测试夹具都可能形成旁路；未来拆进程还会迫使业务代码重写。

### 14.5 审批时状态合法就不在提交时复验

拒绝。审批与提交之间存在不可消除的 TOCTOU；必须以 expected version/CAS 和领域事务重检关闭。

### 14.6 以通用 CRUD/MCP tool 自动暴露解决 Agent 接入

拒绝。发现和传输不等于授权；自动暴露扩大攻击面并绕过业务用例、数据最小化、幂等和领域不变量。

## 15. 评审入口与待决问题

本 ADR 进入 Accepted 前必须确认：

1. FastDDD `CallerContext` 是替换还是扩展当前 user-oriented Security SPI；兼容和弃用周期是什么；
2. 同进程 ExecutionPermit 的发行/验证所有者，以及如何阻止任意 Bean 构造；
3. 跨进程首选 credential 形态、issuer、audience、sender constraint 和撤销失效上界；
4. `BusinessActionDescriptor` 与 FastAgent `ActionDefinition` 的字段映射、版本和所有权；
5. ResourceFactSnapshot 的最小字段、版本/ETag 和数据分类；
6. FastDDD 本地 RBAC/ABAC/数据权限与外部 PDP 的组合优先级；
7. Agent Access PEP 应位于独立 Framework 模块、security starter、application 模块还是具体业务宿主；
8. 稳定错误码是否进入 FastDDD 1.x 公共兼容承诺；
9. Phase 1A/SEC-1A 与 V04-09 SEC-1B 的边界、owner 和目标宿主是谁；
10. 哪些基础 API 必须在 FastDDD 1.0 GA 前冻结，哪些明确延后至 1.1+；
11. `effectDigest`、`authorizationBindingDigest`、READ 重放上限和 canonical golden vectors；
12. Receipt/Status 完整性机制、独立恢复授权和 Gate Attestation 的签发/撤销/失效规则。

缺少上述决定时，不得以“先加一个 `AGENT` 枚举”“先把 CommandHandler 暴露成 MCP tool”代替架构设计。认证通道不等于主体模型，工具发现也不等于授权。

## 16. 参考

- FastDDD ADR-004：`docs/architecture/adr/ADR-004-security-jwt-model.md`
- FastDDD AI 辅助开发契约：`docs/FASTDDD_AI_CONTRACT.md`
- FastAgent ADR-009：`docs/adr/ADR-009-authority-and-data-egress-control.md`
- FastAgent ADR-010：`docs/adr/ADR-010-command-envelope-and-state-machine.md`
- FastAgent ADR-011：`docs/adr/ADR-011-governed-extension-via-archunit-not-meta-model.md`
- FastAgent ADR-012：`docs/adr/ADR-012-execution-binding-and-evidence.md`
- FastAgent v0.4 FastDDD Egress 方案：`.taskmaster/docs/fastagent-v04-fastddd-egress-vertical-slice-execution-plan.md`
- NIST SP 800-207, Zero Trust Architecture: <https://csrc.nist.gov/pubs/sp/800/207/final>
- NIST SP 800-207A, Cloud-Native Access Control: <https://csrc.nist.gov/pubs/sp/800/207/a/final>
- RFC 9700, OAuth 2.0 Security Best Current Practice: <https://www.rfc-editor.org/rfc/rfc9700.html>
- RFC 8693, OAuth 2.0 Token Exchange: <https://www.rfc-editor.org/rfc/rfc8693.html>
- RFC 9396, OAuth 2.0 Rich Authorization Requests: <https://www.rfc-editor.org/rfc/rfc9396.html>
