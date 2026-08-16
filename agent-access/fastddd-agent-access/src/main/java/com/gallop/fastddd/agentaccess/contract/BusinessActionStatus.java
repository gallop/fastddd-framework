package com.gallop.fastddd.agentaccess.contract;

/**
 * Status 查询响应五态（Contract §5.7 冻结；sealed——形态封闭，不引入第六态）。
 *
 * <p>数据最小化逐态承载：</p>
 * <ul>
 *   <li>{@link Completed}：返回稳定 {@link BusinessActionReceipt}；</li>
 *   <li>{@link FailedFinal}：返回确定失败分类（{@code ActionOutcomeCode}，
 *       不携带内部 reason——细因经审计通道，不进 wire）；</li>
 *   <li>{@link InProgress} / {@link Unknown}：仅状态语义，无负载——
 *       {@code UNKNOWN} 是可证明性状态，禁止映射为任何具体失败；</li>
 *   <li>{@link NotVisible}：<b>零负载</b>——错误 tenant/无权查询统一响应，
 *       不携带存在性、租户差异或字段值信息（§9：不得泄露其他 tenant
 *       的资源存在性）。</li>
 * </ul>
 */
public sealed interface BusinessActionStatus {

    OutcomeVisibility visibility();

    /** 已完成：返回稳定 Receipt（对账收敛的唯一定态来源）。 */
    record Completed(BusinessActionReceipt receipt) implements BusinessActionStatus {

        public Completed {
            if (receipt == null) {
                throw new IllegalArgumentException("receipt 必选（COMPLETED 必须携带稳定 Receipt）");
            }
        }

        @Override
        public OutcomeVisibility visibility() {
            return OutcomeVisibility.COMPLETED;
        }
    }

    /** 确定失败：携带失败分类（外部稳定结果码，非内部细因）。 */
    record FailedFinal(ActionOutcomeCode outcomeCode) implements BusinessActionStatus {

        public FailedFinal {
            if (outcomeCode == null) {
                throw new IllegalArgumentException("outcomeCode 必选（FAILED_FINAL 必须携带确定失败分类）");
            }
            if (outcomeCode == ActionOutcomeCode.ALREADY_COMPLETED) {
                throw new IllegalArgumentException(
                        "ALREADY_COMPLETED 是已提交态，不得作为 FAILED_FINAL 分类（应经 COMPLETED 收敛）");
            }
        }

        @Override
        public OutcomeVisibility visibility() {
            return OutcomeVisibility.FAILED_FINAL;
        }
    }

    /** 进行中：尚不能重试。 */
    record InProgress() implements BusinessActionStatus {

        @Override
        public OutcomeVisibility visibility() {
            return OutcomeVisibility.IN_PROGRESS;
        }
    }

    /** 无法证明成功或失败：禁止盲重试，按原操作查询/人工处置。 */
    record Unknown() implements BusinessActionStatus {

        @Override
        public OutcomeVisibility visibility() {
            return OutcomeVisibility.UNKNOWN;
        }
    }

    /** 错误 tenant/无权查询统一响应：零负载，不泄露存在性。 */
    record NotVisible() implements BusinessActionStatus {

        @Override
        public OutcomeVisibility visibility() {
            return OutcomeVisibility.NOT_VISIBLE;
        }
    }
}
