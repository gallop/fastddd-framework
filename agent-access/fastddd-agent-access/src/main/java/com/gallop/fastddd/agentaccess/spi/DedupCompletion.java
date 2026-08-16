package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;

/**
 * 去重稳定结果写入载荷（Contract §7 步骤 11：同事务「写稳定去重结果」）。
 *
 * <p>完整性绑定（AAC-16 同源）：{@code receipt} 的
 * {@code tenantId}/{@code businessOperationId}/{@code effectDigest} 必须
 * 与 {@code key}/{@code effectDigest} 逐项一致——去重记录与稳定结果不可
 * 错位登记。且 {@code receipt.outcomeCode} 必须为 {@code ALREADY_COMPLETED}：
 * §9 尾注「拒绝类结果必须保证没有新增聚合变更、<b>成功去重状态</b>或业务
 * Outbox」——拒绝/不定态不得写入成功去重记录。</p>
 */
public record DedupCompletion(DedupKey key, String effectDigest, BusinessActionReceipt receipt) {

    public DedupCompletion {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        if (effectDigest == null || effectDigest.isBlank()) {
            throw new IllegalArgumentException("effectDigest 必选且不得为空白");
        }
        if (receipt == null) {
            throw new IllegalArgumentException("receipt 必选");
        }
        if (!receipt.tenantId().equals(key.tenantId())
                || !receipt.businessOperationId().equals(key.businessOperationId())
                || !receipt.effectDigest().equals(effectDigest)) {
            throw new IllegalArgumentException(
                    "receipt 与 key/effectDigest 完整性绑定失败：去重记录与稳定结果不可错位登记（AAC-16）");
        }
        if (receipt.outcomeCode() != ActionOutcomeCode.ALREADY_COMPLETED) {
            throw new IllegalArgumentException(
                    "仅 ALREADY_COMPLETED 可写入成功去重记录：" + receipt.outcomeCode()
                            + "（拒绝类结果不得产生成功去重状态，§9 尾注）");
        }
    }
}
