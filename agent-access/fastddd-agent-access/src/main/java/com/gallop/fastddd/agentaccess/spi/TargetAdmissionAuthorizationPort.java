package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;

/**
 * 事务外准入预检 Port（两段式授权第一段）。
 *
 * <p>职责：在进入业务事务<b>之前</b>对（caller, action, effect）做准入判定；
 * 可以咨询远程 PDP/FastPolicy。结果按 deny-overrides 消费：
 * {@link DecisionKind#DENY} 与 {@link DecisionKind#INDETERMINATE} 均不放行。</p>
 *
 * <p>预检通过不构成执行授权：事务内还必须经
 * {@link ResourceAuthorizationPort} 终检（TM-19：关闭预检-执行 TOCTOU 窗口）。</p>
 */
public interface TargetAdmissionAuthorizationPort {

    /**
     * 事务外准入预检。
     *
     * @param query 准入查询
     * @return 授权决定（PERMIT/DENY/INDETERMINATE；无法判定时返回 INDETERMINATE，不得抛错后默认放行）
     */
    AuthorizationDecision authorizeAdmission(AdmissionQuery query);

    /**
     * 准入查询参数。
     *
     * @param caller       已验证调用责任链
     * @param actionId     动作标识
     * @param actionVersion 动作契约版本
     * @param effect       效果类型
     */
    record AdmissionQuery(CallerContext caller, String actionId, String actionVersion, Effect effect) {
        public AdmissionQuery {
            if (caller == null) {
                throw new IllegalArgumentException("caller 必选");
            }
            if (actionId == null || actionId.isBlank()) {
                throw new IllegalArgumentException("actionId 必选且不得为空白");
            }
            if (actionVersion == null || actionVersion.isBlank()) {
                throw new IllegalArgumentException("actionVersion 必选且不得为空白");
            }
            if (effect == null) {
                throw new IllegalArgumentException("effect 必选");
            }
        }
    }
}
