package com.gallop.fastddd.agentaccess.spi;

/**
 * 对账许可验证失败异常（Contract §5.7 fail-closed 信号）。
 *
 * <p><b>与 {@link PermitVerificationException} 刻意分离</b>：执行许可失败
 * 映射 {@code PERMIT_INVALID_OR_EXPIRED}/{@code PERMIT_BINDING_MISMATCH}
 * 外码；而对账查询的失败语义是<b>存在性不泄露</b>——编排层
 * （OutcomeStatusPort 参考编排，FDD-AA-14 子任务 4）必须把本异常统一映射
 * {@code NOT_VISIBLE}，不区分「许可无效」「无权」「租户错位」，防止通过
 * 差异化错误探测 {@code businessOperationId} 是否存在。本类型不携带
 * {@code InternalReason}，正是因为它<b>不应</b>映射为任何
 * PERMIT_* 外码。</p>
 *
 * <p>消息只携带失败原因与涉及字段名，不携带 payload、secret 或完整许可。</p>
 */
public class ReconciliationPermitVerificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;

    /**
     * @param reasonCode 结构化细因码（小写点分，如 {@code reconciliation.issuer.untrusted}；审计与测试断言用，
     *                   不得作为外码透出）
     * @param message    失败原因与涉及字段名（不含 payload/secret）
     * @throws IllegalArgumentException reasonCode/message 缺失或空白
     */
    public ReconciliationPermitVerificationException(String reasonCode, String message) {
        super(requireMessage(message));
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode 必选且不得为空白");
        }
        this.reasonCode = reasonCode;
    }

    /** 结构化细因码（仅供审计与测试断言；编排层统一映射 NOT_VISIBLE，不得透出为外码）。 */
    public String reasonCode() {
        return reasonCode;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 必选且不得为空白");
        }
        return message;
    }
}
