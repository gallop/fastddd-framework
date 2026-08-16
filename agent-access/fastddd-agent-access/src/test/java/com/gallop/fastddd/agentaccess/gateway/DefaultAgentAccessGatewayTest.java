package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.permit.DefaultExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.AgentActionRequest;
import com.gallop.fastddd.agentaccess.spi.AuditProjection;
import com.gallop.fastddd.agentaccess.spi.AuthorizationDecision;
import com.gallop.fastddd.agentaccess.spi.DecisionKind;
import com.gallop.fastddd.agentaccess.spi.DescriptorRegistry;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultAgentAccessGateway} 编排骨架测试（标准执行顺序步骤 3-6 的
 * 全部出口：通过 + 各拒绝细因 → 结果码映射 + 审计白名单一致性）。
 *
 * <p><b>验收层级如实标注</b>：事务内步骤 7-13（去重/终检/聚合/Outbox/Receipt）
 * 归 FDD-AA-13/14，本测试不代签。</p>
 */
class DefaultAgentAccessGatewayTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";

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

    private static ExecutionPermit permit() {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-001",
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
                "idem-1",
                null,
                Map.of("ReferenceThing/REF-001", "42"),
                null,
                null,
                null,
                null);
    }

    private static PermitBindingView matchingBinding(ExecutionPermit p) {
        return new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
    }

    private static DescriptorRegistry registryWithReferenceAction() {
        return StaticDescriptorRegistry.builder()
                .register(ReferenceDescriptorFixtures.referenceWriteAction())
                .build();
    }

    private static TargetAdmissionAuthorizationPort permittingPort() {
        return query -> new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                Set.of(query.actionId()), List.of(), null);
    }

    private static TargetAdmissionAuthorizationPort portReturning(AuthorizationDecision decision) {
        return query -> decision;
    }

    private static DefaultExecutionPermitVerifier verifier() {
        return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of(TRUSTED_ISSUER),
                AUDIENCE,
                Duration.ofMinutes(1),
                false));
    }

    private static DefaultAgentAccessGateway gateway(DescriptorRegistry registry,
                                                     TargetAdmissionAuthorizationPort admissionPort) {
        return new DefaultAgentAccessGateway(verifier(), registry, admissionPort);
    }

    @Test
    void admittedOnFullyValidRequestCarriesDescriptorAndWhitelistAudit() {
        BusinessActionDescriptor descriptor = ReferenceDescriptorFixtures.referenceWriteAction();
        DescriptorRegistry registry = StaticDescriptorRegistry.builder().register(descriptor).build();
        GatewayOutcome outcome = gateway(registry, permittingPort())
                .admitAgentAction(new AgentActionRequest(permit(), matchingBinding(permit())));

        GatewayOutcome.Admitted admitted = assertInstanceOf(GatewayOutcome.Admitted.class, outcome);
        assertSame(descriptor, admitted.descriptor());
        AuditProjection audit = admitted.audit();
        // §10 白名单字段取自已验证许可（权威来源），零 payload：record 组件即白名单全集
        assertAll("审计投影 = §10 白名单，值来自许可",
                () -> assertEquals("acme", audit.tenantId()),
                () -> assertEquals("bop-2026-0001", audit.businessOperationId()),
                () -> assertEquals(descriptor.actionId(), audit.actionId()),
                () -> assertEquals(descriptor.actionVersion(), audit.actionVersion()),
                () -> assertEquals("subject-alice", audit.subjectId()),
                () -> assertEquals("agent-ar-collector", audit.actorId()),
                () -> assertEquals("workload-egress-1", audit.workloadId()),
                () -> assertEquals("permit-001", audit.permitId()),
                // 准入通过不是执行终态：终态 outcomeCode 缺失（AA-13 闭环后回填）
                () -> assertEquals(null, audit.outcomeCode()));
    }

    @Test
    void expiredPermitIsRejectedAsPermitInvalidOrExpiredWithAuditableReason() {
        ExecutionPermit expired = new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "permit-001",
                TRUSTED_ISSUER, AUDIENCE, caller(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionId(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionVersion(),
                Effect.WRITE, new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(30)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                "idem-1", null, Map.of("ReferenceThing/REF-001", "42"),
                null, null, null, null);

        GatewayOutcome outcome = gateway(registryWithReferenceAction(), permittingPort())
                .admitAgentAction(new AgentActionRequest(expired, matchingBinding(expired)));

        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class, outcome);
        assertAll("过期许可 → PERMIT_INVALID_OR_EXPIRED + 细因 + 审计一致",
                () -> assertEquals(ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, rejected.outcomeCode()),
                () -> assertEquals("permit.expired", rejected.internalReason().code()),
                () -> assertEquals(ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, rejected.audit().outcomeCode()),
                // 拒绝同样可审计：白名单字段照常从许可提取
                () -> assertEquals("bop-2026-0001", rejected.audit().businessOperationId()));
    }

    @Test
    void bindingMismatchIsRejectedAsPermitBindingMismatch() {
        ExecutionPermit p = permit();
        PermitBindingView tamperedTenant = new PermitBindingView(
                "tenant-victim", p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());

        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), permittingPort())
                        .admitAgentAction(new AgentActionRequest(p, tamperedTenant)));
        assertAll("绑定投影篡改 → PERMIT_BINDING_MISMATCH",
                () -> assertEquals(ActionOutcomeCode.PERMIT_BINDING_MISMATCH, rejected.outcomeCode()),
                () -> assertEquals("permit.binding.field.mismatch", rejected.internalReason().code()),
                () -> assertEquals("acme", rejected.audit().tenantId()));
    }

    @Test
    void unregisteredActionIsRejectedAsActionNotExposed() {
        ExecutionPermit p = permit();
        // 未注册 actionId：与 disabled/版本漂移统一外码，存在性差异只在内部细因
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), permittingPort())
                        .admitAgentAction(new AgentActionRequest(unregisteredActionPermit(),
                                matchingBinding(unregisteredActionPermit()))));
        assertAll("未注册 → ACTION_NOT_EXPOSED（AST-05 统一外码）",
                () -> assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, rejected.outcomeCode()),
                () -> assertEquals("descriptor.unregistered", rejected.internalReason().code()),
                () -> assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, rejected.audit().outcomeCode()));
    }

    @Test
    void admissionDenyIsRejected() {
        AuthorizationDecision deny =
                new AuthorizationDecision(DecisionKind.DENY, "pv-1", null, Set.of(), List.of(), "拒绝");
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), portReturning(deny))
                        .admitAgentAction(new AgentActionRequest(permit(), matchingBinding(permit()))));
        assertAll("准入 DENY → TARGET_AUTHORIZATION_DENIED",
                () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.outcomeCode()),
                () -> assertEquals("admission.denied", rejected.internalReason().code()));
    }

    @Test
    void admissionIndeterminateIsRejected() {
        AuthorizationDecision indeterminate = new AuthorizationDecision(DecisionKind.INDETERMINATE,
                "pv-1", null, Set.of(), List.of(), "无法判定");
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), portReturning(indeterminate))
                        .admitAgentAction(new AgentActionRequest(permit(), matchingBinding(permit()))));
        assertAll("INDETERMINATE 按拒绝处理（deny-overrides 之外的 fail-closed）",
                () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.outcomeCode()),
                () -> assertEquals("admission.indeterminate", rejected.internalReason().code()));
    }

    @Test
    void admissionPortReturningNullDecisionIsRejectedAsIndeterminate() {
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), portReturning(null))
                        .admitAgentAction(new AgentActionRequest(permit(), matchingBinding(permit()))));
        assertEquals("admission.indeterminate", rejected.internalReason().code());
    }

    @Test
    void admissionPortThrowingIsFailClosedNotLeaked() {
        TargetAdmissionAuthorizationPort throwingPort = query -> {
            throw new IllegalStateException("PDP 故障（违约路径：契约要求返回 INDETERMINATE）");
        };
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway(registryWithReferenceAction(), throwingPort)
                        .admitAgentAction(new AgentActionRequest(permit(), matchingBinding(permit()))));
        assertAll("Port 违约抛错 → 收敛为 fail-closed 拒绝，不吞不放行",
                () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.outcomeCode()),
                () -> assertEquals("admission.port.error", rejected.internalReason().code()));
    }

    @Test
    void nullRequestIsProgrammingError() {
        assertThrows(IllegalArgumentException.class,
                () -> gateway(registryWithReferenceAction(), permittingPort()).admitAgentAction(null));
    }

    private static ExecutionPermit unregisteredActionPermit() {
        BusinessActionDescriptor registered = ReferenceDescriptorFixtures.referenceWriteAction();
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "permit-001",
                TRUSTED_ISSUER, AUDIENCE, caller(),
                "ar.reference.never-registered", registered.actionVersion(),
                Effect.WRITE, new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                "idem-1", null, Map.of("ReferenceThing/REF-001", "42"),
                null, null, null, null);
    }
}
