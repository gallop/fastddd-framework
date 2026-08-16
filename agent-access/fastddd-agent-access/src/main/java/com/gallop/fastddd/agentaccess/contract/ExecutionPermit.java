package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 一次受限执行许可（Contract §5.2 权威字段表）。
 *
 * <p>证明上游治理允许<b>本次尝试</b>，不替代 FastDDD 本地授权（双端 PEP）。
 * 验证必须 fail-closed：未知版本、必填缺失、时间无效、issuer/audience/sender
 * 不匹配、与 Envelope 同名字段不一致均拒绝（验证器属 {@code .spi}/
 * {@code .permit}，本类型只做结构紧致校验）。</p>
 *
 * <p>重放语义（§5.2 冻结）：WRITE/TASK 只允许同 {@code businessOperationId} +
 * 同 {@code effectDigest} 的受控重试；READ 按 usageLimit/时间窗/dataBudget/速率
 * 四约束执行；{@code permitId} 只是调查与消费记录的键，不因「存在唯一 ID」
 * 自动获得防重放能力。</p>
 *
 * <p>本 record 同时实现 {@link ExecutionPermitCarrier}：{@code caller()} 组件
 * 即接口方法（7.2 冻结的最小载体语义不被破坏）；本地受控签发入口是
 * {@code .permit.PermitIssuer}，wire 反序列化构造不等于本地签发或验证通过。</p>
 *
 * <p>组件清单（按 §5.2 表序）：必选 17 + 按 Descriptor 4（externalIdempotencyKey/
 * preconditionsHash/expectedResourceVersions/…）+ READ 按 Descriptor 2
 * （usageLimit/dataBudget）+ 可选 2（confirmation/policyDecisionRef）。</p>
 */
public record ExecutionPermit(
        String schemaVersion,
        String permitId,
        String issuer,
        String audience,
        CallerContext caller,
        String actionId,
        String actionVersion,
        Effect effect,
        ResourceConstraints resourceConstraints,
        String purpose,
        String businessOperationId,
        String authorizationDigest,
        String authorizationEvidenceRef,
        String effectDigest,
        String authorizationBindingDigest,
        OffsetDateTime notBefore,
        OffsetDateTime expiresAt,
        String externalIdempotencyKey,
        String preconditionsHash,
        Map<String, String> expectedResourceVersions,
        Long usageLimit,
        DataBudget dataBudget,
        String confirmation,
        String policyDecisionRef) implements ExecutionPermitCarrier {

    public ExecutionPermit {
        if (!AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion 不受支持：期望 " + AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION
                            + "，实际 " + schemaVersion);
        }
        requireNonBlank("permitId", permitId);
        requireNonBlank("issuer", issuer);
        requireNonBlank("audience", audience);
        requireNonNull("caller", caller);
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        requireNonNull("effect", effect);
        requireNonNull("resourceConstraints", resourceConstraints);
        requireNonBlank("purpose", purpose);
        requireNonBlank("businessOperationId", businessOperationId);
        requireNonBlank("authorizationDigest", authorizationDigest);
        requireNonBlank("authorizationEvidenceRef", authorizationEvidenceRef);
        requireNonBlank("effectDigest", effectDigest);
        requireNonBlank("authorizationBindingDigest", authorizationBindingDigest);
        requireNonNull("notBefore", notBefore);
        requireNonNull("expiresAt", expiresAt);
        if (!notBefore.isBefore(expiresAt)) {
            throw new IllegalArgumentException("notBefore 必须早于 expiresAt（结构校验；时刻有效性由验证器判定）");
        }
        requireNonBlankIfPresent("externalIdempotencyKey", externalIdempotencyKey);
        requireNonBlankIfPresent("preconditionsHash", preconditionsHash);
        requireNonBlankIfPresent("confirmation", confirmation);
        requireNonBlankIfPresent("policyDecisionRef", policyDecisionRef);
        if (usageLimit != null && usageLimit <= 0) {
            throw new IllegalArgumentException("usageLimit 必须为正数");
        }
        if (expectedResourceVersions != null) {
            if (expectedResourceVersions.isEmpty()) {
                throw new IllegalArgumentException("expectedResourceVersions 可选但提供时不得为空映射（CAS 声明必须有实际条目）");
            }
            expectedResourceVersions = Map.copyOf(expectedResourceVersions);
        }
    }

    private static void requireNonNull(String field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 必选");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }

    private static void requireNonBlankIfPresent(String field, String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " 可选但提供时不得为空白");
        }
    }
}
