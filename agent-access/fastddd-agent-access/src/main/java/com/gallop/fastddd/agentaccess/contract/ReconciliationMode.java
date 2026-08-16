package com.gallop.fastddd.agentaccess.contract;

/**
 * 对账模式（Contract §5.3：reconciliationMode 在 WRITE/TASK 必选）。
 */
public enum ReconciliationMode {
    /** 可通过 Status/对账查询收敛 UNKNOWN。 */
    QUERYABLE,
    /** 仅人工对账。 */
    MANUAL_ONLY,
    /** 不支持对账（UNKNOWN 只能人工处置）。 */
    NOT_SUPPORTED
}
