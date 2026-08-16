package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;

/**
 * 许可验证安全 Bean（Contract §5.2「许可验证必须 fail-closed」；AAC-03/04 +
 * AST-01 的执行点）。
 *
 * <p>验证清单（缺一即 {@link PermitVerificationException}，无任何默认放行）：</p>
 * <ol>
 *   <li>结构与版本：许可本体结构由 record 紧致校验保证，验证器复核 schemaVersion；</li>
 *   <li>时间窗：当前时刻 ∈ [notBefore − skew, expiresAt + skew]，skew 有上界
 *       （{@link PermitVerifierConfig#MAX_ALLOWED_CLOCK_SKEW}，TM-20）；</li>
 *   <li>issuer ∈ 受信发行方集合；audience = 本资源服务器期望值；</li>
 *   <li>sender binding：配置要求时 confirmation 必须提供（REMOTE 高风险场景）；</li>
 *   <li>{@link PermitBindingView} 同名字段逐项比对：必选项缺失即拒绝，
 *       按 Descriptor 项双方提供状态敏感。</li>
 * </ol>
 *
 * <p>实现约束：显式安全 Bean（kit Validator 必需项，缺失启动失败）；
 * 默认实现 {@code .permit.DefaultExecutionPermitVerifier}；验证通过返回原
 * 不可变许可——「通过验证」这一事实的载体化（如受控 carrier）由
 * {@link PermitIssuer} 单独承担，不与验证混合。</p>
 */
public interface ExecutionPermitVerifier {

    /**
     * fail-closed 验证许可与其 Envelope 绑定投影。
     *
     * @param permit  待验证许可
     * @param binding Envelope 治理字段投影（可为 null —— 无绑定上下文即拒绝）
     * @return 验证通过的不可变许可
     * @throws PermitVerificationException 任一验证事实不成立
     */
    ExecutionPermit verify(ExecutionPermit permit, PermitBindingView binding);
}
