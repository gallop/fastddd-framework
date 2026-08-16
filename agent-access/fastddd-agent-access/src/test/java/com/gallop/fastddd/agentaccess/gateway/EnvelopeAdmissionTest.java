package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.BusinessActionEnvelope;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.permit.DefaultExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.AuthorizationDecision;
import com.gallop.fastddd.agentaccess.spi.DecisionKind;
import com.gallop.fastddd.agentaccess.spi.DescriptorRegistry;
import com.gallop.fastddd.agentaccess.spi.ExecutionPermitVerifier;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Envelope 入口编排测试（§7 步骤 1-6；FDD-AA-13 编排充实）：
 * 许可验证先于任何业务读取（顺序断言）、信封↔许可一致比对拒绝、permitRef
 * 骨架期显式失败、AAC-07 语义级重试结构稳定（可变调度信息不进投影/审计）。
 *
 * <p><b>验收层级如实标注</b>：AAC-06 完整语义（资源版本变化 →
 * PRECONDITION_CHANGED/RESOURCE_VERSION_CONFLICT）与持久去重归 FDD-AA-14
 * 事务内终检；本测试只覆盖入口级绑定比对拒绝。</p>
 */
class EnvelopeAdmissionTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";

    /** Envelope 路径用许可：不带 expectedResourceVersions 的形态（信封直投 null=缺省，提供状态一致即通过）。 */
    private static ExecutionPermit envelopePermit() {
        return envelopePermit(null);
    }

    private static ExecutionPermit envelopePermit(Map<String, String> expectedResourceVersions) {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-env-001",
                TRUSTED_ISSUER,
                AUDIENCE,
                caller(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionId(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionVersion(),
                Effect.WRITE,
                new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing",
                "bop-2026-0001",
                "sha256:auth",
                "evidence://fastagent/grant-1",
                "sha256:effect",
                "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                null,
                null,
                expectedResourceVersions,
                null,
                null,
                null,
                null);
    }

    private static CallerContext caller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice",
                SubjectType.HUMAN,
                "agent-ar-collector",
                ActorType.AGENT,
                "workload-egress-1",
                "acme",
                "dev",
                null,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                null);
    }

    /**
     * 与许可治理字段逐项一致的匹配信封（requestId/attemptId 可变——不进投影）。
     * 修订 2026-08-16：{@code preconditionsHash}/{@code expectedResourceVersions}
     * 直携自许可（对齐形态——真实 V04-04 载体与许可构造期交叉校验后直携同等值）。
     */
    private static BusinessActionEnvelope envelope(ExecutionPermit permit, String requestId, String attemptId) {
        return new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                requestId,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                permit.caller().tenantId(),
                permit.caller().environment(),
                permit.actionId(),
                permit.actionVersion(),
                permit.effect(),
                permit,
                null,
                permit.authorizationDigest(),
                null,
                permit.businessOperationId(),
                attemptId,
                null,
                null,
                "ReferenceThing/REF-001",
                null,
                permit.preconditionsHash(),
                permit.expectedResourceVersions(),
                permit.purpose(),
                "{\"amount\":100}",
                "1",
                permit.effectDigest(),
                permit.authorizationBindingDigest(),
                null,
                null,
                null,
                null);
    }

    /** 不直携前置条件组件的信封变体（Permit 单方携带时用于 presence-mismatch 负向）。 */
    private static BusinessActionEnvelope envelopeWithoutPreconditionsCarriage(
            ExecutionPermit permit, String requestId, String attemptId) {
        return new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                requestId,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                permit.caller().tenantId(),
                permit.caller().environment(),
                permit.actionId(),
                permit.actionVersion(),
                permit.effect(),
                permit,
                null,
                permit.authorizationDigest(),
                null,
                permit.businessOperationId(),
                attemptId,
                null,
                null,
                "ReferenceThing/REF-001",
                null,
                null,
                null,
                permit.purpose(),
                "{\"amount\":100}",
                "1",
                permit.effectDigest(),
                permit.authorizationBindingDigest(),
                null,
                null,
                null,
                null);
    }

    private static DefaultAgentAccessGateway gateway() {
        return new DefaultAgentAccessGateway(
                new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                        Duration.ofMinutes(1), false)),
                StaticDescriptorRegistry.builder()
                        .register(ReferenceDescriptorFixtures.referenceWriteAction()).build(),
                query -> new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                        Set.of(query.actionId()), List.of(), null));
    }

    @Test
    void carrierPathAdmitsMatchingEnvelope() {
        ExecutionPermit permit = envelopePermit();
        GatewayOutcome outcome = gateway().admitAgentAction(envelope(permit, "req-1", "attempt-1"));
        GatewayOutcome.Admitted admitted = assertInstanceOf(GatewayOutcome.Admitted.class, outcome);
        assertEquals(ReferenceDescriptorFixtures.referenceWriteAction().actionId(),
                admitted.descriptor().actionId());
        assertEquals("bop-2026-0001", admitted.audit().businessOperationId());
    }

    @Test
    void permitRefPathFailsClosedAtSkeleton() {
        // permitRef 是合法 wire 形态，但骨架期无解引用通道与审计责任链承载——显式失败优于不可审计拒绝
        BusinessActionEnvelope refOnly = new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "req-1",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), "acme", "dev",
                "ar.reference.write-sample", "1", Effect.WRITE,
                null, "permit-ref-001", "sha256:auth", null, "bop-2026-0001",
                null, null, null, "ReferenceThing/REF-001", null, null, null,
                "reference.testing",
                "{}", "1", "sha256:effect", "sha256:binding",
                null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> gateway().admitAgentAction(refOnly));
        assertThrows(IllegalArgumentException.class, () -> gateway().admitAgentAction((BusinessActionEnvelope) null));
    }

    @Test
    void unknownCarrierFormFailsClosedAtSkeleton() {
        // 载体静态类型是 ExecutionPermitCarrier；骨架期仅接受许可本体（ExecutionPermit）
        // 承载——非本体实现（伪造载体/未来远程 token 载体）显式 IAE。
        // 负向锁定该守卫分支，防止后续重构无声删除
        ExecutionPermitCarrier forged = () -> caller();
        BusinessActionEnvelope withForgedCarrier = new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "req-1",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), "acme", "dev",
                "ar.reference.write-sample", "1", Effect.WRITE,
                forged, null, "sha256:auth", null, "bop-2026-0001",
                null, null, null, "ReferenceThing/REF-001", null, null, null,
                "reference.testing",
                "{}", "1", "sha256:effect", "sha256:binding",
                null, null, null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> gateway().admitAgentAction(withForgedCarrier));
        // 消息断言区分于 permitRef 路径的 IAE（两条守卫各自可辨）
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("载体形态"),
                "未知载体守卫消息应含「载体形态」，实际：" + e.getMessage());
    }

    @Test
    void verificationPrecedesDescriptorResolutionAndAdmission() {
        // §7 步骤 3 → 5 → 6 顺序断言：许可验证先于任何业务读取（resolve/准入）
        List<String> events = new ArrayList<>();
        ExecutionPermit permit = envelopePermit();
        ExecutionPermitVerifier recordingVerifier = (p, b) -> {
            events.add("verify");
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                    Duration.ofMinutes(1), false)).verify(p, b);
        };
        DescriptorRegistry recordingRegistry = new DescriptorRegistry() {
            private final DescriptorRegistry delegate = StaticDescriptorRegistry.builder()
                    .register(ReferenceDescriptorFixtures.referenceWriteAction()).build();

            @Override
            public com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor resolve(
                    String actionId, String actionVersion) {
                events.add("resolve");
                return delegate.resolve(actionId, actionVersion);
            }

            @Override
            public int registeredCount() {
                return delegate.registeredCount();
            }
        };
        TargetAdmissionAuthorizationPort recordingPort = query -> {
            events.add("admission");
            return new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                    Set.of(query.actionId()), List.of(), null);
        };
        new DefaultAgentAccessGateway(recordingVerifier, recordingRegistry, recordingPort)
                .admitAgentAction(envelope(permit, "req-1", "attempt-1"));
        assertEquals(List.of("verify", "resolve", "admission"), events);
    }

    @Test
    void envelopePermitDigestMismatchRejected() {
        // 信封 effectDigest 与许可不一致 → PERMIT_BINDING_MISMATCH（步骤 4 一致比对）
        ExecutionPermit permit = envelopePermit();
        BusinessActionEnvelope tampered = new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "req-1",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), "acme", "dev",
                permit.actionId(), permit.actionVersion(), Effect.WRITE,
                permit, null, permit.authorizationDigest(), null, permit.businessOperationId(),
                null, null, null, "ReferenceThing/REF-001", null, null, null, permit.purpose(),
                "{\"amount\":100}", "1", "sha256:tampered-effect", permit.authorizationBindingDigest(),
                null, null, null, null);
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway().admitAgentAction(tampered));
        assertAll("信封摘要被篡改 → 绑定比对拒绝",
                () -> assertEquals(ActionOutcomeCode.PERMIT_BINDING_MISMATCH, rejected.outcomeCode()),
                () -> assertEquals("permit.binding.field.mismatch", rejected.internalReason().code()));
    }

    @Test
    void permitWithExpectedVersionsFailsClosedWhenEnvelopeDoesNotCarry() {
        // 修订 2026-08-16 后的 fail-closed 语义：Permit 携带 expectedResourceVersions 而
        // Envelope 未直携对等主张（投 null）→ 提供状态不一致即拒绝，不静默放行
        ExecutionPermit permit = envelopePermit(Map.of("ReferenceThing/REF-001", "42"));
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway().admitAgentAction(envelopeWithoutPreconditionsCarriage(permit, "req-1", "attempt-1")));
        assertAll("Permit 单方携带资源版本 → PERMIT_BINDING_MISMATCH",
                () -> assertEquals(ActionOutcomeCode.PERMIT_BINDING_MISMATCH, rejected.outcomeCode()),
                () -> assertEquals("permit.binding.presence.mismatch", rejected.internalReason().code()));
    }

    /** 修订 2026-08-16 新能力：Permit 携带版本重检表且信封直携同等值 → 放行（对齐 V04-04 直携形态）。 */
    @Test
    void permitWithExpectedVersionsAdmitsWhenEnvelopeCarriesThem() {
        ExecutionPermit permit = envelopePermit(Map.of("ReferenceThing/REF-001", "42"));
        GatewayOutcome outcome = gateway().admitAgentAction(envelope(permit, "req-1", "attempt-1"));
        GatewayOutcome.Admitted admitted = assertInstanceOf(GatewayOutcome.Admitted.class, outcome);
        assertEquals("bop-2026-0001", admitted.audit().businessOperationId());
    }

    @Test
    void retryWithSameOperationAndEffectDigestIsStructurallyStable() {
        // AAC-07 语义级：同 businessOperationId + 同 effectDigest 的重试在结构上等价
        // （requestId/attemptId 是可变调度信息，不进绑定投影、不进审计白名单）；
        // 持久去重与稳定 Receipt 归 FDD-AA-14
        ExecutionPermit permit = envelopePermit();
        DefaultAgentAccessGateway gateway = gateway();
        GatewayOutcome first = gateway.admitAgentAction(envelope(permit, "req-1", "attempt-1"));
        GatewayOutcome retry = gateway.admitAgentAction(envelope(permit, "req-2", "attempt-2"));
        GatewayOutcome.Admitted firstAdmitted = assertInstanceOf(GatewayOutcome.Admitted.class, first);
        GatewayOutcome.Admitted retryAdmitted = assertInstanceOf(GatewayOutcome.Admitted.class, retry);
        assertEquals(firstAdmitted.audit(), retryAdmitted.audit());
        assertEquals(firstAdmitted.descriptor(), retryAdmitted.descriptor());
    }
}
