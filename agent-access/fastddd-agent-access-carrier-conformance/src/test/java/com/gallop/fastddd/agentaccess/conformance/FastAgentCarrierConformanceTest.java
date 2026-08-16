package com.gallop.fastddd.agentaccess.conformance;

import com.gallop.fastagent.core.action.ActionDigest;
import com.gallop.fastagent.core.action.ActionProposal;
import com.gallop.fastagent.core.action.RiskLevel;
import com.gallop.fastagent.core.authority.AuthorityMode;
import com.gallop.fastagent.core.evidence.ExecutionAuthorizationDigest;
import com.gallop.fastagent.core.evidence.ExecutionAuthorizationSubject;
import com.gallop.fastagent.core.identity.PrincipalId;
import com.gallop.fastagent.core.port.Decision;
import com.gallop.fastagent.security.approval.ApprovalGrant;
import com.gallop.fastagent.security.approval.ApprovalStatus;
import com.gallop.fastagent.security.gateway.Credentials;
import com.gallop.fastagent.security.gateway.ExecutionBindingSnapshot;
import com.gallop.fastagent.security.gateway.GatewayEgressCommand;
import com.gallop.fastagent.security.gateway.GatewayOperation;
import com.gallop.fastagent.security.gateway.RegistryScope;
import com.gallop.fastagent.security.permit.IssuedExecutionPermit;
import com.gallop.fastagent.security.permit.ObligationView;
import com.gallop.fastagent.security.permit.PermitIssuanceRegistry;
import com.gallop.fastagent.security.permit.PermitIssuanceRequest;
import com.gallop.fastagent.security.permit.PermitIssuanceService;
import com.gallop.fastagent.security.permit.ResourceConstraintsView;
import com.gallop.fastagent.security.policy.EvaluatedPolicyDecision;
import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.BusinessActionEnvelope;
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
import com.gallop.fastddd.agentaccess.spi.ActionDigestCalculator;
import com.gallop.fastddd.agentaccess.spi.AuthorizationDecision;
import com.gallop.fastddd.agentaccess.spi.DecisionKind;
import com.gallop.fastddd.agentaccess.spi.GatewayOutcome;
import com.gallop.fastddd.agentaccess.spi.JsonCanonicalizer;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.StaticDescriptorRegistry;
import com.gallop.fastddd.agentaccess.spi.TargetAdmissionAuthorizationPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FDD-AA-13 收口判定（Phase baseline §7 task 10）：消费 V04-04 真实 dispatch
 * contract artifact——从 Maven 坐标（本地仓）加载
 * {@code com.gallop.fastagent:fastagent-fastddd-command-adapter:0.4.0-alpha.1}
 * 的 {@link GatewayEgressCommand}（34 组件真实载体）与
 * {@link PermitIssuanceService} 真实签发的 {@link IssuedExecutionPermit}，
 * 经测试域映射为 {@link BusinessActionEnvelope} + {@link ExecutionPermit}
 * 后通过 {@link DefaultAgentAccessGateway#admitAgentAction} 全链编排。
 *
 * <p>与 FastAgent 侧 T34 conformance（{@code FastDDDAgentAccessConformanceTest}，
 * 16 项）互为镜像：对侧证明「本方签发 → FastDDD verifier 收敛」，本侧证明
 * 「FastDDD 网关从真实载体收到的一切治理事实可验证、可追踪、fail-closed」。
 * 两侧夹具（EAD/grant/snapshot/签发请求）逐字同源，漂移即测试红。</p>
 *
 * <p><b>映射决策登记（conformance 测试域；Envelope 网关编排在 FastDDD 侧
 * FDD-AA-12 落地，本测试的映射器模拟宿主侧投影的诚实形态）</b>：</p>
 * <ul>
 *   <li><b>Permit 侧</b>：与 FastAgent T34 conformance 的映射逐字一致
 *       （{@code Instant → OffsetDateTime} 一律 UTC；{@code authenticationTime}
 *       取 {@code notBefore} 诚实近似；{@code resourceIds → resourceIdConstraints}
 *       排序逗号连接，空集 → null；空串/空 Map → null 提供状态归一）；
 *       {@code delegatedBy/identityEvidenceRef/usageLimit/dataBudget/confirmation/
 *       policyDecisionRef} 缺省（M1 无来源，发明即伪造）。</li>
 *   <li><b>Envelope 侧（AA02 §2 映射 + 本测试登记的开放项）</b>：
 *       {@code requestId := carrier.dispatchId()}（单次派发 = 单次请求，M1 无独立
 *       请求标识字段）；{@code sentAt := 宿主入口时刻}（真实形态由宿主提供，
 *       测试域固定 NOW）；{@code authorizationEvidenceRef ← actionOperationId}
 *       （AA02 §2 行 10：调查组定位到授权操作记录）；{@code originalBindingRef :=
 *       bindingVersion + "#" + generation}（调查组原 binding 引用复合形态）；
 *       {@code resourceRef := resourceType + "/" + resourceId}；
 *       {@code payload := JsonCanonicalizer.canonicalize(projectedParameters)}
 *       （与 digest 基础设施同一 canonical 化器，空 Map → {@code "{}"}）；
 *       {@code payloadSchemaVersion := "1"}（开放映射项：M1 载体无参数 schema
 *       版本来源，取骨架期常量，schema 冻结归后续任务）；{@code preconditions :=
 *       null}（M1 载体只携摘要不携结构化列表，AAC-06 事务内权威重读语义不受影响）；
 *       可选文本/Map 同 Permit 侧归一。</li>
 * </ul>
 *
 * <p><b>诚实边界</b>：L1 级 conformance（本地仓制品消费 + 同 JVM 测试域），
 * 不证明跨进程/跨网络传输；LOCAL_OBJECT TCB 只防误接线，不抵御同 JVM 任意
 * 代码执行（ADR-006 决策 #11）。宿主真实装配（Spring/事务内终检 AAC-06）归
 * FDD-AA-14/16。</p>
 */
class FastAgentCarrierConformanceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
    private static final String OP_ID = "0199d0a0-0000-7000-8000-000000000041";
    private static final String SPIFFE = "spiffe://fastagent/gateway/ar";
    private static final String ISSUER = "fastagent-kernel-ar";
    private static final String AUDIENCE = "fastddd-ar-gateway";
    private static final String ACTION_ID = "ar.sendCollectionEmail";

    // ------------------------------------------------------------------ FastAgent 侧签发夹具（与 T34 conformance 同源）

    private static ExecutionAuthorizationDigest ead() {
        return new ExecutionAuthorizationSubject(OP_ID, "acme", "prod", "a1".repeat(32), "3",
                "b1".repeat(32), "c1".repeat(32), "d1".repeat(32), "dunning.firstNotice",
                "HUMAN_DELEGATED", 2L, Map.of("dg-ar-001", 4L, "dg-ar-002", 0L),
                Map.of("ag-email", 7L), 1_790_000_000L).digest();
    }

    private static ApprovalGrant boundGrant() {
        ActionProposal p = new ActionProposal("prop-1", "ar.dunning", ACTION_ID,
                "1.0.0", Map.of("invoiceId", "INV-1"), NOW, "acme");
        EvaluatedPolicyDecision evidence = new EvaluatedPolicyDecision(Decision.ALLOW, "ok",
                List.of(), "v0.1.0", "f", Duration.ofSeconds(3600), NOW, List.of(), RiskLevel.R3);
        ApprovalGrant grant = new ApprovalGrant("appr-1", OP_ID, ActionDigest.from(p), "1.0.0",
                "precond-hash", evidence, "dunning.firstNotice",
                List.of(new PrincipalId("approver-1")), NOW,
                NOW.plusSeconds(86400), ApprovalStatus.APPROVED, 1, AuthorityMode.HUMAN_DELEGATED,
                Map.of("dg-1", 0L), Map.of("ag-1", 3L), null);
        return grant.withExecutionAuthorizationDigest(ead());
    }

    private static ExecutionBindingSnapshot snapshot() {
        return new ExecutionBindingSnapshot("exec-ar-email", new RegistryScope("acme", "dev"),
                "MockEmailAdapter", "unversioned", "test", 7L, 3L, SPIFFE, "dhp-ar-1",
                "QUERY_BEFORE_RETRY", ACTION_ID, "1.0.0", NOW);
    }

    /** 可变签发夹具：默认值全合法（可选域齐全形态），负向用例逐字段覆写后 issue()。 */
    private static class Req {
        String tenantId = "acme";
        String environment = "dev";
        ApprovalGrant grant = boundGrant();
        ExecutionAuthorizationDigest ead = ead();
        ExecutionBindingSnapshot bindingSnapshot = snapshot();
        String subjectId = "principal-1";
        String subjectType = "HUMAN";
        String actorId = "agent-ar-1";
        String actorType = "AGENT";
        String workloadIdentityRef = SPIFFE;
        String actionId = ACTION_ID;
        String actionVersion = "1.0.0";
        com.gallop.fastagent.security.permit.Effect effect =
                com.gallop.fastagent.security.permit.Effect.WRITE;
        String dataClassification = "CUSTOMER_FINANCIAL";
        String resourceType = "ArInvoice";
        String resourceId = "INV-1";
        ResourceConstraintsView resourceConstraints =
                new ResourceConstraintsView(Set.of("ArInvoice"), Set.of(), java.util.Optional.empty());
        String purpose = "dunning.firstNotice";
        List<ObligationView> obligations = List.of();
        String businessOperationId = OP_ID;
        String externalIdempotencyKey = "idem-1";
        Map<String, String> expectedResourceVersions = Map.of();
        String authorityEvidenceRef = "evidence/ar/1";
        String issuer = ISSUER;
        String audience = AUDIENCE;
        Instant notBefore = NOW.minusSeconds(60);
        Instant expiresAt = NOW.plusSeconds(600);
        Map<String, Object> projectedEffectParameters = Map.of("invoiceId", "INV-1");

        // 载体专属字段（签发请求之外、出口派发时确定的调度/绑定事实）
        String actionOperationId = "action-op-ar-0001";
        String attemptId = "attempt-ar-0001";
        String dispatchId = "dispatch-ar-0001";
        String traceId = "trace-ar-0001";
        String bindingVersion = "7";
        long generation = 7L;

        PermitIssuanceRequest build() {
            return new PermitIssuanceRequest(tenantId, environment, grant, ead, bindingSnapshot,
                    subjectId, subjectType, actorId, actorType, workloadIdentityRef,
                    actionId, actionVersion, effect, dataClassification,
                    resourceType, resourceId, resourceConstraints, purpose, obligations,
                    businessOperationId, externalIdempotencyKey, expectedResourceVersions,
                    authorityEvidenceRef, issuer, audience, notBefore, expiresAt,
                    projectedEffectParameters);
        }
    }

    private static IssuedExecutionPermit issue(Req req) {
        return new PermitIssuanceService(
                new PermitIssuanceRegistry(Set.of(ISSUER), Set.of(AUDIENCE)),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(1)).issue(req.build());
    }

    /**
     * 真实载体构造（34 组件，构造点逐项交叉校验 permit）：真实链路中由
     * GatewayExecutor 在 8c Permit 签发通过后装配；测试域以同一 Req 保证
     * 载体与 permit 的重叠字段自洽（错配即构造拒绝，见负向用例）。
     */
    private static GatewayEgressCommand carrier(Req req, IssuedExecutionPermit permit) {
        return new GatewayEgressCommand(
                req.resourceType,
                req.resourceId,
                GatewayOperation.WRITE,
                req.effect,
                req.projectedEffectParameters,
                new Credentials("token-ar-1", AUDIENCE, NOW.plusSeconds(300), Set.of("ar:write")),
                req.externalIdempotencyKey,
                req.tenantId,
                req.environment,
                req.businessOperationId,
                req.actionOperationId,
                req.attemptId,
                req.dispatchId,
                req.actionId,
                req.actionVersion,
                req.subjectId,
                req.subjectType,
                req.actorId,
                req.actorType,
                req.workloadIdentityRef,
                req.resourceConstraints,
                req.purpose,
                req.obligations,
                permit.preconditionsHash(),
                permit.expectedResourceVersions(),
                permit.effectDigest(),
                permit.authorizationBindingDigest(),
                permit.authorizationDigest(),
                permit.authorityEvidenceRef(),
                req.bindingVersion,
                req.generation,
                req.traceId,
                permit,
                null); // permitRef：M1 值域 = LOCAL_OBJECT 直携，引用形态构造即拒
    }

    // ------------------------------------------------------------------ 测试域映射（conformance-only）

    /**
     * 真实 Permit → FastDDD ExecutionPermit。映射规则与 FastAgent T34 conformance
     * 的 {@code toFastDddPermit} 逐字一致（漂移即两侧测试同时红）。
     */
    private static ExecutionPermit toFastDddPermit(IssuedExecutionPermit permit) {
        CallerContext caller = new CallerContext(
                permit.schemaVersion(),
                permit.subjectId(),
                SubjectType.valueOf(permit.subjectType()),
                permit.actorId(),
                ActorType.valueOf(permit.actorType()),
                permit.workloadIdentityRef(),
                permit.tenantId(),
                permit.environment(),
                null, // delegatedBy：M1 无委托链字段，缺省（发明委托即伪造）
                permit.notBefore().atOffset(ZoneOffset.UTC), // authenticationTime 近似，见类 javadoc
                null); // identityEvidenceRef：不冒用 authorityEvidenceRef
        ResourceConstraints constraints = new ResourceConstraints(
                permit.resourceConstraints().resourceTypes(),
                permit.resourceConstraints().resourceIds().isEmpty()
                        ? null
                        : String.join(",", new TreeSet<>(permit.resourceConstraints().resourceIds())),
                permit.resourceConstraints().dataScope().orElse(null));
        return new ExecutionPermit(
                permit.schemaVersion(),
                permit.permitId(),
                permit.issuer(),
                permit.audience(),
                caller,
                permit.actionId(),
                permit.actionVersion(),
                Effect.valueOf(permit.effect().name()),
                constraints,
                permit.purpose(),
                permit.businessOperationId(),
                permit.authorizationDigest(),
                permit.authorityEvidenceRef(),
                permit.effectDigest(),
                permit.authorizationBindingDigest(),
                permit.notBefore().atOffset(ZoneOffset.UTC),
                permit.expiresAt().atOffset(ZoneOffset.UTC),
                normalizeOptionalText(permit.externalIdempotencyKey()),
                permit.preconditionsHash(),
                normalizeOptionalMap(permit.expectedResourceVersions()),
                null, null, null, null); // usageLimit/dataBudget/confirmation/policyDecisionRef：M1 未主张
    }

    /**
     * 真实载体 → BusinessActionEnvelope（AA02 §2 映射 + 类 javadoc 登记的开放项）。
     * {@code preconditionsHash/expectedResourceVersions} 直携自载体（2026-08-16
     * Contract §5.5 勘误对齐形态——真实载体与 Permit 构造期交叉校验后直携同等值）。
     */
    private static BusinessActionEnvelope toEnvelope(GatewayEgressCommand carrier,
                                                     ExecutionPermit mappedPermit) {
        return new BusinessActionEnvelope(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                carrier.dispatchId(), // requestId := dispatchId（登记决策，见类 javadoc）
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), // sentAt := 宿主入口时刻（测试域固定）
                carrier.tenantId(),
                carrier.environment(),
                carrier.actionId(),
                carrier.actionVersion(),
                Effect.valueOf(carrier.effect().name()),
                mappedPermit, // LOCAL_OBJECT 直携（ExecutionPermit 实现 ExecutionPermitCarrier）
                null,
                carrier.authorizationDigest(),
                carrier.actionOperationId(), // authorizationEvidenceRef ← actionOperationId（AA02 §2）
                carrier.businessOperationId(),
                carrier.attemptId(),
                carrier.dispatchId(),
                normalizeOptionalText(carrier.externalIdempotencyKey()),
                carrier.resourceType() + "/" + carrier.resourceId(),
                null, // preconditions：M1 载体只携 hash（登记决策，见类 javadoc）
                carrier.preconditionsHash(),
                normalizeOptionalMap(carrier.expectedResourceVersions()),
                carrier.purpose(),
                JsonCanonicalizer.canonicalize(carrier.projectedParameters()),
                "1", // payloadSchemaVersion（开放映射项，见类 javadoc）
                carrier.effectDigest(),
                carrier.authorizationBindingDigest(),
                normalizeOptionalText(carrier.traceId()),
                null,
                null,
                carrier.bindingVersion() + "#" + carrier.generation()); // originalBindingRef 复合形态
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static Map<String, String> normalizeOptionalMap(Map<String, String> value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // ------------------------------------------------------------------ 网关装配（真实编排序：verify → resolve → admission）

    private static BusinessActionDescriptor arDescriptor() {
        return new BusinessActionDescriptor(
                ACTION_ID, "1.0.0", Effect.WRITE, Set.of("ArInvoice"),
                "schema://ar/send-collection-email-input@1",
                "schema://ar/send-collection-email-output@1",
                Set.of("dunning.firstNotice"), "CUSTOMER_FINANCIAL",
                IdempotencyMode.REQUIRED, PreconditionMode.REQUIRED,
                ReconciliationMode.QUERYABLE, "SHORT", true);
    }

    private static DefaultAgentAccessGateway gateway() {
        return new DefaultAgentAccessGateway(
                new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(ISSUER), AUDIENCE,
                        Duration.ofMinutes(1), false)),
                StaticDescriptorRegistry.builder().register(arDescriptor()).build(),
                query -> new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "dd-1",
                        Set.of(query.actionId()), List.of(), null));
    }

    /** 端到端快捷路径：真实签发 → 真实载体 → 映射 → 网关准入。 */
    private static GatewayOutcome admit(Req req) {
        IssuedExecutionPermit permit = issue(req);
        GatewayEgressCommand carrier = carrier(req, permit);
        return gateway().admitAgentAction(toEnvelope(carrier, toFastDddPermit(permit)));
    }

    // ------------------------------------------------------------------ positive

    /** FDD-AA-13 主判定：真实 V04-04 载体（本地仓制品加载）全链通过网关准入。 */
    @Test
    void realCarrierAdmitsThroughGatewayEndToEnd() {
        GatewayOutcome outcome = admit(new Req());
        GatewayOutcome.Admitted admitted = assertInstanceOf(GatewayOutcome.Admitted.class, outcome);
        assertAll("真实载体 → Envelope → 许可验证 → 描述符解析 → 准入全链通过",
                () -> assertEquals(ACTION_ID, admitted.descriptor().actionId()),
                () -> assertEquals("1.0.0", admitted.descriptor().actionVersion()),
                () -> assertEquals(OP_ID, admitted.audit().businessOperationId()));
    }

    /**
     * 治理事实可追踪（FDD-AA-13 验收第一项）：载体 34 组件中进入 Envelope/Permit/
     * 审计投影的每个治理字段，都能从载体组件逐项指认——网关收到的不是不可名状的
     * 通过/失败，而是可对账的治理事实链。
     */
    @Test
    void governedFieldsAreTraceableFromCarrierToEnvelopeAndAudit() {
        Req req = new Req();
        IssuedExecutionPermit ours = issue(req);
        GatewayEgressCommand carrier = carrier(req, ours);
        ExecutionPermit permit = toFastDddPermit(ours);
        BusinessActionEnvelope envelope = toEnvelope(carrier, permit);
        GatewayOutcome.Admitted admitted =
                assertInstanceOf(GatewayOutcome.Admitted.class,
                        gateway().admitAgentAction(envelope));

        assertAll("Envelope 治理字段 = 载体同源值（AA02 §2 逐行）",
                () -> assertEquals(carrier.tenantId(), envelope.tenantId()),
                () -> assertEquals(carrier.environment(), envelope.environment()),
                () -> assertEquals(carrier.actionId(), envelope.actionId()),
                () -> assertEquals(carrier.actionVersion(), envelope.actionVersion()),
                () -> assertEquals(Effect.valueOf(carrier.effect().name()), envelope.effect()),
                () -> assertEquals(carrier.businessOperationId(), envelope.businessOperationId()),
                () -> assertEquals(carrier.attemptId(), envelope.attemptId()),
                () -> assertEquals(carrier.dispatchId(), envelope.dispatchId()),
                () -> assertEquals(carrier.externalIdempotencyKey(), envelope.externalIdempotencyKey()),
                () -> assertEquals(carrier.resourceType() + "/" + carrier.resourceId(),
                        envelope.resourceRef()),
                () -> assertEquals(carrier.preconditionsHash(), envelope.preconditionsHash()),
                () -> assertEquals(carrier.purpose(), envelope.purpose()),
                () -> assertEquals(carrier.effectDigest(), envelope.effectDigest()),
                () -> assertEquals(carrier.authorizationBindingDigest(),
                        envelope.authorizationBindingDigest()),
                () -> assertEquals(carrier.authorizationDigest(), envelope.authorizationDigest()),
                () -> assertEquals(carrier.actionOperationId(), envelope.authorizationEvidenceRef()),
                () -> assertEquals(carrier.bindingVersion() + "#" + carrier.generation(),
                        envelope.originalBindingRef()),
                () -> assertEquals(carrier.traceId(), envelope.traceId()),
                () -> assertEquals(JsonCanonicalizer.canonicalize(carrier.projectedParameters()),
                        envelope.payload()));

        assertAll("Permit 承载 = 载体重叠字段（构造点交叉校验的映射侧镜像）",
                () -> assertEquals(carrier.tenantId(), permit.caller().tenantId()),
                () -> assertEquals(carrier.subjectId(), permit.caller().subjectId()),
                () -> assertEquals(carrier.actorId(), permit.caller().actorId()),
                () -> assertEquals(carrier.workloadIdentityRef(), permit.caller().workloadId()),
                () -> assertEquals(carrier.actionId(), permit.actionId()),
                () -> assertEquals(carrier.purpose(), permit.purpose()),
                () -> assertEquals(carrier.businessOperationId(), permit.businessOperationId()),
                () -> assertEquals(carrier.effectDigest(), permit.effectDigest()),
                () -> assertEquals(carrier.authorizationDigest(), permit.authorizationDigest()),
                () -> assertEquals(carrier.preconditionsHash(), permit.preconditionsHash()));

        assertAll("审计投影 = 载体治理字段白名单投影（AAC-02 审计先于效果）",
                () -> assertEquals(carrier.tenantId(), admitted.audit().tenantId()),
                () -> assertEquals(carrier.businessOperationId(),
                        admitted.audit().businessOperationId()),
                () -> assertEquals(carrier.actionId(), admitted.audit().actionId()),
                () -> assertEquals(carrier.actionVersion(), admitted.audit().actionVersion()),
                () -> assertEquals(carrier.subjectId(), admitted.audit().subjectId()),
                () -> assertEquals(carrier.actorId(), admitted.audit().actorId()),
                () -> assertEquals(carrier.workloadIdentityRef(), admitted.audit().workloadId()),
                () -> assertEquals(ours.permitId(), admitted.audit().permitId()),
                () -> assertNull(admitted.audit().traceId(),
                        "审计投影 traceId 恒缺省（§10 白名单：审计字段取自许可本体，可变调度信息不进白名单）"));
    }

    /** 最小可选形态：空幂等键/空版本重检表（本方「不依赖」语义）归一后仍通过——归一没有把缺省误传成非法值或单方提供。 */
    @Test
    void minimalOptionalCarrierFormStillAdmits() {
        Req minimal = new Req();
        minimal.externalIdempotencyKey = "";
        minimal.expectedResourceVersions = Map.of();
        GatewayOutcome outcome = admit(minimal);
        GatewayOutcome.Admitted admitted = assertInstanceOf(GatewayOutcome.Admitted.class, outcome);
        assertAll("空串/空 Map 归一为缺省后与 permit 侧提供状态一致",
                () -> assertEquals(OP_ID, admitted.audit().businessOperationId()),
                () -> assertNull(toEnvelopeCheck(minimal).externalIdempotencyKey(),
                        "空串幂等键归一为信封缺省（null）"),
                () -> assertNull(toEnvelopeCheck(minimal).expectedResourceVersions(),
                        "空 Map 版本重检表归一为信封缺省（null）"));
    }

    /** 最小形态信封归一检查辅助（独立签发，不与 admit 共享实例）。 */
    private static BusinessActionEnvelope toEnvelopeCheck(Req req) {
        IssuedExecutionPermit ours = issue(req);
        return toEnvelope(carrier(req, ours), toFastDddPermit(ours));
    }

    // ------------------------------------------------------------------ negative（fail-closed）

    /** 信封摘要篡改（步骤 4 一致比对）：载体 effectDigest 与 permit 不一致的信封无法通过绑定比对。 */
    @Test
    void tamperedEnvelopeDigestFailsClosed() {
        Req req = new Req();
        IssuedExecutionPermit ours = issue(req);
        GatewayEgressCommand carrier = carrier(req, ours);
        ExecutionPermit permit = toFastDddPermit(ours);
        BusinessActionEnvelope base = toEnvelope(carrier, permit);
        BusinessActionEnvelope tampered = new BusinessActionEnvelope(
                base.schemaVersion(), base.requestId(), base.sentAt(), base.tenantId(),
                base.environment(), base.actionId(), base.actionVersion(), base.effect(),
                base.permit(), null, base.authorizationDigest(), base.authorizationEvidenceRef(),
                base.businessOperationId(), base.attemptId(), base.dispatchId(),
                base.externalIdempotencyKey(), base.resourceRef(), base.preconditions(),
                base.preconditionsHash(), base.expectedResourceVersions(), base.purpose(),
                base.payload(), base.payloadSchemaVersion(),
                "ab".repeat(32), base.authorizationBindingDigest(),
                base.traceId(), base.correlationId(), base.causationId(),
                base.originalBindingRef());
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway().admitAgentAction(tampered));
        assertAll("信封 effectDigest 篡改 → PERMIT_BINDING_MISMATCH",
                () -> assertEquals(ActionOutcomeCode.PERMIT_BINDING_MISMATCH, rejected.outcomeCode()),
                () -> assertEquals("permit.binding.field.mismatch", rejected.internalReason().code()));
    }

    /**
     * 时间窗独立收敛：digest 不含时间窗，过期许可不触发绑定比对而是被窗口检查
     * 独立拒绝（副本保持窗序——notBefore &lt; expiresAt 是载体构造校验）。
     * 真实链路中 PermitIssuanceService 不会签出已过期许可，此用例锁定消费侧
     * 对「许可被换/被延迟投递」的防线。
     */
    @Test
    void expiredPermitCopyFailsClosed() {
        Req req = new Req();
        IssuedExecutionPermit ours = issue(req);
        GatewayEgressCommand carrier = carrier(req, ours);
        ExecutionPermit expired = withTimeWindow(toFastDddPermit(ours),
                NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway().admitAgentAction(toEnvelope(carrier, expired)));
        assertAll("过期许可副本 → PERMIT_INVALID_OR_EXPIRED",
                () -> assertEquals(ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, rejected.outcomeCode()),
                () -> assertEquals("permit.expired", rejected.internalReason().code()));
    }

    /**
     * 勘误（2026-08-16）核心负向：真实签发链路的 permit <b>恒携</b> preconditionsHash
     * （来自审批事实 ApprovalGrant，非载体自报）——信封若不直携对等主张（投 null），
     * 提供状态单方错配即拒。这正是 §5.5 勘误前「投影恒投 null 会把真实载体全量
     * 拒掉」缝隙的存在性证明：真实形态下信封必须直携。
     */
    @Test
    void envelopeDroppingPreconditionsCarriageFailsClosed() {
        Req req = new Req();
        IssuedExecutionPermit ours = issue(req);
        GatewayEgressCommand carrier = carrier(req, ours);
        BusinessActionEnvelope base = toEnvelope(carrier, toFastDddPermit(ours));
        BusinessActionEnvelope withoutCarriage = new BusinessActionEnvelope(
                base.schemaVersion(), base.requestId(), base.sentAt(), base.tenantId(),
                base.environment(), base.actionId(), base.actionVersion(), base.effect(),
                base.permit(), null, base.authorizationDigest(), base.authorizationEvidenceRef(),
                base.businessOperationId(), base.attemptId(), base.dispatchId(),
                base.externalIdempotencyKey(), base.resourceRef(), base.preconditions(),
                null, null, base.purpose(),
                base.payload(), base.payloadSchemaVersion(), base.effectDigest(),
                base.authorizationBindingDigest(), base.traceId(), base.correlationId(),
                base.causationId(), base.originalBindingRef());
        GatewayOutcome.Rejected rejected = assertInstanceOf(GatewayOutcome.Rejected.class,
                gateway().admitAgentAction(withoutCarriage));
        assertAll("真实 permit 恒携 preconditionsHash + 信封缺直携 → presence mismatch",
                () -> assertEquals(ActionOutcomeCode.PERMIT_BINDING_MISMATCH, rejected.outcomeCode()),
                () -> assertEquals("permit.binding.presence.mismatch",
                        rejected.internalReason().code()));
    }

    // ------------------------------------------------------------------ digest 跨实现复算（消费方独立复验能力）

    /**
     * 真实载体的 effectDigest 可由本仓 {@link ActionDigestCalculator} 按 ADR-014
     * §2 规则 6 投影公式独立复算（投影按 ADR 冻结公式在测试域显式构造，非抄实现）——
     * 网关消费方具备「不信任、只复验」的消化能力，与 FastAgent 侧同构测试互为镜像。
     */
    @Test
    void carrierEffectDigestIsReproducibleByLocalCalculator() {
        Map<String, Object> effectProjection = new LinkedHashMap<>();
        effectProjection.put("tenantId", "acme");
        effectProjection.put("environment", "dev");
        effectProjection.put("actionId", ACTION_ID);
        effectProjection.put("actionVersion", "1.0.0");
        effectProjection.put("effect", "WRITE");
        effectProjection.put("resourceType", "ArInvoice");
        effectProjection.put("resourceId", "INV-1");
        effectProjection.put("resourceConstraints", Map.of("resourceTypes", List.of("ArInvoice")));
        effectProjection.put("purpose", "dunning.firstNotice");
        effectProjection.put("dataClassification", "CUSTOMER_FINANCIAL");
        effectProjection.put("businessOperationId", OP_ID);
        effectProjection.put("externalIdempotencyKey", "idem-1");
        effectProjection.put("invoiceId", "INV-1");

        Req req = new Req();
        IssuedExecutionPermit ours = issue(req);
        GatewayEgressCommand carrier = carrier(req, ours);
        assertEquals(ours.effectDigest(), carrier.effectDigest(),
                "夹具自检：载体摘要与签发产物一致（构造点交叉校验的测试域见证）");
        assertEquals(ours.effectDigest(), ActionDigestCalculator.effectDigest(effectProjection),
                "真实载体 effectDigest 可被本仓实现按 ADR-014 §2 规则 6 投影独立复算");
    }

    // ------------------------------------------------------------------ 映射域副本构造（record 无 wither）

    /** 时间窗副本（保持窗序：notBefore &lt; expiresAt 是载体构造校验）。 */
    private static ExecutionPermit withTimeWindow(ExecutionPermit p, Instant notBefore,
                                                  Instant expiresAt) {
        return new ExecutionPermit(p.schemaVersion(), p.permitId(), p.issuer(), p.audience(),
                p.caller(), p.actionId(), p.actionVersion(), p.effect(), p.resourceConstraints(),
                p.purpose(), p.businessOperationId(), p.authorizationDigest(),
                p.authorizationEvidenceRef(), p.effectDigest(), p.authorizationBindingDigest(),
                notBefore.atOffset(ZoneOffset.UTC), expiresAt.atOffset(ZoneOffset.UTC),
                p.externalIdempotencyKey(), p.preconditionsHash(),
                p.expectedResourceVersions(), p.usageLimit(), p.dataBudget(), p.confirmation(),
                p.policyDecisionRef());
    }
}
