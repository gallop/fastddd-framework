package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;

/**
 * 独立对账授权（Contract §5.7 冻结）。
 *
 * <p><b>独立性与不可复用（§5.7）</b>：对账查询不依赖、也不复用原执行
 * {@link ExecutionPermit}——原 Permit 可以已经过期，但<b>过期不扩大查询
 * 权限</b>；本许可绑定的才是查询权威。同一许可不得用于执行路径
 * （{@code ReconciliationScope#STATUS_QUERY} 无执行语义）。</p>
 *
 * <p><b>绑定面</b>：当前已认证 recovery Workload（可与原执行 workload
 * 不同——recovery 是独立职责）、原 subject/actor、tenant、action/version、
 * 原 binding 引用（不解析最新绑定）、audience、{@code businessOperationId}
 * 与允许返回的数据投影标识。{@code allowedProjection} 只绑定签发方定义的
 * 投影标识，不在此冻结字段级投影形态。</p>
 *
 * <p><b>收敛语义（§5.7）</b>：无记录不能等价为 {@code FAILED_FINAL}，
 * 也不得授权上游换新 {@code businessOperationId} 盲重放——本许可绑定的
 * {@code businessOperationId} 是唯一可查询的对账主键。</p>
 *
 * @param schemaVersion        wire 结构版本，首版 {@code "0.1"}
 * @param permitId             许可标识（调查与消费记录键，不自带防重放）
 * @param issuer               发行方
 * @param audience             被点名消费者（目标资源服务）
 * @param scope                授权范围（首版仅 STATUS_QUERY）
 * @param recoveryWorkloadId   当前已认证 recovery Workload 引用
 * @param originalSubjectId    原执行 subject
 * @param originalActorId      原执行 actor
 * @param tenantId             权威租户作用域
 * @param actionId             原执行动作标识
 * @param actionVersion        原执行动作版本
 * @param bindingRef           原目标 binding/version 引用（不解析最新）
 * @param businessOperationId  唯一可查询的对账主键
 * @param allowedProjection    允许返回的数据投影标识（签发方定义）
 * @param notBefore            时间窗起点
 * @param expiresAt            时间窗终点
 */
public record ReconciliationPermit(
        String schemaVersion,
        String permitId,
        String issuer,
        String audience,
        ReconciliationScope scope,
        String recoveryWorkloadId,
        String originalSubjectId,
        String originalActorId,
        String tenantId,
        String actionId,
        String actionVersion,
        String bindingRef,
        String businessOperationId,
        String allowedProjection,
        OffsetDateTime notBefore,
        OffsetDateTime expiresAt) {

    public ReconciliationPermit {
        if (!AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion 不受支持：期望 " + AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION
                            + "，实际 " + schemaVersion);
        }
        requireNonBlank("permitId", permitId);
        requireNonBlank("issuer", issuer);
        requireNonBlank("audience", audience);
        if (scope == null) {
            throw new IllegalArgumentException("scope 必选");
        }
        if (scope != ReconciliationScope.STATUS_QUERY) {
            throw new IllegalArgumentException("未受支持的对账范围：" + scope + "（首版仅 STATUS_QUERY）");
        }
        requireNonBlank("recoveryWorkloadId", recoveryWorkloadId);
        requireNonBlank("originalSubjectId", originalSubjectId);
        requireNonBlank("originalActorId", originalActorId);
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        requireNonBlank("bindingRef", bindingRef);
        requireNonBlank("businessOperationId", businessOperationId);
        requireNonBlank("allowedProjection", allowedProjection);
        if (notBefore == null || expiresAt == null) {
            throw new IllegalArgumentException("notBefore/expiresAt 必选");
        }
        if (!notBefore.isBefore(expiresAt)) {
            throw new IllegalArgumentException("notBefore 必须早于 expiresAt（结构校验；时刻有效性由验证器判定）");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}
