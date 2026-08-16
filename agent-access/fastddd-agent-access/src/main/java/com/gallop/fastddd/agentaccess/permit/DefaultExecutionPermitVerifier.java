package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.spi.ExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.PermitBindingView;
import com.gallop.fastddd.agentaccess.spi.PermitVerificationException;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ExecutionPermitVerifier} 默认 fail-closed 实现（Contract §5.2
 * 验证段逐项执行；无默认放行路径）。
 *
 * <p>sender binding 说明（如实标注）：{@code confirmation} 的强制校验由
 * {@link PermitVerifierConfig#requireSenderConfirmation()} 控制——REMOTE
 * 高风险场景必须开启；LOCAL_OBJECT Profile 骨架期允许关闭，升级到 REMOTE
 * 时只允许收紧不允许放宽。mTLS/DPoP 的密码学验证属传输/凭据层（FDD-AA-12+
 * 落地），本实现校验「要求时必须提供」。</p>
 */
public final class DefaultExecutionPermitVerifier implements ExecutionPermitVerifier {

    private final PermitVerifierConfig config;

    public DefaultExecutionPermitVerifier(PermitVerifierConfig config) {
        this.config = Objects.requireNonNull(config, "config 必选");
    }

    @Override
    public ExecutionPermit verify(ExecutionPermit permit, PermitBindingView binding) {
        if (permit == null) {
            throw new PermitVerificationException("许可缺失（AAC-02：无 Permit 不放行）");
        }
        if (binding == null) {
            throw new PermitVerificationException("绑定投影缺失：无法证明 Envelope 与许可一致即拒绝");
        }
        verifyTimeWindow(permit);
        verifyTrust(permit);
        verifySenderBinding(permit);
        verifyBindingAgreement(permit, binding);
        return permit;
    }

    private void verifyTimeWindow(ExecutionPermit permit) {
        Instant now = config.clock().instant();
        Duration skew = config.maxClockSkew();
        if (now.isBefore(permit.notBefore().toInstant().minus(skew))) {
            throw new PermitVerificationException("许可尚未生效（notBefore 早于当前时刻减偏移）");
        }
        if (now.isAfter(permit.expiresAt().toInstant().plus(skew))) {
            throw new PermitVerificationException("许可已过期（expiresAt 加偏移仍早于当前时刻）");
        }
    }

    private void verifyTrust(ExecutionPermit permit) {
        if (!config.trustedIssuers().contains(permit.issuer())) {
            throw new PermitVerificationException("许可发行方不在受信集合：issuer 拒绝");
        }
        if (!config.expectedAudience().equals(permit.audience())) {
            throw new PermitVerificationException("许可 audience 与本资源服务器期望不符");
        }
    }

    private void verifySenderBinding(ExecutionPermit permit) {
        if (config.requireSenderConfirmation() && permit.confirmation() == null) {
            throw new PermitVerificationException("配置要求 sender binding 但许可未携带 confirmation");
        }
    }

    private void verifyBindingAgreement(ExecutionPermit permit, PermitBindingView binding) {
        // 必选同名项：投影缺失即拒绝（无法证明一致等于不一致）
        requireAgreement("tenantId", binding.tenantId(), permit.caller().tenantId());
        requireAgreement("environment", binding.environment(), permit.caller().environment());
        requireAgreement("actionId", binding.actionId(), permit.actionId());
        requireAgreement("actionVersion", binding.actionVersion(), permit.actionVersion());
        requireAgreement("effect", binding.effect() == null ? null : binding.effect().name(),
                permit.effect().name());
        requireAgreement("purpose", binding.purpose(), permit.purpose());
        requireAgreement("businessOperationId", binding.businessOperationId(), permit.businessOperationId());
        requireAgreement("effectDigest", binding.effectDigest(), permit.effectDigest());
        requireAgreement("authorizationDigest", binding.authorizationDigest(), permit.authorizationDigest());
        requireAgreement("authorizationBindingDigest", binding.authorizationBindingDigest(),
                permit.authorizationBindingDigest());
        // 按 Descriptor 项：双方提供状态敏感——单方提供即拒绝
        requirePresenceSensitive("externalIdempotencyKey", binding.externalIdempotencyKey(),
                permit.externalIdempotencyKey());
        requirePresenceSensitive("preconditionsHash", binding.preconditionsHash(),
                permit.preconditionsHash());
        requirePresenceSensitiveMap("expectedResourceVersions", binding.expectedResourceVersions(),
                permit.expectedResourceVersions());
    }

    private void requireAgreement(String field, String envelopeValue, String permitValue) {
        if (envelopeValue == null) {
            throw new PermitVerificationException("绑定投影缺失必选同名项 " + field + "：无法证明一致即拒绝");
        }
        if (!envelopeValue.equals(permitValue)) {
            throw new PermitVerificationException("绑定比对不一致：" + field);
        }
    }

    private void requirePresenceSensitive(String field, String envelopeValue, String permitValue) {
        boolean envelopePresent = envelopeValue != null;
        boolean permitPresent = permitValue != null;
        if (envelopePresent != permitPresent) {
            throw new PermitVerificationException(
                    "按 Descriptor 项 " + field + " 提供状态不一致（单方缺失即拒绝）");
        }
        if (envelopePresent && !envelopeValue.equals(permitValue)) {
            throw new PermitVerificationException("绑定比对不一致：" + field);
        }
    }

    private void requirePresenceSensitiveMap(String field, Map<String, String> envelopeValue,
                                             Map<String, String> permitValue) {
        boolean envelopePresent = envelopeValue != null;
        boolean permitPresent = permitValue != null;
        if (envelopePresent != permitPresent) {
            throw new PermitVerificationException(
                    "按 Descriptor 项 " + field + " 提供状态不一致（单方缺失即拒绝）");
        }
        if (envelopePresent && !envelopeValue.equals(permitValue)) {
            throw new PermitVerificationException("绑定比对不一致：" + field);
        }
    }
}
