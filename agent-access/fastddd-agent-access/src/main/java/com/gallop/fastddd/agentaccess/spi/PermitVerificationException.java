package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.InternalReason;

/**
 * 许可验证失败异常（fail-closed 拒绝，AAC-03/04/AST-01 信号）。
 *
 * <p>语义：任一验证事实不成立（未知版本、必填缺失、时间无效、issuer/audience/
 * sender 不匹配、与 Envelope 同名字段不一致、PDP 故障或 INDETERMINATE）即拒绝，
 * <b>不存在</b>默认放行、静默降级或无限期缓存路径。消息只携带失败原因与
 * 涉及字段名，不得携带 payload、secret 或完整许可内容。</p>
 *
 * <p>携带结构化 {@link InternalReason}：外码二分为
 * {@code PERMIT_INVALID_OR_EXPIRED}（许可自身效力不成立）与
 * {@code PERMIT_BINDING_MISMATCH}（Envelope 绑定投影与许可不一致/缺失）。
 * 网关按此映射结果码，不解析消息文本。</p>
 */
public class PermitVerificationException extends RuntimeException {

    private final InternalReason internalReason;

    /**
     * @param internalReason 结构化细因（必选；外码限 PERMIT_INVALID_OR_EXPIRED /
     *                       PERMIT_BINDING_MISMATCH）
     * @param message        失败原因与涉及字段名（不含 payload/secret）
     * @throws IllegalArgumentException internalReason 缺失或外码超出许可验证语义域
     */
    public PermitVerificationException(InternalReason internalReason, String message) {
        super(message);
        if (internalReason == null) {
            throw new IllegalArgumentException("internalReason 必选");
        }
        if (!isPermitVerificationOutcome(internalReason)) {
            throw new IllegalArgumentException(
                    "许可验证细因的外码只能是 PERMIT_INVALID_OR_EXPIRED 或 PERMIT_BINDING_MISMATCH："
                            + internalReason.code());
        }
        this.internalReason = internalReason;
    }

    /** 结构化细因（供网关映射结果码；多内→一外，反向唯一由 {@link InternalReason} 约束）。 */
    public InternalReason internalReason() {
        return internalReason;
    }

    private static boolean isPermitVerificationOutcome(InternalReason reason) {
        return reason.outcome() == ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED
                || reason.outcome() == ActionOutcomeCode.PERMIT_BINDING_MISMATCH;
    }
}
