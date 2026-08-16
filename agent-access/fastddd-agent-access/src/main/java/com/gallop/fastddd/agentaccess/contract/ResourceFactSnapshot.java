package com.gallop.fastddd.agentaccess.contract;

/**
 * 审批时资源事实快照（Contract §13 首样板字段形态，AA-02 冻结）。
 *
 * <p>用途边界（如实标注）：快照<b>仅作授权输入</b>——业务事务内必须权威重读资源
 * 并以 {@link ResourcePrecondition#expectedVersion()}/CAS 复验（§5.4/§13）。
 * 本类型只冻结字段形态与字面值域（resourceType、status、dataClassification
 * 的枚举值形态）；实际 resourceId/version/金额由运行时事实决定，不得硬编码
 * 样例值。金额以 {@code Long} 最小承载——货币/精度形态未冻结，细化随
 * Descriptor schema 演进。</p>
 *
 * @param resourceType      资源类型字面值（样板 {@code ar.overdueInvoice}）
 * @param resourceId        tenant 内资源标识（样板 {@code INV-2026-0001}）
 * @param version           整数乐观锁版本（进 {@code expectedVersion}）
 * @param status            资源状态（样板 {@code OVERDUE}）
 * @param tenantId          归属租户（数据权限终检输入）
 * @param ownerCustomerRef  归属客户引用（样板 {@code CUS-10423}）
 * @param dataClassification 数据分类（样板 {@code CUSTOMER_FINANCIAL}；驱动
 *                           DataHandlingPolicy 投影）
 * @param amount            金额类敏感值（样例 {@code 100}）
 */
public record ResourceFactSnapshot(
        String resourceType,
        String resourceId,
        Long version,
        String status,
        String tenantId,
        String ownerCustomerRef,
        String dataClassification,
        Long amount) {

    public ResourceFactSnapshot {
        requireNonBlank("resourceType", resourceType);
        requireNonBlank("resourceId", resourceId);
        if (version == null) {
            throw new IllegalArgumentException("version 必选");
        }
        requireNonBlank("status", status);
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("ownerCustomerRef", ownerCustomerRef);
        requireNonBlank("dataClassification", dataClassification);
        if (amount == null) {
            throw new IllegalArgumentException("amount 必选");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}
