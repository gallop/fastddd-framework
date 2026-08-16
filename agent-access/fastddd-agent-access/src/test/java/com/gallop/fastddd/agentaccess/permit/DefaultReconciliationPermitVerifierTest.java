package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;
import com.gallop.fastddd.agentaccess.contract.ReconciliationScope;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerificationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §5.7 对账许可验证逐项 fail-closed：时间窗（含 skew 边界）、issuer/audience、
 * recovery workload 绑定恒定强制（不受 requireSenderConfirmation 放宽）。
 * 所有失败统一为 {@link ReconciliationPermitVerificationException}
 * （NOT_VISIBLE 映射属编排层 FDD-AA-14 子任务 4，不在此测试）。
 */
class DefaultReconciliationPermitVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";
    private static final String RECOVERY_WORKLOAD = "workload-recovery-01";

    private static PermitVerifierConfig config(boolean requireSenderConfirmation) {
        return new PermitVerifierConfig(Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of(TRUSTED_ISSUER), AUDIENCE, Duration.ofMinutes(1), requireSenderConfirmation);
    }

    private static ReconciliationPermit permit(String issuer, String audience,
                                               OffsetDateTime notBefore, OffsetDateTime expiresAt,
                                               String recoveryWorkloadId) {
        return new ReconciliationPermit(AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "recon-2026-0001", issuer, audience, ReconciliationScope.STATUS_QUERY,
                recoveryWorkloadId, "subject-77", "actor-42", "acme",
                "ar.createCrmFollowup", "1", "binding-2026-08-16-001",
                "bop-2026-0001", "status-minimal", notBefore, expiresAt);
    }

    private static DefaultReconciliationPermitVerifier verifier() {
        return new DefaultReconciliationPermitVerifier(config(false));
    }

    @Test
    void validPermitWithAuthenticatedWorkloadPasses() {
        ReconciliationPermit permit = permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                RECOVERY_WORKLOAD);
        assertSame(permit, verifier().verify(permit, RECOVERY_WORKLOAD));
    }

    @Test
    void skewToleranceCoversBoundaryDeviation() {
        // notBefore = NOW + 30s，在 1 分钟 skew 内 → 放行（TM-20 上界内的容忍）
        ReconciliationPermit permit = permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofSeconds(30)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                RECOVERY_WORKLOAD);
        assertSame(permit, verifier().verify(permit, RECOVERY_WORKLOAD));
    }

    @Test
    void notYetValidBeyondSkewFailsClosed() {
        ReconciliationPermit permit = permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(2)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                RECOVERY_WORKLOAD);
        assertEquals("reconciliation.window.not.yet.valid",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit, RECOVERY_WORKLOAD)).reasonCode());
    }

    @Test
    void expiredBeyondSkewFailsClosed() {
        ReconciliationPermit permit = permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(2)), ZoneOffset.UTC),
                RECOVERY_WORKLOAD);
        assertEquals("reconciliation.window.expired",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit, RECOVERY_WORKLOAD)).reasonCode());
    }

    @Test
    void untrustedIssuerAndAudienceMismatchFailClosed() {
        assertEquals("reconciliation.issuer.untrusted",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit("https://rogue.example", AUDIENCE,
                                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                                RECOVERY_WORKLOAD), RECOVERY_WORKLOAD)).reasonCode());
        assertEquals("reconciliation.audience.mismatch",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit(TRUSTED_ISSUER, "other-service",
                                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                                RECOVERY_WORKLOAD), RECOVERY_WORKLOAD)).reasonCode());
    }

    @Test
    void recoveryWorkloadBindingIsAlwaysEnforcedEvenWithSenderConfirmationRelaxed() {
        // requireSenderConfirmation=false（LOCAL_OBJECT 骨架期）也不放宽 workload 绑定（§5.7 恒定强制）
        ReconciliationPermit permit = permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC),
                RECOVERY_WORKLOAD);
        assertEquals("reconciliation.workload.missing",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit, null)).reasonCode());
        assertEquals("reconciliation.workload.missing",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit, "  ")).reasonCode());
        assertEquals("reconciliation.workload.mismatch",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(permit, "workload-OTHER")).reasonCode());
    }

    @Test
    void nullPermitFailsClosed() {
        assertEquals("reconciliation.permit.missing",
                assertThrows(ReconciliationPermitVerificationException.class,
                        () -> verifier().verify(null, RECOVERY_WORKLOAD)).reasonCode());
    }

    @Test
    void nullConfigIsProgrammingError() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultReconciliationPermitVerifier(null));
    }
}
