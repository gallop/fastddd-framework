package com.gallop.fastddd.agentaccess.contract;

/**
 * Receipt 重试处置（Contract §5.6 冻结三值）。
 *
 * <p>上游据此决定后续动作：{@link #DO_NOT_RETRY} 终态拒绝、
 * {@link #RETRY_SAME_ATTEMPT} 原 Attempt/原 binding/原键受控重试、
 * {@link #QUERY_FIRST} 先按原操作查询收敛再决策（UNKNOWN 的强制路径——
 * 禁止盲重试，Contract §9）。</p>
 */
public enum RetryDisposition {
    DO_NOT_RETRY,
    RETRY_SAME_ATTEMPT,
    QUERY_FIRST
}
