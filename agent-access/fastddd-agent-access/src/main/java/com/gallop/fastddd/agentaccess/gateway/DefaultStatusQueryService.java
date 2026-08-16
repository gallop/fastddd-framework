package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.BusinessActionStatus;
import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;
import com.gallop.fastddd.agentaccess.spi.AuditProjection;
import com.gallop.fastddd.agentaccess.spi.DedupKey;
import com.gallop.fastddd.agentaccess.spi.OutcomeRecordView;
import com.gallop.fastddd.agentaccess.spi.OutcomeStatusPort;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerificationException;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.StatusQueryResult;
import com.gallop.fastddd.agentaccess.spi.StatusQueryService;

/**
 * {@link StatusQueryService} 默认 fail-closed 编排（Contract §5.7 +
 * AST-08；与 {@code DefaultAgentAccessGateway} 同模式：显式安全 Bean +
 * 值对象审计投影）。
 *
 * <p>编排序（不可重排）：</p>
 * <ol>
 *   <li>许可验证（{@code ReconciliationPermitVerifier}）——失败统一
 *       {@code NOT_VISIBLE}，<b>不触碰记录 Port</b>（验证先于记录读取；
 *       存在性不泄露：许可无效与无权同响应）；</li>
 *   <li>记录读取——查询键 {@code (tenantId, businessOperationId)} 由
 *       <b>已验证许可</b>推导（租户作用域不来自请求自报）；</li>
 *   <li>收敛——按 {@link OutcomeRecordView} 最特定形态映射五态。</li>
 * </ol>
 *
 * <p><b>Recorded 收敛的完整性复验（AAC-16 同构）</b>：宿主返回的
 * Receipt 必须与查询键及许可原 {@code bindingRef} 逐项一致；任一错位
 * 即「原 binding 不可证明」——保持 {@code UNKNOWN}，不切换新 binding
 * 收敛成功，差异细因只进审计（AST-08），不进响应。</p>
 *
 * <p>{@code UNKNOWN} Receipt 例外：{@code outcomeCode == UNKNOWN} 的
 * 记录是「结果不可证明」的记载，不是稳定结果——仍收敛
 * {@code UNKNOWN}，不得作为 COMPLETED/FAILED_FINAL 依据（§9.3）。</p>
 */
public final class DefaultStatusQueryService implements StatusQueryService {

    private final ReconciliationPermitVerifier verifier;
    private final OutcomeStatusPort statusPort;

    public DefaultStatusQueryService(ReconciliationPermitVerifier verifier, OutcomeStatusPort statusPort) {
        if (verifier == null) {
            throw new IllegalArgumentException("verifier 必选");
        }
        if (statusPort == null) {
            throw new IllegalArgumentException("statusPort 必选");
        }
        this.verifier = verifier;
        this.statusPort = statusPort;
    }

    @Override
    public StatusQueryResult queryStatus(ReconciliationPermit permit, String authenticatedRecoveryWorkloadId) {
        if (permit == null) {
            throw new IllegalArgumentException("permit 必选（null 属调用方编程错误，非 NotVisible 语义）");
        }
        try {
            ReconciliationPermit verified = verifier.verify(permit, authenticatedRecoveryWorkloadId);
            return convergeByRecord(verified);
        } catch (ReconciliationPermitVerificationException verificationFailed) {
            // 统一 NOT_VISIBLE：不区分无效/过期/未绑定 workload/无权，也不读记录——存在性不泄露
            return new StatusQueryResult(new BusinessActionStatus.NotVisible(), auditOf(permit, null), null);
        }
    }

    private StatusQueryResult convergeByRecord(ReconciliationPermit permit) {
        // allowedProjection 原样透传（§5.7 冻结绑定面）：数据最小化由拥有数据的宿主执行
        OutcomeRecordView view = statusPort.find(
                new DedupKey(permit.tenantId(), permit.businessOperationId()), permit.allowedProjection());
        return switch (view) {
            case OutcomeRecordView.Recorded recorded -> convergeRecorded(permit, recorded.receipt());
            case OutcomeRecordView.InFlight inFlight -> inProgressResult(permit);
            case OutcomeRecordView.BindingTombstoned tombstoned -> {
                // AST-08：按原 binding 审计并判定；原 binding 已 tombstone 且无稳定结果 → 可证明状态是 UNKNOWN
                yield new StatusQueryResult(new BusinessActionStatus.Unknown(),
                        auditOf(permit, ActionOutcomeCode.UNKNOWN), tombstoned.tombstoneRef());
            }
            case OutcomeRecordView.Absent absent ->
                // §5.7：无记录≠FAILED，保持 UNKNOWN（上游按原操作查询/人工处置，禁止换键盲重放）
                new StatusQueryResult(new BusinessActionStatus.Unknown(),
                        auditOf(permit, ActionOutcomeCode.UNKNOWN), null);
        };
    }

    private StatusQueryResult convergeRecorded(ReconciliationPermit permit, BusinessActionReceipt receipt) {
        // §5.7 许可绑定面逐项复验（AAC-16 同构）：tenant/businessOperationId/bindingRef/
        // actionId/actionVersion/audience 任一错位即「原 binding 不可证明」
        boolean originalBindingProvable = receipt.tenantId().equals(permit.tenantId())
                && receipt.businessOperationId().equals(permit.businessOperationId())
                && receipt.bindingRef().equals(permit.bindingRef())
                && receipt.actionId().equals(permit.actionId())
                && receipt.actionVersion().equals(permit.actionVersion())
                && receipt.audience().equals(permit.audience());
        if (!originalBindingProvable) {
            // AAC-16 同构完整性错位：不可证明即 UNKNOWN；不泄露差异细因，不切换新 binding 收敛成功（AST-08）
            return new StatusQueryResult(new BusinessActionStatus.Unknown(),
                    auditOf(permit, ActionOutcomeCode.UNKNOWN), null);
        }
        if (receipt.outcomeCode() == ActionOutcomeCode.ALREADY_COMPLETED) {
            return new StatusQueryResult(new BusinessActionStatus.Completed(receipt),
                    auditOf(permit, ActionOutcomeCode.ALREADY_COMPLETED), null);
        }
        if (receipt.outcomeCode() == ActionOutcomeCode.UNKNOWN) {
            return new StatusQueryResult(new BusinessActionStatus.Unknown(),
                    auditOf(permit, ActionOutcomeCode.UNKNOWN), null);
        }
        return new StatusQueryResult(new BusinessActionStatus.FailedFinal(receipt.outcomeCode()),
                auditOf(permit, receipt.outcomeCode()), null);
    }

    private StatusQueryResult inProgressResult(ReconciliationPermit permit) {
        return new StatusQueryResult(new BusinessActionStatus.InProgress(), auditOf(permit, null), null);
    }

    private static AuditProjection auditOf(ReconciliationPermit permit, ActionOutcomeCode outcomeCode) {
        // 全部取自许可本体（验证失败路径许可未通过验证，审计照常记录其治理标识）
        return new AuditProjection(
                permit.tenantId(),
                permit.businessOperationId(),
                permit.actionId(),
                permit.actionVersion(),
                permit.originalSubjectId(),
                permit.originalActorId(),
                permit.recoveryWorkloadId(),
                permit.permitId(),
                null,
                outcomeCode);
    }
}
