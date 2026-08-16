package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 受治理 Agent 动作信封（Contract §5.5）：FastAgent Egress → FastDDD 网关的
 * 唯一 wire 承载——关闭 Adapter 丢字段（§5.5 规则 6：当前
 * {@code GatewayEgressCommand → FastDDDCommandEgress} 会丢弃 credential、
 * tenant/environment、resource、purpose、preconditions 与外部幂等语义，
 * 进入 Phase 1A 前必须以本契约升级，不能依赖 ThreadLocal/Map 隐藏通道）。
 *
 * <p><b>骨架期如实标注</b>：{@code payload} 以 String 承载不透明 JSON 文本
 * （结构化形态未冻结，细化随 Descriptor schema 演进）；
 * {@code attemptId}/{@code dispatchId} 保持可选承载——FastAgent V04-04
 * 真实载体（2026-08-16 交付，FDD-AA-13 收口消费）恒携两字段（其构造点
 * requireText），但 Envelope 契约不据此收紧，其他 caller 形态可缺省——
 * 它们与 {@code traceId} 一样是可变调度信息，
 * <b>不进入 {@code effectDigest}</b>（§5.5 规则 2）。</p>
 *
 * <p><b>修订（2026-08-16，FDD-AA-13 收口勘误）</b>：增列 {@code preconditionsHash}
 * 与 {@code expectedResourceVersions} 两个可选组件（§11.2 安全忽略型字段新增）。
 * 此前两项在 Envelope 无同名 wire 承载、投影层恒投 null——FastAgent V04-04
 * 真实载体（{@code GatewayEgressCommand}）交付后确证其<b>直携</b>两项且与
 * Permit 构造期交叉校验，故本修订选择直携对齐（无推导发明）：Envelope 侧
 * 提供状态与 Permit 逐项敏感比对（单方提供即拒，验证器既有语义不变）；
 * {@code preconditions} 结构化列表保持 AAC-06 事务内权威重读语义，与摘要
 * 承载并行不悖。</p>
 *
 * <p>关键规则（javadoc 承载，验证在网关编排层）：</p>
 * <ul>
 *   <li>{@code businessOperationId} 稳定必选；{@code attemptId}/{@code dispatchId}
 *       不能替代它（规则 1）；</li>
 *   <li>{@code permit} 与 {@code permitRef} 二选一恰好一个：同进程 LOCAL_OBJECT
 *       载体或跨进程许可引用；</li>
 *   <li>{@code payload} 不得包含原始 access token、完整 Prompt、完整 Approval、
 *       完整委托图、企业 Secret 或非必要证据（规则 4）——类型层无法强制，
 *       由上游治理与敏感数据扫描兜底；</li>
 *   <li>{@code authorizationEvidenceRef} 仅用于调查，缺少有效 Permit 时不能放行
 *       （规则 5）；</li>
 *   <li>Envelope 与 Permit 的同名治理字段（caller/effect/tenant/environment/
 *       action/version/resource/purpose/operation/idempotency/preconditions/
 *       authorization 与两个 digest）逐项比对失败即拒绝（§5.2 验证段）。</li>
 * </ul>
 *
 * @param schemaVersion             wire 协议版本（= "0.1"）
 * @param requestId                请求标识
 * @param sentAt                   发送时刻
 * @param tenantId                 权威租户作用域
 * @param environment              环境
 * @param actionId                 动作标识
 * @param actionVersion            动作契约版本
 * @param effect                   动作效果（WRITE/TASK/READ）
 * @param permit                   同进程许可载体（与 permitRef 恰一）
 * @param permitRef                跨进程许可引用（与 permit 恰一）
 * @param authorizationDigest      上游治理授权摘要
 * @param authorizationEvidenceRef 可选：调查用证据引用（非放行条件）
 * @param businessOperationId      稳定业务操作 ID（对账主键）
 * @param attemptId                可选：尝试标识（短期调度信息，不进 effectDigest）
 * @param dispatchId               可选：派发标识（短期调度信息，不进 effectDigest）
 * @param externalIdempotencyKey   可选：按 Descriptor 提供的外部幂等键
 * @param resourceRef              目标资源引用
 * @param preconditions            可选：资源前置条件（提供时非空）
 * @param preconditionsHash        可选：资源前置条件摘要（提供状态须与 Permit 一致——修订 2026-08-16）
 * @param expectedResourceVersions 可选：资源版本重检表（提供时非空 Map；提供状态须与 Permit 一致）
 * @param purpose                  用途声明
 * @param payload                  业务参数（骨架期 String JSON 文本）
 * @param payloadSchemaVersion     业务参数 schema 版本
 * @param effectDigest             稳定业务效果摘要
 * @param authorizationBindingDigest 授权绑定摘要
 * @param traceId                  可选：租户内辅助关联键
 * @param correlationId            可选：关联标识
 * @param causationId              可选：因果引用
 * @param originalBindingRef       可选：原 binding/version 引用
 */
