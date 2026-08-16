package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.spi.PermitBindingView;
import com.gallop.fastddd.agentaccess.spi.PermitVerificationException;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultExecutionPermitVerifier} fail-closed 负向测试（AAC-02 语义级：
 * 无有效许可即不放行；时间窗/信任/sender binding/绑定逐项比对均无默认放行路径）。
 *
 * <p><b>验收层级如实标注</b>：本测试覆盖验证器骨架的全部拒绝分支；
 * 「宿主真实业务表 0 变更」的 AAC-01 完整验收需要 FDD-AA-12/16 宿主接线，
 * 不由本测试代签。</p>
 */
class DefaultExecutionPermitVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";
    private static final Duration SKEW = Duration.ofMinutes(1);

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
                "ar.dunning.send-reminder",
                "1.0",
                Effect.WRITE,
                new ResourceConstraints(Set.of("ArInvoice"), null, null),
                "dunning.communicate",
                "bop-2026-0001",
                "sha256:auth",
                "evidence://fastagent/grant-1",
                "sha256:effect",
                "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                "idem-1",
                null,
                Map.of("ArInvoice/INV-001", "42"),
                null,
                null,
                null,
                null);
    }

    private static PermitBindingView matchingBinding(ExecutionPermit p) {
        return new PermitBindingView(
                p.caller().tenantId(),
                p.caller().environment(),
                p.actionId(),
                p.actionVersion(),
                p.effect(),
                p.purpose(),
                p.businessOperationId(),
                p.externalIdempotencyKey(),
                p.preconditionsHash(),
                p.expectedResourceVersions(),
                p.effectDigest(),
                p.authorizationDigest(),
                p.authorizationBindingDigest());
    }

    private static PermitVerifierConfig config(boolean requireSenderConfirmation) {
        return new PermitVerifierConfig(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of(TRUSTED_ISSUER),
                AUDIENCE,
                SKEW,
                requireSenderConfirmation);
    }

    private static DefaultExecutionPermitVerifier verifier(boolean requireSenderConfirmation) {
        return new DefaultExecutionPermitVerifier(config(requireSenderConfirmation));
    }

    @Test
    void validPermitWithMatchingBindingPassesAndReturnsSamePermit() {
        ExecutionPermit permit = permit();
        ExecutionPermit verified = assertDoesNotThrow(
                () -> verifier(false).verify(permit, matchingBinding(permit)));
        assertSame(permit, verified);
    }

    @Test
    void nullPermitIsRejected() {
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(null, matchingBinding(permit())));
    }

    @Test
    void nullBindingProjectionIsRejected() {
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(permit(), null));
    }

    @Test
    void notYetEffectivePermitIsRejected() {
        ExecutionPermit p = permit();
        ExecutionPermit future = withWindow(p,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(future, matchingBinding(future)));
    }

    @Test
    void notBeforeWithinSkewToleranceIsAccepted() {
        ExecutionPermit p = permit();
        // notBefore 在 now 之后 30s，但在 skew 上界（1min）宽容范围内
        ExecutionPermit edge = withWindow(p,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofSeconds(30)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertDoesNotThrow(() -> verifier(false).verify(edge, matchingBinding(edge)));
    }

    @Test
    void expiredPermitIsRejected() {
        ExecutionPermit p = permit();
        ExecutionPermit expired = withWindow(p,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC));
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(expired, matchingBinding(expired)));
    }

    @Test
    void expiryWithinSkewToleranceIsAccepted() {
        ExecutionPermit p = permit();
        // expiresAt 在 now 之前 30s，但在 skew 上界（1min）宽容范围内
        ExecutionPermit edge = withWindow(p,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofSeconds(30)), ZoneOffset.UTC));
        assertDoesNotThrow(() -> verifier(false).verify(edge, matchingBinding(edge)));
    }

    @Test
    void untrustedIssuerIsRejected() {
        ExecutionPermit forged = new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-rogue",
                "https://rogue-issuer.example",
                AUDIENCE,
                caller(),
                "ar.dunning.send-reminder",
                "1.0",
                Effect.WRITE,
                new ResourceConstraints(Set.of("ArInvoice"), null, null),
                "dunning.communicate",
                "bop-2026-0001",
                "sha256:auth",
                "evidence://fastagent/grant-1",
                "sha256:effect",
                "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                "idem-1",
                null,
                Map.of("ArInvoice/INV-001", "42"),
                null,
                null,
                null,
                null);
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(forged, matchingBinding(forged)));
    }

    @Test
    void wrongAudienceIsRejected() {
        PermitVerifierConfig forOtherAudience = new PermitVerifierConfig(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of(TRUSTED_ISSUER),
                "different-resource-server",
                SKEW,
                false);
        DefaultExecutionPermitVerifier v = new DefaultExecutionPermitVerifier(forOtherAudience);
        ExecutionPermit p = permit();
        assertThrows(PermitVerificationException.class, () -> v.verify(p, matchingBinding(p)));
    }

    @Test
    void missingSenderConfirmationIsRejectedWhenRequired() {
        // REMOTE 高风险语义：requireSenderConfirmation=true 且许可无 confirmation → 拒绝
        ExecutionPermit p = permit();
        assertThrows(PermitVerificationException.class,
                () -> verifier(true).verify(p, matchingBinding(p)));
    }

    @Test
    void bindingProjectionMissingRequiredFieldIsRejected() {
        ExecutionPermit p = permit();
        PermitBindingView noActionVersion = new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(),
                null, // 必选同名项缺失：无法证明一致即拒绝
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(p, noActionVersion));
    }

    @Test
    void bindingProjectionFieldMismatchIsRejected() {
        ExecutionPermit p = permit();
        PermitBindingView tamperedBusinessOperationId = new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(),
                "bop-ATTACKED", // 与许可不一致：effect/business/摘要绑定被破坏
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(p, tamperedBusinessOperationId));
    }

    @Test
    void presenceSensitiveFieldSingleSidedPresenceIsRejected() {
        ExecutionPermit p = permit();
        // 许可携带 externalIdempotencyKey，但投影缺失 → 提供状态不一致即拒绝
        PermitBindingView droppedIdempotencyKey = new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                null, p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(p, droppedIdempotencyKey));
    }

    @Test
    void presenceSensitiveMapSingleSidedPresenceIsRejected() {
        ExecutionPermit p = permit();
        // 许可携带 expectedResourceVersions，但投影缺失 → CAS 声明单方缺失即拒绝
        PermitBindingView droppedVersions = new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), null,
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(p, droppedVersions));
    }

    @Test
    void tenantEscalationThroughBindingIsRejected() {
        ExecutionPermit p = permit();
        // 投影声称别的租户：tenant/environment 必选同名项比对失败
        PermitBindingView crossTenant = new PermitBindingView(
                "tenant-victim", p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
        assertThrows(PermitVerificationException.class,
                () -> verifier(false).verify(p, crossTenant));
    }

    @Test
    void configRejectsSkewBeyondGovernedCeilingAndEmptyTrust() {
        // TM-20：skew 上界的上界（5min）——更大 skew 属配置错误，不得变相放行过期许可
        assertAll("PermitVerifierConfig 自身 fail-closed",
                () -> assertThrows(IllegalArgumentException.class, () -> new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                        PermitVerifierConfig.MAX_ALLOWED_CLOCK_SKEW.plusSeconds(1), false)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(), AUDIENCE, SKEW, false)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), " ", SKEW, false)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PermitVerifierConfig(
                        null, Set.of(TRUSTED_ISSUER), AUDIENCE, SKEW, false)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PermitVerifierConfig(
                        Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE, null, false)),
                () -> assertTrue(PermitVerifierConfig.MAX_ALLOWED_CLOCK_SKEW.equals(Duration.ofMinutes(5))));
    }

    private static ExecutionPermit withWindow(ExecutionPermit p, OffsetDateTime notBefore,
                                              OffsetDateTime expiresAt) {
        return new ExecutionPermit(
                p.schemaVersion(), p.permitId(), p.issuer(), p.audience(), p.caller(),
                p.actionId(), p.actionVersion(), p.effect(), p.resourceConstraints(), p.purpose(),
                p.businessOperationId(), p.authorizationDigest(), p.authorizationEvidenceRef(),
                p.effectDigest(), p.authorizationBindingDigest(),
                notBefore, expiresAt,
                p.externalIdempotencyKey(), p.preconditionsHash(), p.expectedResourceVersions(),
                p.usageLimit(), p.dataBudget(), p.confirmation(), p.policyDecisionRef());
    }
}
