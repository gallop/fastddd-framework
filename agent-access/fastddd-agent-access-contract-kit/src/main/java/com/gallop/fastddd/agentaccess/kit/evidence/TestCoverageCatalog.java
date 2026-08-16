package com.gallop.fastddd.agentaccess.kit.evidence;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * test ID 全集三分类覆盖目录（FDD-AA-15 验收对照）：每个 test ID 的
 * 承载层级——本仓显式测试 / kit conformance（宿主真表执行）/ 宿主或跨仓归属。
 *
 * <p>这是<b>登记</b>不是执行声明：{@link CoverageLevel#HOST_OR_CROSS_REPO}
 * 条目显式声明本仓不承载、不冒充（如 Python GV 逐字节复算归 FastAgent 侧、
 * 事务内资源终检归 V04-09 宿主编排）。半边承载的场景（骨架层已测的子集）
 * 在 {@code carriedBy} 注明半边边界。FDD-AA-16 Attestation 与 clean consumer
 * 的证据 manifest 以本目录为对照输入。</p>
 */
public final class TestCoverageCatalog {

    /** 承载层级 */
    public enum CoverageLevel {
        /** 本仓显式测试承载（测试类/方法为证；骨架层子集在 carriedBy 注明） */
        REPOSITORY_TEST,
        /** kit conformance 契约承载（抽象基类，宿主在真实表/事务上执行） */
        KIT_CONFORMANCE,
        /** 宿主实现或跨仓联测归属（本仓不承载，不冒充已执行） */
        HOST_OR_CROSS_REPO
    }

    /** 单条归属：testId + 层级 + 承载说明（测试类名或归属理由） */
    public record CoverageEntry(String testId, CoverageLevel level, String carriedBy) {
        public CoverageEntry {
            if (testId == null || testId.isBlank() || level == null
                    || carriedBy == null || carriedBy.isBlank()) {
                throw new IllegalArgumentException("testId/level/carriedBy 必选且非空白");
            }
        }
    }

    public static final List<CoverageEntry> ENTRIES = build();

    private static final Map<String, List<CoverageEntry>> BY_TEST_ID = ENTRIES.stream()
            .collect(Collectors.collectingAndThen(
                    Collectors.groupingBy(CoverageEntry::testId,
                            Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)),
                    Collections::unmodifiableMap));

    private TestCoverageCatalog() {
    }

    /** 全集每个 test ID 至少有一条归属（无遗漏）；多余/未知 ID 一律拒绝 */
    public static Map<String, List<CoverageEntry>> byTestId() {
        return BY_TEST_ID;
    }

    public static Set<String> coveredTestIds() {
        return byTestId().keySet();
    }

    private static List<CoverageEntry> build() {
        return List.of(
                // ---- AAC-01～18（Contract §12）----
                entry("AAC-01", CoverageLevel.REPOSITORY_TEST,
                        "AacQuadrantNegativeMatrixTest Q3（FastDDD DENY 半边 + 审计拒绝行）；真表 0 变更断言归 KIT_CONFORMANCE"),
                entry("AAC-01", CoverageLevel.KIT_CONFORMANCE, "PersistentDedupConformanceContract.misboundCompletionIsRejectedAtConstruction（拒绝路径零业务效果，宿主真表执行）"),
                entry("AAC-02", CoverageLevel.REPOSITORY_TEST,
                        "AacQuadrantNegativeMatrixTest Q2/Q4（无效许可拒绝 + 0 次准入读取）+ Envelope permitRef 骨架期 IAE"),
                entry("AAC-03", CoverageLevel.REPOSITORY_TEST,
                        "DefaultExecutionPermitVerifierTest（forged/expired/unknown-version → PERMIT_INVALID_OR_EXPIRED）"),
                entry("AAC-04", CoverageLevel.REPOSITORY_TEST,
                        "DefaultExecutionPermitVerifierTest（wrongAudience/tenant escalation/绑定逐项错配）+ DefaultStatusQueryServiceTest misbound 矩阵"),
                entry("AAC-05", CoverageLevel.REPOSITORY_TEST,
                        "AgentAccessArchitectureTest（B1/hostMayOnlyUseGovernedSeams）+ AgentAccessAssemblyValidatorTest（Bean 图无替代入口）"),
                entry("AAC-06", CoverageLevel.REPOSITORY_TEST,
                        "Aac06ResourceVersionDriftTest（绑定比对半边：版本漂移→PERMIT_BINDING_MISMATCH）；PRECONDITION_CHANGED/RESOURCE_VERSION_CONFLICT 完整收敛归宿主终检编排"),
                entry("AAC-06", CoverageLevel.HOST_OR_CROSS_REPO, "事务内资源终检（Contract §7 步骤 9/11）归宿主/V04-09"),
                entry("AAC-07", CoverageLevel.REPOSITORY_TEST, "InMemoryDedupStoreTest（同键同摘要稳定结果）"),
                entry("AAC-07", CoverageLevel.KIT_CONFORMANCE, "PersistentDedupConformanceContract.sameKeySameDigestReturnsStableResult（宿主真表执行）"),
                entry("AAC-08", CoverageLevel.REPOSITORY_TEST, "InMemoryDedupStoreTest（异摘要 IDEMPOTENCY_CONFLICT 不覆盖）"),
                entry("AAC-08", CoverageLevel.KIT_CONFORMANCE, "PersistentDedupConformanceContract.sameKeyDifferentDigestConflictsAndNeverOverwrites（宿主真表执行）"),
                entry("AAC-09", CoverageLevel.KIT_CONFORMANCE, "PersistentDedupConformanceContract.rolledBackTransactionLeavesNoSideEffects + Outbox 伴写钩子（宿主真表执行）"),
                entry("AAC-10", CoverageLevel.REPOSITORY_TEST, "DefaultStatusQueryServiceTest（UNKNOWN 收敛不换键不盲重放）"),
                entry("AAC-11", CoverageLevel.REPOSITORY_TEST,
                        "AuthorizationDecisionTest（dataScope 交空 DENY/numericCap 取 min——READ 行/字段/用途 DTO+Decision 层）；目标侧最小投影宿主半边见 HOST 条目"),
                entry("AAC-11", CoverageLevel.HOST_OR_CROSS_REPO, "READ 目标侧拒绝/最小投影执行归宿主（allowedProjection 消费义务）"),
                entry("AAC-12", CoverageLevel.REPOSITORY_TEST,
                        "Aac12HumanEntranceIndependenceTest（结构面：桥接无 Permit 耦合）；宿主运行时半边归 HOST"),
                entry("AAC-12", CoverageLevel.HOST_OR_CROSS_REPO, "人工入口宿主装配与运行时授权行为归宿主测试"),
                entry("AAC-13", CoverageLevel.REPOSITORY_TEST,
                        "DefaultExecutionPermitVerifierTest（换 operation/payload/precondition → PERMIT_BINDING_MISMATCH）"),
                entry("AAC-14", CoverageLevel.REPOSITORY_TEST,
                        "DefaultExecutionPermitVerifierTest（sender binding）+ InMemoryDedupStoreTest（并发重放返回原结果）；READ usage/budget 账本半边见 AST-07"),
                entry("AAC-15", CoverageLevel.REPOSITORY_TEST,
                        "LocalDecisionCacheTest.revisionDriftInvalidatesCache（缓存适用性半边）"),
                entry("AAC-15", CoverageLevel.HOST_OR_CROSS_REPO, "事务内终检撤 ACL/改归属的完整拒绝（聚合/去重/Outbox 零变更）归宿主/V04-09"),
                entry("AAC-16", CoverageLevel.REPOSITORY_TEST,
                        "DefaultStatusQueryServiceTest（伪造/错绑定 Receipt 六项完整性复验不收敛）+ BusinessActionStatusTest"),
                entry("AAC-17", CoverageLevel.REPOSITORY_TEST,
                        "DefaultReconciliationPermitVerifierTest（原 Permit 过期不扩大查询权限/workload 切换）+ DefaultStatusQueryServiceTest（NOT_VISIBLE）"),
                entry("AAC-18", CoverageLevel.REPOSITORY_TEST, "AacQuadrantNegativeMatrixTest（四象限仅双 PERMIT 进 dispatch 接缝）"),
                // ---- AST-01～08（AA-03 §3）----
                entry("AST-01", CoverageLevel.REPOSITORY_TEST,
                        "Ast01AdmissionFailClosedTest（PDP 超时/INDETERMINATE/断连三形态 fail-closed）+ AgentAccessAssemblyValidatorTest（Verifier Bean 缺失 fail-fast）"),
                entry("AST-01", CoverageLevel.KIT_CONFORMANCE, "DB/Outbox 断开真表回滚归 PersistentDedupConformanceContract.rolledBackTransactionLeavesNoSideEffects（宿主执行）"),
                entry("AST-02", CoverageLevel.REPOSITORY_TEST,
                        "SensitiveDataScannerTest（token/prompt/低熵/白名单 diff 四检测器）；命中事实进 EvidenceManifest"),
                entry("AST-03", CoverageLevel.REPOSITORY_TEST,
                        "DigestGoldenVectorsTest（GV-01～07 Java 侧逐字节复算）"),
                entry("AST-03", CoverageLevel.HOST_OR_CROSS_REPO,
                        "Python 侧逐字节复算归 FastAgent 跨仓联测；增补平面边界/大整数补充向量归后续 kit 版本（向量文档 §3/§5 勘误登记）"),
                entry("AST-04", CoverageLevel.REPOSITORY_TEST,
                        "VerifiedCallerSpiTest + CallerEntranceGovernanceTest（Agent 路径匿名降级拒绝，无 system 兜底）"),
                entry("AST-05", CoverageLevel.REPOSITORY_TEST,
                        "StaticDescriptorRegistryTest + DefaultAgentAccessGatewayTest.unregisteredActionIsRejectedAsActionNotExposed"),
                entry("AST-06", CoverageLevel.REPOSITORY_TEST,
                        "AgentAccessArchitectureTest（noFastAgentDependency + hostEgressMustNotTouchFastAgent）"),
                entry("AST-06", CoverageLevel.HOST_OR_CROSS_REPO, "FastAgent 仓同向锁定规则与 Egress 调用账本运行时断言归 V04-04"),
                entry("AST-07", CoverageLevel.REPOSITORY_TEST,
                        "DataBudget/ResourceConstraints DTO 负向 + AuthorizationDecisionTest numericCap（预算/上限 Decision 层）"),
                entry("AST-07", CoverageLevel.HOST_OR_CROSS_REPO,
                        "READ 时间窗/速率维度执行与预算账本持久化归宿主编排（本仓未实现，不冒充）"),
                entry("AST-08", CoverageLevel.REPOSITORY_TEST,
                        "DefaultStatusQueryServiceTest.bindingTombstoneStaysUnknownAndSurfacesTombstoneRef（原 binding 审计 + tombstone 引用）"),
                // ---- GV-01～07（向量文档 v1）----
                entry("GV-01", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-02", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-03", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-04", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-05", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-06", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-07", CoverageLevel.REPOSITORY_TEST, "DigestGoldenVectorsTest（Java 逐字节一致）"),
                entry("GV-01", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-02", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-03", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-04", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-05", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-06", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"),
                entry("GV-07", CoverageLevel.HOST_OR_CROSS_REPO, "Python 侧逐字节复算归 FastAgent 跨仓联测（GV-01～07 同）"));
    }

    private static CoverageEntry entry(String testId, CoverageLevel level, String carriedBy) {
        return new CoverageEntry(testId, level, carriedBy);
    }
}
