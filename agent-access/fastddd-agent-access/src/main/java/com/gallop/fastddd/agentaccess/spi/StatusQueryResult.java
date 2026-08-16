package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionStatus;

/**
 * Status 查询编排结果（响应五态 + 审计投影；与 {@link GatewayOutcome}
 * 同构「判定可审计」）。
 *
 * <p>{@code audit.outcomeCode} 语义：收敛到稳定结果时必填
 * （{@code ALREADY_COMPLETED}/失败分类/{@code UNKNOWN}）；
 * {@code IN_PROGRESS} 与 {@code NOT_VISIBLE} 无终态结果码，置 null——
 * 这两个<b>响应状态</b>由 {@code BusinessActionStatus} 形态承载，不是
 * 十二结果码之一，不得伪填（§9.3：不得把响应状态伪装为收敛依据）。</p>
 *
 * <p>{@code tombstoneRef} 是 AST-08 审计补充：仅当记录形态为
 * {@link OutcomeRecordView.BindingTombstoned} 时非空——
 * {@link AuditProjection} 白名单无 tombstone 字段，故并列携带，由宿主
 * 审计落库时与投影合并记录。</p>
 *
 * @param status        五态响应（必选）
 * @param audit         审计白名单投影（必选；取自许可本体）
 * @param tombstoneRef  可选：AST-08 tombstone 引用
 */
public record StatusQueryResult(BusinessActionStatus status, AuditProjection audit, String tombstoneRef) {

    public StatusQueryResult {
        if (status == null) {
            throw new IllegalArgumentException("status 必选");
        }
        if (audit == null) {
            throw new IllegalArgumentException("audit 必选");
        }
        if (tombstoneRef != null && tombstoneRef.isBlank()) {
            throw new IllegalArgumentException("tombstoneRef 可选但提供时不得为空白");
        }
        if (tombstoneRef != null && !(status instanceof BusinessActionStatus.Unknown)) {
            throw new IllegalArgumentException(
                    "tombstoneRef 只在 UNKNOWN 收敛（AST-08：原 binding 不可证明不切换新 binding 收敛成功）时携带");
        }
        if (status instanceof BusinessActionStatus.Completed && audit.outcomeCode() != ActionOutcomeCode.ALREADY_COMPLETED) {
            throw new IllegalArgumentException("COMPLETED 收敛的审计终态必须是 ALREADY_COMPLETED");
        }
        if (status instanceof BusinessActionStatus.FailedFinal failed
                && audit.outcomeCode() != failed.outcomeCode()) {
            throw new IllegalArgumentException("FAILED_FINAL 收敛的审计终态必须与失败分类一致");
        }
        if (status instanceof BusinessActionStatus.Unknown
                && tombstoneRef == null
                && audit.outcomeCode() != null
                && audit.outcomeCode() != ActionOutcomeCode.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN 收敛的审计终态只能是 UNKNOWN 或缺失");
        }
    }
}
