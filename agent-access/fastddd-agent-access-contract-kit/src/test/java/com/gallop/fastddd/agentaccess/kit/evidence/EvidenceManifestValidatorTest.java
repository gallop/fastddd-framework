package com.gallop.fastddd.agentaccess.kit.evidence;

import com.gallop.fastddd.agentaccess.kit.scan.ScanFinding;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * manifest schema 校验：完备性/ID 全集/执行与登记不相交/PASSED 必要条件
 * （AST-02 0 命中）/未知版本 fail-closed。
 */
class EvidenceManifestValidatorTest {

    private final EvidenceManifestValidator validator = new EvidenceManifestValidator();

    private static EvidenceManifest manifest(Map<String, TestOutcome> testResults,
                                             Set<String> registeredOnly) {
        return new EvidenceManifest(1,
                "com.gallop.fastddd:fastddd-agent-access:0.1.0-jdk21-alpha.1",
                "sha256:" + "a".repeat(64),
                testResults, registeredOnly,
                List.of("surefire-conformance-snapshot.json"),
                List.of("audit-ledger-run-42.jsonl"),
                List.of(),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void validManifestPasses() {
        assertTrue(validator.isValid(manifest(
                Map.of("AAC-08", TestOutcome.PASSED, "AST-02", TestOutcome.PASSED),
                Set.of("GV-01"))));
    }

    @Test
    void unknownSchemaVersionFailsClosed() {
        List<String> issues = validator.validate(new EvidenceManifest(2,
                "g:a:1", "sha256:" + "a".repeat(64),
                Map.of(), Set.of(), List.of(), List.of(), List.of(),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC)));
        assertTrue(issues.stream().anyMatch(i -> i.contains("schemaVersion")));
    }

    @Test
    void blankCoordinatesOrMalformedChecksumAreIssues() {
        List<String> issues = validator.validate(new EvidenceManifest(1,
                " ", "sha256:ABC", Map.of(), Set.of(), List.of(), List.of(), List.of(),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC)));
        assertTrue(issues.stream().anyMatch(i -> i.contains("artifactCoordinates")));
        assertTrue(issues.stream().anyMatch(i -> i.contains("artifactChecksum")));
        // 大写 hex 不接受（冻结小写形态）
        List<String> upperCase = validator.validate(new EvidenceManifest(1, "g:a:1",
                "sha256:" + "A".repeat(64), Map.of(), Set.of(), List.of(), List.of(), List.of(),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC)));
        assertTrue(upperCase.stream().anyMatch(i -> i.contains("artifactChecksum")));
    }

    @Test
    void testIdsOutsideCatalogAreRejected() {
        List<String> issues = validator.validate(manifest(
                Map.of("AAC-19", TestOutcome.PASSED), Set.of("AST-09")));
        assertTrue(issues.stream().anyMatch(i -> i.contains("AAC-19")));
        assertTrue(issues.stream().anyMatch(i -> i.contains("AST-09")));
        // 集合内 ID（边界值）通过
        assertTrue(validator.isValid(manifest(
                Map.of("AAC-18", TestOutcome.PASSED, "AST-08", TestOutcome.PASSED, "GV-07", TestOutcome.SKIPPED),
                Set.of("AAC-01"))));
    }

    @Test
    void executedAndRegisteredOnlyMustNotOverlap() {
        List<String> issues = validator.validate(manifest(
                Map.of("AST-03", TestOutcome.PASSED), Set.of("AST-03")));
        assertTrue(issues.stream().anyMatch(i -> i.contains("既宣称执行又只登记")));
    }

    @Test
    void passedRequiresCleanSensitiveScan() {
        ScanFinding finding = new ScanFinding(ScanFinding.FindingKind.TOKEN_PATTERN,
                "audit-row-9", "offset:17");
        List<String> issues = validator.validate(new EvidenceManifest(1,
                "g:a:1", "sha256:" + "a".repeat(64),
                Map.of("AST-02", TestOutcome.PASSED), Set.of(),
                List.of(), List.of(), List.of(finding),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC)));
        assertTrue(issues.stream().anyMatch(i -> i.contains("AST-02")));
        // 仅 FAILED/SKIPPED 时扫描命中不构成结构矛盾（如实记录失败现场）
        assertTrue(validator.isValid(new EvidenceManifest(1,
                "g:a:1", "sha256:" + "a".repeat(64),
                Map.of("AST-02", TestOutcome.FAILED), Set.of(),
                List.of(), List.of(), List.of(finding),
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC))));
    }

    @Test
    void nullManifestOrMissingTimestampAreIssues() {
        assertEquals(1, validator.validate(null).size());
        List<String> issues = validator.validate(new EvidenceManifest(1,
                "g:a:1", "sha256:" + "a".repeat(64),
                Map.of(), Set.of(), List.of(), List.of(), List.of(), null));
        assertTrue(issues.stream().anyMatch(i -> i.contains("generatedAt")));
    }

    @Test
    void nullCollectionsNormalizeButZeroEvidenceManifestIsRejected() {
        // null 集合归一化为空集合（构造不抛），但双空=零证据空壳，不得通过门禁
        EvidenceManifest normalized = new EvidenceManifest(1,
                "g:a:1", "sha256:" + "a".repeat(64),
                null, null, null, null, null,
                OffsetDateTime.of(2026, 8, 16, 6, 0, 0, 0, ZoneOffset.UTC));
        List<String> issues = validator.validate(normalized);
        assertTrue(issues.stream().anyMatch(i -> i.contains("零证据")));
        assertFalse(validator.isValid(normalized));
    }

    @Test
    void catalogCoversExactFrozenSet() {
        // 33 = AAC 18 + AST 8 + GV 7；格式与边界逐项断言
        assertEquals(33, TestIdCatalog.ALL.size());
        assertTrue(TestIdCatalog.isKnown("AAC-01") && TestIdCatalog.isKnown("AAC-18"));
        assertTrue(TestIdCatalog.isKnown("AST-01") && TestIdCatalog.isKnown("AST-08"));
        assertTrue(TestIdCatalog.isKnown("GV-01") && TestIdCatalog.isKnown("GV-07"));
        assertFalse(TestIdCatalog.isKnown("AAC-00"));
        assertFalse(TestIdCatalog.isKnown("AAC-1"));
        assertFalse(TestIdCatalog.isKnown("aac-01"));
        assertFalse(TestIdCatalog.isKnown(null));
    }
}
