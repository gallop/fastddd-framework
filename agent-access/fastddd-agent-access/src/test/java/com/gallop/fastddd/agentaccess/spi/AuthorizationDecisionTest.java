package com.gallop.fastddd.agentaccess.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthorizationDecision} 合成规则测试（deny-overrides / INDETERMINATE 按拒绝 /
 * 允许集合求交 / 义务合成：审计求并、数据范围求交、数值上限取 min）。
 *
 * <p><b>AAC-01 验收层级如实标注</b>：本测试在骨架期证明「上游 PERMIT +
 * 本端 DENY/INDETERMINATE → 不放行」的双端 PEP <i>语义</i>；完整
 * 「上游 ALLOW 不得替代本端判定、业务表 0 变更」验收需要 FDD-AA-12/16
 * 宿主接线后补端到端证据，不由本测试代签。</p>
 */
class AuthorizationDecisionTest {

    private static AuthorizationDecision permit(Set<String> actions, Obligation... obligations) {
        return new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "digest-1",
                actions, List.of(obligations), null);
    }

    private static AuthorizationDecision deny(String reason, Obligation... obligations) {
        return new AuthorizationDecision(DecisionKind.DENY, "pv-1", null,
                Set.of(), List.of(obligations), reason);
    }

    private static AuthorizationDecision indeterminate(Obligation... obligations) {
        return new AuthorizationDecision(DecisionKind.INDETERMINATE, "pv-1", null,
                Set.of(), List.of(obligations), "pdp 不可用");
    }

    @Test
    void onlyPermitIsPermissive() {
        assertTrue(permit(Set.of("ar.dunning.send")).isPermitted());
        assertFalse(deny("any").isPermitted());
        assertFalse(indeterminate().isPermitted());
    }

    @Test
    void denyOverridesUpstreamPermit() {
        // AAC-01 语义级：上游 FastAgent PERMIT 不能压过本端 DENY
        AuthorizationDecision combined = AuthorizationDecision.combine(
                permit(Set.of("ar.dunning.send")), deny("local policy denies"));
        assertEquals(DecisionKind.DENY, combined.kind());
        assertFalse(combined.isPermitted());
    }

    @Test
    void indeterminateTreatedAsDenial() {
        AuthorizationDecision combined = AuthorizationDecision.combine(
                permit(Set.of("ar.dunning.send")), indeterminate());
        assertEquals(DecisionKind.INDETERMINATE, combined.kind());
        assertFalse(combined.isPermitted());
    }

    @Test
    void nullSideIsRejectedNotDefaultedToPermit() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationDecision.combine(null, permit(Set.of("a"))));
        assertThrows(IllegalArgumentException.class,
                () -> AuthorizationDecision.combine(permit(Set.of("a")), null));
    }

    @Test
    void permittedActionsIntersect() {
        AuthorizationDecision combined = AuthorizationDecision.combine(
                permit(Set.of("ar.dunning.send", "ar.invoice.read")),
                permit(Set.of("ar.dunning.send", "ar.invoice.write")));
        assertEquals(DecisionKind.PERMIT, combined.kind());
        assertEquals(Set.of("ar.dunning.send"), combined.permittedActions());
    }

    @Test
    void emptyActionIntersectionDenies() {
        AuthorizationDecision combined = AuthorizationDecision.combine(
                permit(Set.of("ar.invoice.read")), permit(Set.of("ar.invoice.write")));
        assertEquals(DecisionKind.DENY, combined.kind());
    }

    @Test
    void dataScopeIntersectsAndEmptyScopeDenies() {
        Obligation upstreamScope = new DataScopeObligation(Set.of("tenant-owned", "region-cn"));
        Obligation localScope = new DataScopeObligation(Set.of("tenant-owned", "region-eu"));
        AuthorizationDecision intersected = AuthorizationDecision.combine(
                permit(Set.of("ar.invoice.read"), upstreamScope),
                permit(Set.of("ar.invoice.read"), localScope));
        assertEquals(DecisionKind.PERMIT, intersected.kind());
        DataScopeObligation merged = intersected.obligations().stream()
                .filter(o -> o instanceof DataScopeObligation)
                .map(o -> (DataScopeObligation) o)
                .findFirst().orElseThrow();
        assertEquals(Set.of("tenant-owned"), merged.allowedDataScopes());

        Obligation disjoint = new DataScopeObligation(Set.of("region-us"));
        AuthorizationDecision empty = AuthorizationDecision.combine(
                permit(Set.of("ar.invoice.read"), upstreamScope),
                permit(Set.of("ar.invoice.read"), disjoint));
        assertEquals(DecisionKind.DENY, empty.kind());
    }

    @Test
    void numericCapTakesStricterMinimum() {
        Obligation upstreamCap = new NumericCapObligation("maxRows", 100);
        Obligation localCap = new NumericCapObligation("maxRows", 50);
        AuthorizationDecision combined = AuthorizationDecision.combine(
                permit(Set.of("ar.invoice.read"), upstreamCap),
                permit(Set.of("ar.invoice.read"), localCap));
        assertEquals(DecisionKind.PERMIT, combined.kind());
        NumericCapObligation merged = combined.obligations().stream()
                .filter(o -> o instanceof NumericCapObligation)
                .map(o -> (NumericCapObligation) o)
                .findFirst().orElseThrow();
        assertEquals(50, merged.maxValue());
    }

    @Test
    void auditObligationsUnionOnBothPermitAndDenyPaths() {
        Obligation upstreamAudit = new RequiredAuditObligation("fastagent.action.audit");
        Obligation localAudit = new RequiredAuditObligation("fastddd.egress.audit");

        AuthorizationDecision bothPermit = AuthorizationDecision.combine(
                permit(Set.of("a"), upstreamAudit), permit(Set.of("a"), localAudit));
        Set<String> topics = bothPermit.obligations().stream()
                .filter(o -> o instanceof RequiredAuditObligation)
                .map(o -> ((RequiredAuditObligation) o).auditTopic())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("fastagent.action.audit", "fastddd.egress.audit"), topics);

        // 拒绝路径仍保留两侧必做审计并集（拒绝不豁免审计义务）
        AuthorizationDecision denied = AuthorizationDecision.combine(
                permit(Set.of("a"), upstreamAudit), deny("denied", localAudit));
        assertEquals(DecisionKind.DENY, denied.kind());
        assertEquals(2, denied.obligations().size());
    }

    @Test
    void obligationHierarchyIsSealedSoUnknownTypesCannotExist() {
        // fail-closed 的结构化表达：Obligation 是 sealed，permits 恰好三种已知类型；
        // 新类型必须扩层级并补合成规则，无法以「未知类型」进入合成
        assertTrue(Obligation.class.isSealed());
        Set<String> permittedSubtypes = java.util.Arrays.stream(Obligation.class.getPermittedSubclasses())
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                RequiredAuditObligation.class.getName(),
                DataScopeObligation.class.getName(),
                NumericCapObligation.class.getName()), permittedSubtypes);
    }

    @Test
    void compactConstructorNormalizesNullCollections() {
        AuthorizationDecision bare = new AuthorizationDecision(DecisionKind.PERMIT, null, null, null, null, null);
        assertEquals(Set.of(), bare.permittedActions());
        assertEquals(List.of(), bare.obligations());
        assertNull(bare.reason());
        assertAll("kind 必选 + 空 kind 拒绝",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AuthorizationDecision(null, "pv", null, null, null, null)));
    }
}
