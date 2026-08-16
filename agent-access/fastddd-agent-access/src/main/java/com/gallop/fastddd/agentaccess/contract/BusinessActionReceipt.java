package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 目标侧业务事实响应（Contract §5.6 权威字段表）。
 *
 * <p><b>语义边界（§5.6 冻结）</b>：本类型是 FastDDD 业务事实响应，
 * <b>不是 FastAgent 权威 {@code ActionReceipt} 本身</b>——后者只能由
 * FastAgent Java Kernel 追加。跨信任边界（部署 Profile §4.2
 * {@code REMOTE_RESOURCE_SERVER}）时 {@code integrityProof} 必选，由
 * 验证层强制；{@code LOCAL_OBJECT} 同 JVM 不跨信任边界时可省。</p>
 *
 * <p>跨字段校验按 §9「业务提交」列形式化（非自选语义）：十二码中仅
 * {@code ALREADY_COMPLETED} 为已提交——该码必选
 * {@code businessCommandRef}/{@code aggregateRootRef}/{@code committedAt}；
 * 其余结果码均为未提交或不定态，禁携三提交字段（拒绝类结果必须保证
 * 没有新增聚合变更、成功去重状态或业务 Outbox，§9 尾注）。</p>
 *
 * <p>{@code bindingRef} 是原目标 binding/version 引用，不解析最新绑定
 * （IN_FLIGHT/UNKNOWN 对账使用原 binding）；{@code effectDigest} 必须与
 * 去重记录一致（§8.2）。</p>
 *
 * @param schemaVersion       wire 结构版本，首版 {@code "0.1"}
 * @param tenantId            结果所属租户
 * @param businessOperationId 对账主键
 * @param actionId            执行动作标识
 * @param actionVersion       执行动作版本
 * @param effectDigest        与去重记录一致的稳定业务效果摘要
 * @param issuer              结果事实发行者
 * @param audience            被点名的结果消费者
 * @param bindingRef          原目标 binding/version 引用
 * @param integrityProof      目标签名/MAC 或等价可验证证明（跨信任边界必选）
 * @param outcomeCode         本契约稳定结果码
 * @param businessCommandRef  稳定业务引用（已提交时必选）
 * @param aggregateRootRef    最小聚合引用（已提交时必选）
 * @param resourceVersionAfter 提交后资源版本（适用时）
 * @param committedAt         业务事务提交时间（已提交时必选）
 * @param retryDisposition    上游重试处置
 * @param traceId             调查关联
 * @param responseProjection  经过数据最小化的响应字段（可选；null=不提供，
 *                            空映射=显式空投影，两者语义不同）
 */
public record BusinessActionReceipt(
        String schemaVersion,
        String tenantId,
        String businessOperationId,
        String actionId,
        String actionVersion,
        String effectDigest,
        String issuer,
        String audience,
        String bindingRef,
        String integrityProof,
        ActionOutcomeCode outcomeCode,
        String businessCommandRef,
        String aggregateRootRef,
        String resourceVersionAfter,
        OffsetDateTime committedAt,
        RetryDisposition retryDisposition,
        String traceId,
        Map<String, String> responseProjection) {

    public BusinessActionReceipt {
        if (!AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion 不受支持：期望 " + AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION
                            + "，实际 " + schemaVersion);
        }
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("businessOperationId", businessOperationId);
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        requireNonBlank("effectDigest", effectDigest);
        requireNonBlank("issuer", issuer);
        requireNonBlank("audience", audience);
        requireNonBlank("bindingRef", bindingRef);
        requireNonBlankOptional("integrityProof", integrityProof);
        if (outcomeCode == null) {
            throw new IllegalArgumentException("outcomeCode 必选");
        }
        if (outcomeCode == ActionOutcomeCode.ALREADY_COMPLETED) {
            // §9「业务提交」列：仅 ALREADY_COMPLETED 为已提交 → 三提交字段必选（§5.6 成功时必选）
            requireNonBlank("businessCommandRef", businessCommandRef);
            requireNonBlank("aggregateRootRef", aggregateRootRef);
            if (committedAt == null) {
                throw new IllegalArgumentException("committedAt 已提交时必选（ALREADY_COMPLETED）");
            }
        } else if (businessCommandRef != null || aggregateRootRef != null || committedAt != null) {
            // 拒绝/未提交/不定态禁携提交字段（§9：拒绝类保证零聚合变更与零成功去重）
            throw new IllegalArgumentException(
                    "仅 ALREADY_COMPLETED 可携带 businessCommandRef/aggregateRootRef/committedAt："
                            + outcomeCode + " 为未提交或不定态，禁携提交字段");
        }
        requireNonBlankOptional("resourceVersionAfter", resourceVersionAfter);
        if (retryDisposition == null) {
            throw new IllegalArgumentException("retryDisposition 必选");
        }
        requireNonBlank("traceId", traceId);
        if (responseProjection != null) {
            responseProjection = Map.copyOf(responseProjection);
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }

    private static void requireNonBlankOptional(String field, String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " 可选但提供时不得为空白");
        }
    }
}
