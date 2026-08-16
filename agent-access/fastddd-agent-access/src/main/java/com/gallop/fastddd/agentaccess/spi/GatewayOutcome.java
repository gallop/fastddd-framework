package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.InternalReason;

/**
 * 网关准入判定结果（骨架期两态：准入通过 / 拒绝）。
 *
 * <p>骨架期网关覆盖标准执行顺序（Contract §7）步骤 3-6：验证许可 → 绑定
 * 比对 → resolve descriptor → 事务外准入预检。事务内步骤 7-13（去重、资源
 * 终检、聚合、Outbox、Receipt）归 FDD-AA-13/14 充实——「准入通过」因此
 * <b>不是</b>执行终态，十二结果码（AA-02）中没有也不会加入「成功」值；
 * 终态结果码在 Envelope 执行闭环产生。</p>
 *
 * <p>拒绝以 {@link Rejected} 值返回而非抛出：fail-closed 拒绝是网关的正常
 * 产品，必须可审计（携带 {@link AuditProjection} 与结构化细因），不与
 * 编程错误（{@code IllegalArgumentException}）混流。</p>
 */
public sealed interface GatewayOutcome permits GatewayOutcome.Admitted, GatewayOutcome.Rejected {

    /** 审计白名单投影（每个出口必带：拒绝与通过同样可审计）。 */
    AuditProjection audit();

    /**
     * 准入通过：许可验证、绑定比对、descriptor 解析与事务外准入预检全部通过。
     *
     * @param descriptor 已解析并启用的动作描述符（后续事务内步骤的权威契约来源）
     * @param audit      审计投影（骨架期 {@code outcomeCode} 允许缺失——终态未定）
     */
    record Admitted(BusinessActionDescriptor descriptor, AuditProjection audit) implements GatewayOutcome {

        public Admitted {
            if (descriptor == null) {
                throw new IllegalArgumentException("descriptor 必选（准入通过即已完成解析）");
            }
            if (audit == null) {
                throw new IllegalArgumentException("audit 必选");
            }
            if (audit.outcomeCode() != null) {
                throw new IllegalArgumentException("准入通过不是终态，audit.outcomeCode 必须缺失");
            }
        }
    }

    /**
     * fail-closed 拒绝：外部结果码 + 内部审计细因（多内→一外，AA-02）。
     *
     * @param outcomeCode  外部结果码（十二码中网关骨架可达子集）
     * @param internalReason 内部审计细因（外码与 outcomeCode 一致）
     * @param audit        审计投影（outcomeCode 必填且与本拒绝外码一致）
     */
    record Rejected(ActionOutcomeCode outcomeCode, InternalReason internalReason, AuditProjection audit)
            implements GatewayOutcome {

        public Rejected {
            if (outcomeCode == null) {
                throw new IllegalArgumentException("outcomeCode 必选");
            }
            if (internalReason == null) {
                throw new IllegalArgumentException("internalReason 必选（拒绝必须可审计细因）");
            }
            if (internalReason.outcome() != outcomeCode) {
                throw new IllegalArgumentException("细因外码与拒绝外码不一致："
                        + internalReason.outcome() + " != " + outcomeCode);
            }
            if (audit == null) {
                throw new IllegalArgumentException("audit 必选");
            }
            if (audit.outcomeCode() != outcomeCode) {
                throw new IllegalArgumentException("审计投影外码与拒绝外码不一致："
                        + audit.outcomeCode() + " != " + outcomeCode);
            }
        }
    }
}
