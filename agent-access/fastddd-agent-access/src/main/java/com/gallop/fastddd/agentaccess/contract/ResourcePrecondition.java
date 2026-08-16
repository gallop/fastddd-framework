package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;

/**
 * 资源前置条件（Contract §5.4）：进入业务事务前声明的「被批准事实」校验要求。
 *
 * <p>语义边界（如实标注）：本类型只是<b>声明</b>——快照与 {@code requiredState}
 * 不能替代事务内的 tenant-scoped 权威重读；FastDDD 必须先按 tenant 定位资源，
 * 再以 {@code expectedVersion}/CAS 比较版本和状态（§5.4 尾段）。错误 tenant 的
 * 请求不得通过差异化响应泄露资源是否存在。</p>
 *
 * <p>{@code expectedVersion} 是 String 承载：契约允许版本、ETag 或等价 CAS 值
 * （按 Descriptor 的 {@code PreconditionMode} 决定是否提供）。提供状态敏感：
 * 与许可侧 {@code expectedResourceVersions} 的提供状态由验证器逐项比对
 * （单方缺失即拒绝，{@code DefaultExecutionPermitVerifier}）。</p>
 *
 * @param resourceType     业务资源类型（如 {@code ar.overdueInvoice}）
 * @param resourceId       tenant 内资源标识
 * @param expectedVersion  可选：版本/ETag/等价 CAS 值（按 Descriptor）
 * @param factSnapshotRef  可选：审批时事实快照引用（调查用途，不是授权替代）
 * @param factDigest       可选：被批准事实的规范化摘要
 * @param requiredState    可选：必要状态（如 {@code OVERDUE}）；不能替代事务重读
 * @param capturedAt       快照时间
 */
public record ResourcePrecondition(
        String resourceType,
        String resourceId,
        String expectedVersion,
        String factSnapshotRef,
        String factDigest,
        String requiredState,
        OffsetDateTime capturedAt) {

    public ResourcePrecondition {
        requireNonBlank("resourceType", resourceType);
        requireNonBlank("resourceId", resourceId);
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt 必选");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}
