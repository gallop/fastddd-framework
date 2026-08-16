package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.Effect;

import java.util.Map;

/**
 * Envelope 治理字段的许可绑定投影（Contract §5.5 安全绑定组：
 * 「Envelope 与 Permit 同名字段逐项比对」）。
 *
 * <p>完整 {@code BusinessActionEnvelope} wire DTO 归 FDD-AA-12 网关编排落地；
 * 验证器只需要其中与许可同名/绑定的治理字段。本投影由 Envelope 落地时
 * 适配产生，验证器对投影做逐项 fail-closed 比对：</p>
 * <ul>
 *   <li>必选同名项（tenant/environment/action/version/effect/purpose/
 *       businessOperationId/三摘要）：投影缺失即拒绝——无法证明一致等于不一致；</li>
 *   <li>按 Descriptor 项（externalIdempotencyKey/preconditionsHash/
 *       expectedResourceVersions）：双方提供状态敏感——一方提供一方缺失即拒绝。</li>
 * </ul>
 *
 * @param tenantId               租户作用域（必选同名项）
 * @param environment            隔离环境（必选同名项）
 * @param actionId               动作标识（必选同名项）
 * @param actionVersion          动作契约版本（必选同名项）
 * @param effect                 效果类型（必选同名项）
 * @param purpose                用途（必选同名项）
 * @param businessOperationId    稳定操作 ID（必选同名项）
 * @param externalIdempotencyKey 目标系统幂等键（按 Descriptor）
 * @param preconditionsHash      前置条件摘要（按 Descriptor）
 * @param expectedResourceVersions 资源版本/ETag（按 Descriptor）
 * @param effectDigest           业务效果摘要（必选同名项）
 * @param authorizationDigest    上游授权摘要（必选同名项）
 * @param authorizationBindingDigest 绑定摘要（必选同名项）
 */
public record PermitBindingView(
        String tenantId,
        String environment,
        String actionId,
        String actionVersion,
        Effect effect,
        String purpose,
        String businessOperationId,
        String externalIdempotencyKey,
        String preconditionsHash,
        Map<String, String> expectedResourceVersions,
        String effectDigest,
        String authorizationDigest,
        String authorizationBindingDigest) {
}
