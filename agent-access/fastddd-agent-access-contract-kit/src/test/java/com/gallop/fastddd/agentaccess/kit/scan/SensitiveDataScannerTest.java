package com.gallop.fastddd.agentaccess.kit.scan;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AST-02 敏感数据扫描器：四类检测命中/豁免/白名单 diff/不回显命中文本。
 */
class SensitiveDataScannerTest {

    private final SensitiveDataScanner scanner = new SensitiveDataScanner();

    @Test
    void tokenPatternsAreDetected() {
        List<String> dirty = List.of(
                "Authorization: Bearer abcdef0123456789abcdef",
                "api_key = hZk9mQ2xVbN7sL4pT3wY",
                "password: Sup3rS3cretValue!",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4",
                "key=AKIAIOSFODNN7EXAMPLE",
                "token ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234");
        for (String text : dirty) {
            List<ScanFinding> findings = scanner.scanText("row", text);
            assertTrue(findings.stream().anyMatch(f -> f.kind() == ScanFinding.FindingKind.TOKEN_PATTERN),
                    "应命中 token 模式：" + text.substring(0, Math.min(12, text.length())));
            assertFalse(scanner.isClean(findings));
        }
    }

    @Test
    void promptKeywordsAreDetected() {
        List<String> dirty = List.of(
                "<|im_start|>system",
                "### Instructions for the assistant",
                "You are an AI assistant.",
                "the system prompt says");
        for (String text : dirty) {
            assertTrue(scanner.scanText("log", text).stream()
                            .anyMatch(f -> f.kind() == ScanFinding.FindingKind.PROMPT_KEYWORD),
                    "应命中 prompt 关键词");
        }
    }

    @Test
    void bareHexDigestIsDetectedButAnnotatedAndUuidAreExempt() {
        // 裸 sha-256 长度 hex：命中
        String bare = "digest=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertTrue(scanner.scanText("outbox", bare).stream()
                .anyMatch(f -> f.kind() == ScanFinding.FindingKind.LOW_ENTROPY_DIGEST));
        // 带治理前缀标注：豁免
        String annotated = "digest=sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertTrue(scanner.isClean(scanner.scanText("outbox", annotated)), "带前缀标注摘要应豁免");
        // UUID 治理标识：豁免
        String uuid = "id=123e4567-e89b-42d3-a456-426614174000";
        assertTrue(scanner.isClean(scanner.scanText("audit", uuid)), "UUID 标识应豁免");
    }

    @Test
    void bareBase64IsDetected() {
        String bare = "payload=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5eg==";
        assertTrue(scanner.scanText("outbox", bare).stream()
                .anyMatch(f -> f.kind() == ScanFinding.FindingKind.LOW_ENTROPY_DIGEST));
    }

    @Test
    void cleanGovernedProjectionTextPasses() {
        // 治理投影形态（§10 白名单字段的正常值）：0 命中
        String clean = "tenant=acme action=ar.createCrmFollowup outcome=TARGET_AUTHORIZATION_DENIED "
                + "trace=123e4567-e89b-42d3-a456-426614174000 ref=sha256:e3b0c44298fc1c14";
        assertTrue(scanner.isClean(scanner.scanText("audit-row-1", clean)));
    }

    @Test
    void fieldNotInWhitelistIsReportedAndValueIsAlsoScanned() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tenantId", "acme");
        fields.put("outcomeCode", "PERMIT_BINDING_MISMATCH");
        fields.put("fullPrompt", "please summarize the invoice");      // 白名单外（本身内容干净）
        fields.put("senderProof", "Bearer abcdef0123456789abcdef");   // 白名单外 + 值命中 token
        List<ScanFinding> findings = scanner.scanFields("audit-row-2",
                fields, Set.of("tenantId", "outcomeCode"));
        assertTrue(findings.stream().anyMatch(f -> f.kind() == ScanFinding.FindingKind.FIELD_NOT_WHITELISTED
                && f.location().equals("field:fullPrompt")));
        assertTrue(findings.stream().anyMatch(f -> f.kind() == ScanFinding.FindingKind.FIELD_NOT_WHITELISTED
                && f.location().equals("field:senderProof")));
        assertTrue(findings.stream().anyMatch(f -> f.kind() == ScanFinding.FindingKind.TOKEN_PATTERN));
        // 白名单内字段全部通过
        Map<String, String> governed = new LinkedHashMap<>();
        governed.put("tenantId", "acme");
        governed.put("outcomeCode", "BUSINESS_RULE_VIOLATION");
        assertTrue(scanner.isClean(scanner.scanFields("audit-row-3",
                governed, Set.of("tenantId", "outcomeCode"))));
    }

    @Test
    void findingsNeverEchoMatchedContent() {
        String secret = "api_key = hZk9mQ2xVbN7sL4pT3wY";
        for (ScanFinding finding : scanner.scanText("row", secret)) {
            assertFalse(finding.toString().contains("hZk9mQ2x"),
                    "finding 不得回显命中文本");
        }
    }

    @Test
    void auditProjectionWhitelistMatchesFrozenSection10() {
        // Contract §10 十字段冻结对照（动态推导防双源漂移，冻结列表防白名单被静默改写）
        assertEquals(Set.of("tenantId", "businessOperationId", "actionId", "actionVersion",
                        "subjectId", "actorId", "workloadId", "permitId", "traceId", "outcomeCode"),
                SensitiveDataScanner.auditProjectionFieldNames());
    }

    @Test
    void blankOrMissingArgumentsAreProgrammingErrors() {
        assertThrows(IllegalArgumentException.class, () -> scanner.scanText(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> scanner.scanText("  ", "x"));
        assertThrows(IllegalArgumentException.class, () -> scanner.scanFields("row", null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> scanner.scanFields("row", Map.of(), null));
        // 空文本/空字段集：0 命中，不是错误
        assertTrue(scanner.isClean(scanner.scanText("row", "")));
        assertTrue(scanner.isClean(scanner.scanText("row", null)));
    }

    @Test
    void scanFindingRejectsBlankComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScanFinding(null, "row", "offset:0"));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanFinding(ScanFinding.FindingKind.TOKEN_PATTERN, " ", "offset:0"));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanFinding(ScanFinding.FindingKind.TOKEN_PATTERN, "row", " "));
    }
}
