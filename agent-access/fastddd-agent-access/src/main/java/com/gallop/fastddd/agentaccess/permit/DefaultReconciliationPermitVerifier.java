package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerificationException;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerifier;

import java.time.Duration;
import java.time.Instant;

/**
 * {@link ReconciliationPermitVerifier} 默认 fail-closed 实现（Contract §5.7）。
 *
 * <p>每个拒绝点携带小写点分细因码（审计/测试断言用）；<b>编排层不得按细因
 * 差异化外码</b>——统一映射 {@code NOT_VISIBLE}（存在性不泄露），这正是
 * 本实现使用 {@link ReconciliationPermitVerificationException} 而非携带
 * PERMIT_* 外码的 {@code PermitVerificationException} 的原因。</p>
 *
 * <p><b>workload 绑定恒定强制</b>：与执行许可的
 * {@code requireSenderConfirmation} 可配置不同，对账许可的 recovery
 * workload 绑定是 §5.7 冻结语义（「绑定当前已认证 recovery Workload」），
 * 不受该 flag 放宽——flag 只影响执行许可路径。配置复用
 * {@link PermitVerifierConfig} 以共享 skew 上界（TM-20）与 issuer/audience
 * 口径。</p>
 */
public final class DefaultReconciliationPermitVerifier implements ReconciliationPermitVerifier {

    private static final String PERMIT_MISSING = "reconciliation.permit.missing";
    private static final String NOT_YET_VALID = "reconciliation.window.not.yet.valid";
    private static final String EXPIRED = "reconciliation.window.expired";
    private static final String ISSUER_UNTRUSTED = "reconciliation.issuer.untrusted";
    private static final String AUDIENCE_MISMATCH = "reconciliation.audience.mismatch";
    private static final String WORKLOAD_MISSING = "reconciliation.workload.missing";
    private static final String WORKLOAD_MISMATCH = "reconciliation.workload.mismatch";

    private final PermitVerifierConfig config;

    public DefaultReconciliationPermitVerifier(PermitVerifierConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 必选");
        }
        this.config = config;
    }

    @Override
    public ReconciliationPermit verify(ReconciliationPermit permit, String authenticatedRecoveryWorkloadId) {
        if (permit == null) {
            throw new ReconciliationPermitVerificationException(PERMIT_MISSING,
                    "对账许可缺失（无许可即拒绝，不进入记录查询）");
        }
        verifyTimeWindow(permit);
        verifyTrust(permit);
        verifyRecoveryWorkloadBinding(permit, authenticatedRecoveryWorkloadId);
        return permit;
    }

    private void verifyTimeWindow(ReconciliationPermit permit) {
        Instant now = config.clock().instant();
        Duration skew = config.maxClockSkew();
        if (now.isBefore(permit.notBefore().toInstant().minus(skew))) {
            throw new ReconciliationPermitVerificationException(NOT_YET_VALID,
                    "对账许可尚未生效（notBefore 早于当前时刻减偏移）");
        }
        if (now.isAfter(permit.expiresAt().toInstant().plus(skew))) {
            throw new ReconciliationPermitVerificationException(EXPIRED,
                    "对账许可已过期（expiresAt 加偏移仍早于当前时刻；原执行 Permit 过期不构成对账豁免）");
        }
    }

    private void verifyTrust(ReconciliationPermit permit) {
        if (!config.trustedIssuers().contains(permit.issuer())) {
            throw new ReconciliationPermitVerificationException(ISSUER_UNTRUSTED,
                    "对账许可发行方不在受信集合：issuer 拒绝");
        }
        if (!config.expectedAudience().equals(permit.audience())) {
            throw new ReconciliationPermitVerificationException(AUDIENCE_MISMATCH,
                    "对账许可 audience 与本资源服务器期望不符");
        }
    }

    private void verifyRecoveryWorkloadBinding(ReconciliationPermit permit, String authenticatedWorkloadId) {
        // 恒定强制（§5.7）：不读 requireSenderConfirmation——该 flag 只放宽执行许可路径
        if (authenticatedWorkloadId == null || authenticatedWorkloadId.isBlank()) {
            throw new ReconciliationPermitVerificationException(WORKLOAD_MISSING,
                    "已认证 recovery Workload 缺失（§5.7：对账许可必须绑定当前已认证 workload）");
        }
        if (!authenticatedWorkloadId.equals(permit.recoveryWorkloadId())) {
            throw new ReconciliationPermitVerificationException(WORKLOAD_MISMATCH,
                    "已认证 workload 与许可绑定的 recoveryWorkloadId 不符");
        }
    }
}
