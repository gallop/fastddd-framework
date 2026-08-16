package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.InternalReason;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 骨架协议 record 紧致校验测试：AgentActionRequest / AuditProjection /
 * GatewayOutcome（Admitted/Rejected 两态的一致性约束）。
 */
class GatewayProtocolRecordsTest {

    private static AuditProjection referenceAudit(ActionOutcomeCode outcomeCode) {
        return new AuditProjection(
                "acme", "bop-2026-0001",
                "ar.reference.write-sample", "1.0",
                "subject-alice", "agent-ar-collector", "workload-egress-1",
                "permit-001", null, outcomeCode);
    }

    @Test
    void agentActionRequestRequiresPermitAndBinding() {
        ExecutionPermit permit = referencePermit();
        PermitBindingView binding = referenceBinding(permit);
        assertAll(
                () -> assertDoesNotThrow(() -> new AgentActionRequest(permit, binding)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentActionRequest(null, binding)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AgentActionRequest(permit, null)));
    }

    @Test
    void auditProjectionEnforcesWhitelistFieldPresence() {
        assertAll("白名单字段必选非空白；traceId 可选但不得空白；outcomeCode 终态语义可空",
                () -> assertDoesNotThrow(() -> referenceAudit(null)),
                () -> assertDoesNotThrow(() -> new AuditProjection(
                        "acme", "bop-2026-0001", "ar.reference.write-sample", "1.0",
                        "subject-alice", "agent-ar-collector", "workload-egress-1",
                        "permit-001", "trace-1", ActionOutcomeCode.ACTION_NOT_EXPOSED)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AuditProjection(
                        null, "bop-2026-0001", "ar.reference.write-sample", "1.0",
                        "subject-alice", "agent-ar-collector", "workload-egress-1",
                        "permit-001", null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AuditProjection(
                        "acme", " ", "ar.reference.write-sample", "1.0",
                        "subject-alice", "agent-ar-collector", "workload-egress-1",
                        "permit-001", null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AuditProjection(
                        "acme", "bop-2026-0001", "ar.reference.write-sample", "1.0",
                        "subject-alice", "agent-ar-collector", "workload-egress-1",
                        "permit-001", " ", null)));
    }

    @Test
    void admittedRequiresDescriptorAndTerminalFreeAudit() {
        BusinessActionDescriptor descriptor = ReferenceDescriptorFixtures.referenceWriteAction();
        assertAll("准入通过：descriptor 必选；audit 必选且不带终态码",
                () -> assertDoesNotThrow(
                        () -> new GatewayOutcome.Admitted(descriptor, referenceAudit(null))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GatewayOutcome.Admitted(null, referenceAudit(null))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GatewayOutcome.Admitted(descriptor, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new GatewayOutcome.Admitted(descriptor,
                                referenceAudit(ActionOutcomeCode.ALREADY_COMPLETED))));
    }

    @Test
    void rejectedEnforcesOutcomeReasonAndAuditAgreement() {
        InternalReason reason = new InternalReason("descriptor.unregistered",
                ActionOutcomeCode.ACTION_NOT_EXPOSED);
        assertAll("拒绝三一致：细因外码 = 拒绝外码 = 审计外码",
                () -> assertDoesNotThrow(() -> new GatewayOutcome.Rejected(
                        ActionOutcomeCode.ACTION_NOT_EXPOSED, reason,
                        referenceAudit(ActionOutcomeCode.ACTION_NOT_EXPOSED))),
                () -> assertThrows(IllegalArgumentException.class, () -> new GatewayOutcome.Rejected(
                        null, reason, referenceAudit(ActionOutcomeCode.ACTION_NOT_EXPOSED))),
                () -> assertThrows(IllegalArgumentException.class, () -> new GatewayOutcome.Rejected(
                        ActionOutcomeCode.ACTION_NOT_EXPOSED, null,
                        referenceAudit(ActionOutcomeCode.ACTION_NOT_EXPOSED))),
                // 细因外码与拒绝外码不一致
                () -> assertThrows(IllegalArgumentException.class, () -> new GatewayOutcome.Rejected(
                        ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, reason,
                        referenceAudit(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED))),
                // 审计外码与拒绝外码不一致
                () -> assertThrows(IllegalArgumentException.class, () -> new GatewayOutcome.Rejected(
                        ActionOutcomeCode.ACTION_NOT_EXPOSED, reason,
                        referenceAudit(ActionOutcomeCode.UNKNOWN))));
    }

    private static ExecutionPermit referencePermit() {
        return new ExecutionPermit(
                com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-001",
                "https://issuer.fastagent.example",
                "fastddd-ar-service",
                new com.gallop.fastddd.agentaccess.contract.CallerContext(
                        com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                        "subject-alice",
                        com.gallop.fastddd.agentaccess.contract.SubjectType.HUMAN,
                        "agent-ar-collector",
                        com.gallop.fastddd.agentaccess.contract.ActorType.AGENT,
                        "workload-egress-1", "acme", "dev", null,
                        java.time.OffsetDateTime.parse("2026-08-16T03:00:00Z"), null),
                "ar.reference.write-sample", "1.0",
                Effect.WRITE,
                new com.gallop.fastddd.agentaccess.contract.ResourceConstraints(
                        java.util.Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                java.time.OffsetDateTime.parse("2026-08-16T03:04:00Z"),
                java.time.OffsetDateTime.parse("2026-08-16T03:15:00Z"),
                "idem-1", null, java.util.Map.of("ReferenceThing/REF-001", "42"),
                null, null, null, null);
    }

    private static PermitBindingView referenceBinding(ExecutionPermit p) {
        return new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
    }
}
