package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;

import java.time.OffsetDateTime;

/**
 * 宿主权威记录的当前可证明形态（{@link OutcomeStatusPort} 查询结果；
 * Contract §5.7 + AST-08）。
 *
 * <p><b>最特定可证明状态优先</b>：宿主按下列全序返回最特定形态——
 * 稳定 Receipt 存在即 {@link Recorded}（binding tombstone 不抹除已提交
 * 结果，判定仍按原 binding）；无 Receipt 且执行在途 {@link InFlight}；
 * 无 Receipt 无在途但原 binding 已 tombstone {@link BindingTombstoned}
 * （AST-08 审计输入）；皆无 {@link Absent}。</p>
 *
 * <p>{@link InFlight}/{@link Absent} 零负载：不携带执行细节，防差异信道。
 * {@link Recorded#receipt()} 与查询键的一致性由编排层逐项复验（AAC-16
 * 同构），视图自身不知查询键。</p>
 */
public sealed interface OutcomeRecordView {

    /** 宿主已记录稳定结果（成功与确定失败分类皆经 Receipt 承载）。 */
    record Recorded(BusinessActionReceipt receipt) implements OutcomeRecordView {

        public Recorded {
            if (receipt == null) {
                throw new IllegalArgumentException("receipt 必选（Recorded 必须携带稳定结果）");
            }
        }
    }

    /** 执行在途、尚无稳定结果（→ IN_PROGRESS）。 */
    record InFlight() implements OutcomeRecordView {
    }

    /**
     * 原 binding 已 tombstone（ExecutorRegistry 语义，AST-08）。
     *
     * @param tombstoneRef  tombstone 引用（审计记录要求）
     * @param reasonCode    tombstone 原因（小写点分，审计/调查用）
     * @param tombstonedAt  tombstone 生效时间
     */
    record BindingTombstoned(String tombstoneRef, String reasonCode, OffsetDateTime tombstonedAt)
            implements OutcomeRecordView {

        public BindingTombstoned {
            if (tombstoneRef == null || tombstoneRef.isBlank()) {
                throw new IllegalArgumentException("tombstoneRef 必选且不得为空白（AST-08：审计记录 tombstone 引用）");
            }
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("reasonCode 必选且不得为空白");
            }
            if (tombstonedAt == null) {
                throw new IllegalArgumentException("tombstonedAt 必选");
            }
        }
    }

    /** 无记录（→ UNKNOWN；§5.7 无记录≠FAILED）。 */
    record Absent() implements OutcomeRecordView {
    }
}
