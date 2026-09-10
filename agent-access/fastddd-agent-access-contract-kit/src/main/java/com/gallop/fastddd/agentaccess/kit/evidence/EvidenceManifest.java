package com.gallop.fastddd.agentaccess.kit.evidence;

import com.gallop.fastddd.agentaccess.kit.scan.ScanFinding;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kit 证据 manifest（AA-04 §7.4）：AAC/AST 执行记录 + 制品 checksum +
 * SQL 前后断言快照引用 + 敏感数据扫描结果的结构化载体，schema 随 kit 发布。
 * AA-05 R8：FDD-AA-15 后 Attestation 的「测试 ID」字段按本 schema 校验。
 *
 * <p><b>manifest 是宿主/fill 方填报的执行事实，kit 校验器只做 schema 与
 * 一致性校验，不生成任何执行事实</b>——「已执行」与「登记范围」经
 * {@code registeredOnlyTestIds} 显式区分（跨仓/宿主归属场景不冒充执行）。</p>
 *
 * @param schemaVersion            manifest schema 版本（kit 自有轴，当前 1；与 wire/digest/制品版本轴互不推导）
 * @param artifactCoordinates      被证明的 Maven 制品坐标（如 {@code io.github.gallop.fastddd:fastddd-agent-access:0.1.0-jdk21-alpha.1}）
 * @param artifactChecksum         制品 checksum（{@code sha256:} + 64 位小写 hex）
 * @param testResults              test ID → 执行结果（ID 必须出自 {@link TestIdCatalog} 全集）
 * @param registeredOnlyTestIds    只登记未执行的 test ID（归属宿主/跨仓，与 {@code testResults} 键不相交）
 * @param sqlAssertionSnapshotRefs SQL 前后断言快照引用（可空——非持久化 conformance 场景无 SQL 断言）
 * @param callLedgerRefs           调用账本（审计行/账本文件）引用
 * @param sensitiveScanFindings    AST-02 敏感扫描命中（PASSED 必须为空，由校验器强制）
 * @param generatedAt              填报时间
 */
public record EvidenceManifest(
        int schemaVersion,
        String artifactCoordinates,
        String artifactChecksum,
        Map<String, TestOutcome> testResults,
        Set<String> registeredOnlyTestIds,
        List<String> sqlAssertionSnapshotRefs,
        List<String> callLedgerRefs,
        List<ScanFinding> sensitiveScanFindings,
        OffsetDateTime generatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EvidenceManifest {
        testResults = testResults == null ? Map.of() : Map.copyOf(testResults);
        registeredOnlyTestIds = registeredOnlyTestIds == null ? Set.of() : Set.copyOf(registeredOnlyTestIds);
        sqlAssertionSnapshotRefs = sqlAssertionSnapshotRefs == null ? List.of() : List.copyOf(sqlAssertionSnapshotRefs);
        callLedgerRefs = callLedgerRefs == null ? List.of() : List.copyOf(callLedgerRefs);
        sensitiveScanFindings = sensitiveScanFindings == null ? List.of() : List.copyOf(sensitiveScanFindings);
    }
}
