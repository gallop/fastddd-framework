# ADR-007：Agent Access 协议制品所有权与依赖方向

| | |
|---|---|
| **状态** | 已接受（Accepted）—— 2026-08-16 随 FDD-AA-05 Ratification 批准（owner gallop 签署，待签清单 R3） |
| **日期** | 2026-08-16 |
| **适用范围** | FastDDD Agent Access 协议制品（Contract DTO、错误码、Contract Kit）与 FastAgent v0.4 dispatch/Permit 载体的所有权边界 |
| **关联决策** | ADR-006；`FASTDDD_AGENT_ACCESS_CONTRACT_V0.1.md` §11 条 5（「另立 ADR」程序）；`FASTDDD_AGENT_ACCESS_AA04_MODULE_AND_COMPAT_PLAN.md` §8.1（本 ADR 的直接素材）；FastAgent ADR-012。上述 AGENT_ACCESS 过程文档已于 2026-09-12 移至仓外私有档案 |
| **关闭的候选** | `FASTDDD_AGENT_ACCESS_AA02_EGRESS_FIELD_MAPPING.md` §5.3「协议制品所有权候选」——自本 ADR 接受起由本文定稿替代 |

## 1. 背景与决策问题

AA-02 冻结 Contract v0.1 时，协议制品的所有权（哪些 DTO/测试资产随哪一侧制品发布）留为候选。Contract §11 条 5 冻结条款要求该项以独立 ADR 承载。同时 FastAgent v0.4 规划了 dispatch artifact（V04-04）与 Permit carrier（V04-07）两类载体。若两侧协议类型互相依赖或所有权含混，会破坏「两侧 core 互不依赖」的架构不变量，并在 experimental 版本线（`0.1.0-jdk21-alpha.1`）与 FastAgent 版本线之间制造不必要的耦合。

## 2. 决策

1. **Contract Kit 与协议 DTO 归 FastDDD 侧 L1 制品**：CallerContext、ExecutionPermit 载体接口、BusinessActionDescriptor、BusinessActionEnvelope、ResourcePrecondition、ResourceFactSnapshot、BusinessActionReceipt/Status、十二外部结果码与内部 reason 常量、digest 域标签/schemaVersion 常量，以及 Contract Kit（AAC/AST 测试基类、golden vectors 资源、conformance fixture、证据 manifest schema），全部随 `com.gallop.fastddd:fastddd-agent-access` / `fastddd-agent-access-contract-kit` 制品发布（AA-04 §2.1 坐标）。
2. **dispatch artifact / Permit carrier 归 FastAgent v0.4（L3）**：`GatewayEgressCommand` 载体升级（V04-04）与 Permit carrier（V04-07）是 FastAgent 侧治理执行链的组成部分，所有权归 FastAgent 仓 v0.4 任务；它们消费本 Contract 冻结的字段语义，但不反向定义目标侧协议。
3. **两侧 core 互不依赖**：`fastagent-core` 不依赖 `fastddd-agent-access*`，FastDDD 主线模块对 FastAgent 制品零坐标零代码路径（AA-04 §3.3 禁止依赖清单 + MC-2 主 reactor 零改动）。跨仓只交换契约文档引用、制品版本/hash 与测试证据，不复制任务状态。
4. **三层边界引用**：L1 FastDDD Agent Access 制品 / L2 业务宿主 Provider（`ar.createCrmFollowup` Descriptor 归宿主，不进 L1 通用制品）/ L3 FastAgent Adapter，逐层定义见 AA-04 §8.1。

## 3. 被拒方案

- **两侧共享一个协议 jar（第三制品）**：制造第三条版本线与双向耦合，experimental 线（0.1.x）无法与 FastAgent 版本线独立演进；违反 Contract §11.5「两侧 core 互不依赖」。
- **协议 DTO 归宿主（L2）私有**：每个宿主各自复制协议类型，字段语义漂移不可检测，golden vectors 无统一实现基准。
- **dispatch artifact 归 FastDDD 侧**：把 FastAgent 治理执行链的内部载体移出权威仓，破坏「LLM/Runtime 无企业执行权、dispatch 只经 FastAgent Gateway」的不变量。

## 4. 反例（必须拒绝）

- 任何让 `fastddd-agent-access*` compile 依赖 `com.gallop.fastagent:*` 或反向依赖的 POM 变更（Enforcer banned-dependencies + ArchUnit AST-06 断言）；
- FastAgent 侧在 V04-04/V04-07 之外另行定义与 Contract §5 冲突的第二套协议字段；
- 以「兼容」名义把 dispatch artifact 类编入 `fastddd-agent-access` 制品。

## 5. 后果

- FDD-AA-13（Envelope/双 digest）与 FDD-AA-15（Kit 制品化）的实现以本文所有权边界为前提；
- FastAgent V04-04/V04-07 消费 Contract v0.1 冻结语义时，以契约文档版本（`fastddd-agent-access/0.1`）与本文为引用基线，两侧制品只通过坐标与 checksum 互证；
- 若未来需要跨仓共享新的协议类型，必须另立 ADR 并同时升级两侧契约文档版本，不得在任一侧静默新增。
