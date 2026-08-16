package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.InternalReason;

/**
 * 动作未暴露拒绝（AST-05：未注册 / disabled / 版本漂移的外部结果码统一
 * {@code ACTION_NOT_EXPOSED}——错误响应不得泄露「动作存在但被禁用」与
 * 「动作不存在」的差异；{@link #internalReason()} 的细因是内部 reason，
 * 仅供本端审计，禁止进入 wire 响应）。
 */
public class ActionNotExposedException extends RuntimeException {

    private final transient InternalReason reason;

    public ActionNotExposedException(InternalReason reason) {
        super(reason == null ? "action not exposed" : reason.code());
        this.reason = reason;
    }

    /** 细因（内部 reason；外部一律 ACTION_NOT_EXPOSED）。 */
    public InternalReason internalReason() {
        return reason;
    }

    /** 外部稳定结果码（恒为 ACTION_NOT_EXPOSED）。 */
    public ActionOutcomeCode outcomeCode() {
        return ActionOutcomeCode.ACTION_NOT_EXPOSED;
    }
}
