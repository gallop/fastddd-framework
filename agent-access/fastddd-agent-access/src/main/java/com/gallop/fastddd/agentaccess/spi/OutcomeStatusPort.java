package com.gallop.fastddd.agentaccess.spi;

/**
 * 宿主权威记录查询 Port（Contract §5.7 Status 查询的记录侧；TM 同构
 * 「宿主对自有表提供实现」）。
 *
 * <p><b>租户作用域来自查询键本身</b>：{@link DedupKey} 的
 * {@code (tenantId, businessOperationId)} 由<b>已验证许可</b>推导，不由
 * 请求自报——跨租户记录在键层面即不可达（§9：不泄露其他 tenant 的资源
 * 存在性）。API 授权不依赖该二元键（§5.7），授权由
 * {@code ReconciliationPermitVerifier} 在记录读取<b>之前</b>完成。</p>
 *
 * <p>实现约束：宿主对自有 attempt/receipt/tombstone 表提供只读投影；
 * 返回值是「当前可证明形态」，不是重放源。无记录返回
 * {@link OutcomeRecordView.Absent}，不返回 null。</p>
 */
public interface OutcomeStatusPort {

    /**
     * 查询权威记录的当前可证明形态。
     *
     * @param key 已验证许可推导的租户作用域查询键
     * @return 最特定可证明形态（永不 null）
     * @throws IllegalArgumentException key 缺失
     */
    OutcomeRecordView find(DedupKey key);
}