public record BusinessActionEnvelope(
        String schemaVersion,
        String requestId,
        OffsetDateTime sentAt,
        String tenantId,
        String environment,
        String actionId,
        String actionVersion,
        Effect effect,
        ExecutionPermitCarrier permit,
        String permitRef,
        String authorizationDigest,
        String authorizationEvidenceRef,
        String businessOperationId,
        String attemptId,
        String dispatchId,
        String externalIdempotencyKey,
        String resourceRef,
        List<ResourcePrecondition> preconditions,
        String preconditionsHash,
        Map<String, String> expectedResourceVersions,
        String purpose,
        String payload,
        String payloadSchemaVersion,
        String effectDigest,
        String authorizationBindingDigest,
        String traceId,
        String correlationId,
        String causationId,
        String originalBindingRef) {

    public BusinessActionEnvelope {
        if (!AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion 不受支持：期望 " + AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION
                            + "，实际 " + schemaVersion);
        }
        requireNonBlank("requestId", requestId);
        if (sentAt == null) {
            throw new IllegalArgumentException("sentAt 必选");
        }
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("environment", environment);
        requireNonBlank("actionId", actionId);
        requireNonBlank("actionVersion", actionVersion);
        if (effect == null) {
            throw new IllegalArgumentException("effect 必选");
        }
        if ((permit == null) == (permitRef == null)) {
            throw new IllegalArgumentException("permit 与 permitRef 必须二选一恰好一个");
        }
        if (permitRef != null && permitRef.isBlank()) {
            throw new IllegalArgumentException("permitRef 提供时不得为空白");
        }
        requireNonBlank("authorizationDigest", authorizationDigest);
        requireNonBlankOptional("authorizationEvidenceRef", authorizationEvidenceRef);
        requireNonBlank("businessOperationId", businessOperationId);
        requireNonBlankOptional("attemptId", attemptId);
        requireNonBlankOptional("dispatchId", dispatchId);
        requireNonBlankOptional("externalIdempotencyKey", externalIdempotencyKey);
        requireNonBlank("resourceRef", resourceRef);
        if (preconditions != null && preconditions.isEmpty()) {
            throw new IllegalArgumentException("preconditions 提供时不得为空列表（无前置条件请传 null）");
        }
        if (preconditions != null) {
            for (ResourcePrecondition precondition : preconditions) {
                if (precondition == null) {
                    // 与本组 record 统一的紧致校验口径：显式 IAE 而非 List.copyOf 的 NPE
                    throw new IllegalArgumentException("preconditions 列表不得含 null 元素（无前置条件请传 null 列表本身）");
                }
            }
        }
        // 修订 2026-08-16 增列组件：可选、提供状态与 Permit 敏感比对（单方提供即拒，
        // 归验证器既有语义）；构造点只做本组统一紧致校验——null=缺省，空白/空 Map 是非法值
        requireNonBlankOptional("preconditionsHash", preconditionsHash);
        if (expectedResourceVersions != null && expectedResourceVersions.isEmpty()) {
            throw new IllegalArgumentException("expectedResourceVersions 提供时不得为空 Map（无版本重检请传 null）");
        }
        if (expectedResourceVersions != null) {
            for (Map.Entry<String, String> entry : expectedResourceVersions.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    // 与 preconditions 列表同口径：显式 IAE 而非 Map.copyOf 的 NPE
                    throw new IllegalArgumentException("expectedResourceVersions 键/值不能为 null");
                }
            }
        }
        requireNonBlank("purpose", purpose);
        requireNonBlank("payload", payload);
        requireNonBlank("payloadSchemaVersion", payloadSchemaVersion);
        requireNonBlank("effectDigest", effectDigest);
        requireNonBlank("authorizationBindingDigest", authorizationBindingDigest);
        requireNonBlankOptional("traceId", traceId);
        requireNonBlankOptional("correlationId", correlationId);
        requireNonBlankOptional("causationId", causationId);
        requireNonBlankOptional("originalBindingRef", originalBindingRef);
        preconditions = preconditions == null ? null : List.copyOf(preconditions);
        expectedResourceVersions = expectedResourceVersions == null ? null : Map.copyOf(expectedResourceVersions);
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
