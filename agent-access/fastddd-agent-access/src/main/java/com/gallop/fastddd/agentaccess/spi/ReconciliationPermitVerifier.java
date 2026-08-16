package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;

/**
 * 对账许可验证安全 Bean（Contract §5.7「对账授权独立验证」；fail-closed）。
 *
 * <p>验证清单（缺一即 {@link ReconciliationPermitVerificationException}，
 * 无默认放行）：</p>
 * <ol>
 *   <li>结构与范围：{@link ReconciliationPermit} record 紧致校验在构造时
 *       保证结构与 {@code STATUS_QUERY} 范围；验证器不重复结构检查，只负责
 *       运行时事实；</li>
 *   <li>时间窗：当前时刻 ∈ [notBefore − skew, expiresAt + skew]，skew 上界
 *       复用 {@link PermitVerifierConfig#MAX_ALLOWED_CLOCK_SKEW}（TM-20）；</li>
 *   <li>issuer ∈ 受信发行方集合；audience = 本资源服务器期望值；</li>
 *   <li>recovery workload 绑定：<b>恒定强制</b>（§5.7「绑定当前已认证
 *       recovery Workload」是许可效力的一部分，不是可选 profile）——
 *       已认证 workload 缺失或与 {@code recoveryWorkloadId} 不符即拒绝。</li>
 * </ol>
 *
 * <p><b>与执行验证的边界</b>：本验证不比较 Envelope 绑定投影（对账许可
 * 独立成立，不复用原执行 Permit），也不对 {@code bindingRef} 做最新解析
 * ——原 binding 不可证明时的处置属编排层（AST-08），不属验证层。</p>
 *
 * <p>实现约束：显式安全 Bean（kit Validator 必需项，缺失启动失败）；
 * 默认实现 {@code .permit.DefaultReconciliationPermitVerifier}。</p>
 */
public interface ReconciliationPermitVerifier {

    /**
     * fail-closed 验证对账许可与其 recovery workload 绑定。
     *
     * @param permit                          待验证对账许可
     * @param authenticatedRecoveryWorkloadId 当前已认证 recovery Workload 标识（传输/凭据层认证结果）
     * @return 验证通过的不可变许可
     * @throws ReconciliationPermitVerificationException 任一验证事实不成立（编排层统一映射 NOT_VISIBLE）
     */
    ReconciliationPermit verify(ReconciliationPermit permit, String authenticatedRecoveryWorkloadId);
}
