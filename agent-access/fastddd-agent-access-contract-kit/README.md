# fastddd-agent-access-contract-kit

FastDDD Agent Access 治理契约测试工具包（普通 jar）。**compile scope 传递**给宿主 test scope 的依赖只有：
`junit-jupiter-api`（conformance 基类编译域）、ArchUnit（`arch` 规则）、`spring-context`
（`AgentAccessAssemblyValidator` 需要）。Testcontainers/postgresql/assertj 与 junit-jupiter
引擎为 kit 自身 test scope，**不传递**——宿主在真实表上执行 conformance 基类时须自配
Testcontainers 与 JUnit 引擎。

## 定位与边界

本 kit 是 **FDD-AA-15 验收的执行载体**，不是新的执行语义实现：

- kit 只提供 conformance 抽象基类与负向载体（故障注入、扫描器、manifest 校验）；
- READ 编排（步骤 8）、预算账本、资源终检（步骤 9/11）归宿主与后续 V04 任务；
- 跨仓半边（Python GV 复算、FastAgent V04-04 联测、宿主真表 SQL 断言）在
  `TestCoverageCatalog` 中显式登记归属，不以本仓测试冒充。

## 工具面

| 包 | 内容 | 消费方式 |
| --- | --- | --- |
| `arch` | `AgentAccessArchitecture` 边界规则（B1、宿主治理接缝、AST-06 参数化 egress 规则） | 宿主 ArchUnit 测试调用 `checkMainArtifact` / `hostMayOnlyUseGovernedSeams` / `hostEgressMustNotTouchFastAgent` |
| `kit.conformance` | `PersistentDedupConformanceContract`（AAC-07/08/09、AST-01 真表回滚） | 宿主继承基类，实现 `OutcomeDedupPort` 的持久化适配并接真实表（Testcontainers） |
| `kit.fault` | `FaultInjection`（AST-01 抛错类故障计数注入） | 宿主内联到任意函数式治理 Port lambda |
| `kit.scan` | `SensitiveDataScanner`（AST-02 token/prompt/低熵/白名单 diff 四检测器；命中只报 kind+offset 不回显） | 宿主对 Evidence/Audit/Checkpoint 字段扫描，`scanText` / `scanFields` / `auditProjectionFieldNames()` |
| `kit.evidence` | `TestIdCatalog`（33 个 test ID 全集）、`EvidenceManifest` + `EvidenceManifestValidator`、`TestCoverageCatalog`（三分类归属登记） | 宿主组装证据 manifest 并在发布前校验（`sha256:` 小写 64 hex、PASSED 必须零命中） |
| `kit` | `AgentAccessAssemblyValidator`（Verifier Bean 缺失 fail-fast） | 宿主启动期装配校验 |

## 证据 manifest 使用

1. test ID 必须出自 `TestIdCatalog.ALL`（AAC-01～18 / AST-01～08 / GV-01～07）；
2. 「登记未执行」的 ID 放 `registeredOnlyTestIds`（与 `testResults` 键不相交由校验器强制）；
3. 制品坐标 + `sha256:` 小写 64 位十六进制摘要；`schemaVersion` 必须等于当前版本；
4. 任一 PASSED 且 `sensitiveScanFindings` 非空 → 校验失败（AST-02 零命中是 PASSED 的必要条件）；
5. 归属对照：`TestCoverageCatalog` 声明每个 ID 由本仓测试 / kit conformance（宿主执行）/ 宿主或跨仓承载。

## 消费证据边界（AA-04 §7 MC-8）

- reactor 内消费证明：本仓 `PostgreSqlDedupConformanceTest`（继承 conformance 基类 + Testcontainers）；
- **clean consumer**（空工程只依赖已安装 kit 制品）的消费证据归 FDD-AA-16；
- 禁止 test-jar classifier、`../` 相对路径偷渡、以本仓测试冒充宿主消费。
