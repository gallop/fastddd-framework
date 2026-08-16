package com.gallop.fastddd.agentaccess.kit.fault;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.IdempotencyMode;
import com.gallop.fastddd.agentaccess.contract.PreconditionMode;
import com.gallop.fastddd.agentaccess.contract.ReconciliationMode;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.gateway.DefaultAgentAccessGateway;
import com.gallop.fastddd.agentaccess.permit.DefaultExecutionPermitVerifier;
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

/**
 * AST-01（AA-03 §3，宿主视角消费 kit）：准入依赖不可用时全形态 fail-closed——
 * 注入「PDP 超时抛错 / INDETERMINATE / 运行中断连（先正常后故障）」三种形态，
 * 编排一律收敛 {@code TARGET_AUTHORIZATION_DENIED}，禁止任何降级放行。
 * 故障形态经 {@link FaultInjection} 注入（宿主 test scope 消费 kit 的同款用法）。
 *
 * <p><b>AST-01 其余子场景归属登记（不冒充）</b>：Verifier Bean 缺失 →
 * kit {@code AgentAccessAssemblyValidator} 启动 fail-fast（已有测试）；
 * DB/Outbox 断开的真表回滚 → kit conformance（Testcontainers，本机无 Docker
 * 时 skipped 如实记录）。</p>
 */
class Ast01AdmissionFailClosedTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";

    private static CallerContext caller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice", SubjectType.HUMAN,
                "agent-ar-collector", ActorType.AGENT,
                "workload-egress-1", "acme", "dev", null,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                null);
    }

    /** reference Descriptor 样板（主制品 test fixture 不跨模块传递，宿主视角自备同形态） */
    private static BusinessActionDescriptor referenceWriteAction() {
        return new BusinessActionDescriptor(
                "ar.reference.write-sample", "1.0", Effect.WRITE,
                Set.of("ReferenceThing"),
                "schema://reference/write-input@1", "schema://reference/write-output@1",
                Set.of("reference.testing"), "INTERNAL",
                IdempotencyMode.REQUIRED, PreconditionMode.REQUIRED,
                ReconciliationMode.QUERYABLE, "SHORT", true);
    }

    private static ExecutionPermit permit() {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-001", TRUSTED_ISSUER, AUDIENCE, caller(),
                referenceWriteAction().actionId(),
                referenceWriteAction().actionVersion(),
                Effect.WRITE, new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                "idem-1", null, Map.of("ReferenceThing/REF-001", "42"),
                null, null, null, null);
    }

    private static AgentActionRequest request() {
        ExecutionPermit p = permit();
        return new AgentActionRequest(p, new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest()));
    }

    private static DefaultAgentAccessGateway gateway(TargetAdmissionAuthorizationPort port) {
        return new DefaultAgentAccessGateway(
                new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                        Duration.ofMinutes(1), false)),
                StaticDescriptorRegistry.builder()
                        .register(referenceWriteAction()).build(),
                port);
    }

    @Test
    void allAdmissionFailureModesConvergeToAuditedDenial() {
        // 形态 1：PDP 超时（调用即抛，超时异常形态）
        TargetAdmissionAuthorizationPort pdpTimeout = query -> FaultInjection
                .<AuthorizationDecision>alwaysFails(new IllegalStateException("simulated pdp timeout")).get();
        // 形态 2：PDP 无法判定（INDETERMINATE，非抛错形态）
        TargetAdmissionAuthorizationPort indeterminate = query -> new AuthorizationDecision(
                DecisionKind.INDETERMINATE, "pv-1", null, Set.of(), List.of(), "无法判定");
        // 形态 3：运行中断连（先正常服务一次，随后依赖消失——注入器状态必须在 Port lambda
        // 外固定，lambda 体内重建会让计数每次归零、故障永不发生）
        java.util.function.Supplier<AuthorizationDecision> onceHealthyThenDead = FaultInjection
                .failsAfter(1,
                        () -> new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                                Set.of("ar.reference.write-sample"), List.of(), null),
                        new IllegalStateException("simulated db disconnect"));
        TargetAdmissionAuthorizationPort midRunOutage = query -> onceHealthyThenDead.get();

        // 断连前正常放行一次（证明注入器工作且正常路径未被误伤）
        assertInstanceOf(GatewayOutcome.Admitted.class, gateway(midRunOutage).admitAgentAction(request()));

        for (TargetAdmissionAuthorizationPort faulty : List.of(pdpTimeout, indeterminate, midRunOutage)) {
            GatewayOutcome outcome = gateway(faulty).admitAgentAction(request());
            GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class, outcome,
                    "依赖不可用的每一形态都必须拒绝（禁止降级放行）");
            assertAll("fail-closed 收敛 + 可审计",
                    () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, rejected.outcomeCode()),
                    () -> assertEquals(ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED,
                            rejected.audit().outcomeCode()));
        }
    }
}
