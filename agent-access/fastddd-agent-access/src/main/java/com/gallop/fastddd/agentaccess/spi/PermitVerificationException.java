package com.gallop.fastddd.agentaccess.spi;

/**
 * 许可验证失败异常（fail-closed 拒绝，AAC-03/04/AST-01 信号）。
 *
 * <p>语义：任一验证事实不成立（未知版本、必填缺失、时间无效、issuer/audience/
 * sender 不匹配、与 Envelope 同名字段不一致、PDP 故障或 INDETERMINATE）即拒绝，
 * <b>不存在</b>默认放行、静默降级或无限期缓存路径。消息只携带失败原因与
 * 涉及字段名，不得携带 payload、secret 或完整许可内容。</p>
 */
public class PermitVerificationException extends RuntimeException {

    public PermitVerificationException(String message) {
        super(message);
    }
}
