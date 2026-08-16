package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.spi.AgentActionRequest;
import com.gallop.fastddd.agentaccess.spi.AuthorizationDecision;
import com.gallop.fastddd.agentaccess.spi.DecisionKind;
import com.gallop.fastddd.agentaccess.spi.GatewayOutcome;
import com.gallop.fastddd.agentaccess.spi.PermitBindingView;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.StaticDescriptorRegistry;
import com.gallop.fastddd.agentaccess.spi.TargetAdmissionAuthorizationPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AAC-01/02/18 负向矩阵：双端 PEP 四象限——仅双 PERMIT 进入 dispatch 接缝，
 * 其余象限零业务效果且无未审计出口。
 *
 * <p><b>骨架期象限承载如实标注</b>：上游（FastAgent）ALLOW/DENY 象限在本仓
 * 以「Permit 有效/无效」承载（V04-04 真实 Permit carrier 交付前的近似——
 * 过期/伪造 Permit 即上游拒绝语义）；下游（FastDDD 目标侧）象限以准入预检
 * PERMIT/DENY 真实承载。零业务效果断言在本层 = 无效象限<b>不读取准入 Port</b>
 * （验证先于业务读取）且全部拒绝携带审计投影（不存在无审计出口）；
 * 业务表级 0 变更断言归宿主 conformance（kit 真表 fixture）。</p>
 */
class AacQuadrantNegativeMatrixTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";

    /** 计数 Port：断言「验证先于业务读取」——无效许可象限不得触碰准入预检 */
    private static final class CountingAdmissionPort implements TargetAdmissionAuthorizationPort {
        int calls;

        @Override
        public AuthorizationDecision authorizeAdmission(AdmissionQuery query) {
            calls++;
            return new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                    Set.of(query.actionId()), List.of(), null);
        }
    }

    private static CallerContext caller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice", SubjectType.HUMAN,
                "agent-ar-collector", ActorType.AGENT,
                "workload-egress-1", "acme", "dev", null,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                null);
    }

    private static ExecutionPermit permit(OffsetDateTime notBefore, OffsetDateTime expiresAt) {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-001", TRUSTED_ISSUER, AUDIENCE, caller(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionId(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionVersion(),
                Effect.WRITE, new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                notBefore, expiresAt,
                "idem-1", null, Map.of("ReferenceThing/REF-001", "42"),
                null, null, null, null);
    }

    private static ExecutionPermit validPermit() {
        return permit(OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
    }

    /** 上游拒绝象限的承载形态：过期许可（=FastAgent 未授权该次执行） */
    private static ExecutionPermit upstreamDeniedPermit() {
        return permit(OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(30)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC));
    }

    private static PermitBindingView matchingBinding(ExecutionPermit p) {
        return new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
    }

    private static TargetAdmissionAuthorizationPort denyingPort() {
        return query -> new AuthorizationDecision(DecisionKind.DENY, "pv-1", null,
                Set.of(), List.of(), "目标侧拒绝");
    }

    private static DefaultAgentAccessGateway gateway(TargetAdmissionAuthorizationPort port) {
        return new DefaultAgentAccessGateway(
                new com.gallop.fastddd.agentaccess.permit.DefaultExecutionPermitVerifier(
                        new PermitVerifierConfig(Clock.fixed(NOW, ZoneOffset.UTC),
                                Set.of(TRUSTED_ISSUER), AUDIENCE,
                                Duration.ofMinutes(1), false)),
                StaticDescriptorRegistry.builder()
                        .register(ReferenceDescriptorFixtures.referenceWriteAction()).build(),
                port);
    }

    @Test
    void quadrantBothAllowIsTheOnlyPathToDispatchSeam() {
        // Q1：双 ALLOW —— 唯一进入 dispatch/Handler 接缝的象限（骨架期以 Admitted+descriptor 承载）
        CountingAdmissionPort port = new CountingAdmissionPort();
        GatewayOutcome admitted = gateway(port).admitAgentAction(
                new AgentActionRequest(validPermit(), matchingBinding(validPermit())));
        GatewayOutcome.Admitted q1 = assertInstanceOf(GatewayOutcome.Admitted.class, admitted);
        assertEquals(ReferenceDescriptorFixtures.referenceWriteAction(), q1.descriptor());
        assertEquals(1, port.calls, "有效许可必须经过准入预检（不跳过下游 PEP）");
        assertEquals(null, q1.audit().outcomeCode(), "准入通过不是执行终态");
    }

    @Test
    void quadrantUpstreamDeniedNeverReachesTargetSideOrLeavesUnaudited() {
        // Q2/Q4：上游拒绝（过期许可承载）——无论下游象限如何，均拒绝且不读准入 Port
        for (TargetAdmissionAuthorizationPort downstream : List.of(new CountingAdmissionPort(), denyingPort())) {
            CountingAdmissionPort counting = downstream instanceof CountingAdmissionPort p ? p : null;
            GatewayOutcome outcome = gateway(downstream).admitAgentAction(
                    new AgentActionRequest(upstreamDeniedPermit(), matchingBinding(upstreamDeniedPermit())));
            GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class, outcome);
            assertAll("上游拒绝象限（AAC-01 上游半边 + AAC-02 无有效 Permit 不放行）",
                    () -> assertEquals(ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, rejected.outcomeCode()),
                    () -> assertEquals(ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, rejected.audit().outcomeCode()),
                    () -> assertNotNull(rejected.audit().businessOperationId(), "拒绝行携带治理标识（可审计）"));
            if (counting != null) {
                assertEquals(0, counting.calls, "无效许可不得触碰准入预检（验证先于业务读取）");
            }
        }
    }

    @Test
    void quadrantDownstreamDeniedIsAuditedTargetRejection() {
        // Q3：上游有效 + 下游 DENY —— TARGET_AUTHORIZATION_DENIED（AAC-01 本仓半边）
        CountingAdmissionPortSpy spy = new CountingAdmissionPortSpy(denyingPort());
        GatewayOutcome outcome = gateway(spy).admitAgentAction(
                new AgentActionRequest(validPermit(), matchingBinding(validPermit())));
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class, outcome);
        assertAll("FastAgent ALLOW + FastDDD DENY → 零业务效果（骨架层=Admitted 不出现 + 拒绝可审计）",
                () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.outcomeCode()),
                () -> assertEquals("admission.denied", rejected.internalReason().code()),
                () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.audit().outcomeCode()),
                () -> assertEquals("acme", rejected.audit().tenantId()));
        assertEquals(1, spy.calls);
    }

    /** 包装任意 Port 计调用（Q3 用 denyingPort 也需要计数） */
    private static final class CountingAdmissionPortSpy implements TargetAdmissionAuthorizationPort {
        final TargetAdmissionAuthorizationPort delegate;
        int calls;

        CountingAdmissionPortSpy(TargetAdmissionAuthorizationPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public AuthorizationDecision authorizeAdmission(AdmissionQuery query) {
            calls++;
            return delegate.authorizeAdmission(query);
        }
    }
}
