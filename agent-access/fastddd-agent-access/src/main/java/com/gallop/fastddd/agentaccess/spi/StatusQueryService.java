package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionStatus;
import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;

/**
 * Status 查询编排安全 Bean（Contract §5.7；与
 * {@code AgentAccessGateway} 同级的治理出口）。
 *
 * <p>编排不变量（缺一即破坏契约语义）：</p>
 * <ol>
 *   <li><b>验证先于记录读取</b>：{@code ReconciliationPermitVerifier}
 *       通过之前不得触碰 {@link OutcomeStatusPort}；</li>
 *   <li><b>无记录仍 UNKNOWN</b>（§5.7 无记录≠FAILED，也不得授权换新
 *       {@code businessOperationId} 盲重放）；</li>
 *   <li><b>验证失败统一 NOT_VISIBLE</b>：许可无效、workload 未绑定、
 *       tenant 无权等一律同一响应，不泄露存在性；</li>
 *   <li><b>tombstone 按原 binding 审计</b>（AST-08）：禁止以当前 resolve
 *       冒充历史；原 binding 不可证明时不切换新 binding 收敛成功，
 *       保持 UNKNOWN。</li>
 * </ol>
 *
 * <p>实现约束：显式安全 Bean；默认实现
 * {@code .gateway.DefaultStatusQueryService}。</p>
 */
public interface StatusQueryService {

    /**
     * 对账查询：验证许可 → 读取宿主记录 → 收敛五态响应。
     *
     * @param permit                          对账许可（null 属调用方编程错误）
     * @param authenticatedRecoveryWorkloadId 当前已认证 recovery Workload 标识
     * @return 状态 + 审计投影（永不 null）
     */
    StatusQueryResult queryStatus(ReconciliationPermit permit, String authenticatedRecoveryWorkloadId);
}
