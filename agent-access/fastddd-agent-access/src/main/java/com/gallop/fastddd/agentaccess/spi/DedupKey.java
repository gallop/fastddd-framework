package com.gallop.fastddd.agentaccess.spi;

/**
 * 持久幂等二元键（Contract §8.1 冻结）：{@code (tenantId, businessOperationId)}
 * 是 WRITE/TASK 持久唯一约束的唯一键形。
 *
 * <p><b>TM-26（结构级表达）</b>：FastDDD {@code commandId} 仅作调查关联，
 * <b>不作业务幂等键</b>——本键类型与其上端口
 * {@link PersistentDedupPort} 均不携带 commandId（结构缺失是最强表达，
 * 非 javadoc 约定）。幂等判定只由本二元键 + {@code effectDigest}（§8.2/8.3
 * 的一致性比对字段）参与。</p>
 */
public record DedupKey(String tenantId, String businessOperationId) {

    public DedupKey {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 必选且不得为空白");
        }
        if (businessOperationId == null || businessOperationId.isBlank()) {
            throw new IllegalArgumentException("businessOperationId 必选且不得为空白");
        }
    }
}
