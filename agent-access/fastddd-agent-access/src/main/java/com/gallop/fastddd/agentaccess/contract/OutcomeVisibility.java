package com.gallop.fastddd.agentaccess.contract;

/**
 * Status 查询五态响应（Contract §5.7 冻结）。
 *
 * <p>{@code UNKNOWN} 是可证明性状态不是失败分类；{@code NOT_VISIBLE} 是
 * 错误 tenant/无权查询的统一响应，<b>不泄露存在性</b>（对无权与不存在
 * 不可区分）。无记录不等价 {@code FAILED_FINAL}，也不得据此授权换新
 * {@code businessOperationId} 盲重放。</p>
 */
public enum OutcomeVisibility {
    COMPLETED,
    FAILED_FINAL,
    IN_PROGRESS,
    UNKNOWN,
    NOT_VISIBLE
}
