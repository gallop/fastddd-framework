package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;

/**
 * 网关审计白名单投影（Contract §10 字段白名单；零 payload）。
 *
 * <p>只允许 §10 枚举的治理标识字段：不携带 effectDigest、authorizationDigest
 * 等任何摘要，不携带 prompt、工具响应、业务 payload 或 secret。字段值取自
 * <b>许可本体</b>（而非请求自报的绑定投影）——拒绝路径上许可未必通过验证，
 * 审计照常记录其治理标识。</p>
 *
 * <p>{@code outcomeCode} 表达<b>终态</b>结果码：拒绝路径必填（外码已知）；
 * 准入通过等中间事件尚无终态，骨架期允许缺失，FDD-AA-13 Envelope 执行
 * 闭环后由终态强制回填。{@code traceId} 是租户内辅助关联键，不是授权条件
 * （与 FastAgent 侧 Evidence 治理同源口径）；骨架期入参无 trace 来源，
 * 允许缺失。</p>
 *
 * @param tenantId            权威租户作用域
 * @param businessOperationId 稳定业务操作 ID
 * @param actionId            动作标识
 * @param actionVersion       动作契约版本
 * @param subjectId           最终责任主体 ID
 * @param actorId             行动主体 ID（Agent 场景为稳定 Agent ID）
 * @param workloadId          发送调用的 Egress/服务实例稳定身份引用
 * @param permitId            许可 ID（调查与消费记录的键）
 * @param traceId             可选：租户内辅助关联键（非授权条件）
 * @param outcomeCode         可选：终态结果码（拒绝路径必填）
 */
public record AuditProjection(
        String tenantId,
        String businessOperationId,
        String actionId,
        String actionVersion,
        String subjectId,
        String actorId,
        String workloadId,
        String permitId,
        String traceId,
        ActionOutcomeCode outcomeCode) {

    public AuditProjection {
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("businessOperationId", businessOperationId);
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        requireNonBlank("subjectId", subjectId);
        requireNonBlank("actorId", actorId);
        requireNonBlank("workloadId", workloadId);
        requireNonBlank("permitId", permitId);
        if (traceId != null && traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 可选但提供时不得为空白");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}
