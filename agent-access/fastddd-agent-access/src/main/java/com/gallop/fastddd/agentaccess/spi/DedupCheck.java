package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;

/**
 * 持久去重检查结果（Contract §7 步骤 8 / §8.2-8.3 三分支，sealed——
 * 不引入第四分支）。
 *
 * <ul>
 *   <li>{@link Absent}：二元键无记录——首执行路径，事务内继续权威重读与终检；</li>
 *   <li>{@link Loaded}：同键同 {@code effectDigest}——返回既有稳定结果
 *       （{@link BusinessActionReceipt}），<b>不重复修改聚合</b>（§8.2）；</li>
 *   <li>{@link Conflict}：同键异 {@code effectDigest}——映射
 *       {@code IDEMPOTENCY_CONFLICT} 并产生完整性事件（§8.3）；携带既有
 *       effectDigest 供完整性事件与审计（不携带既有 Receipt——冲突路径
 *       不向调用方回放他人稳定结果）。</li>
 * </ul>
 */
public sealed interface DedupCheck {

    /** 二元键无记录：首执行。 */
    record Absent() implements DedupCheck {
    }

    /** 同键同摘要：既有稳定结果（不重复修改聚合）。 */
    record Loaded(BusinessActionReceipt receipt) implements DedupCheck {

        public Loaded {
            if (receipt == null) {
                throw new IllegalArgumentException("receipt 必选（Loaded 必须携带既有稳定结果）");
            }
        }
    }

    /** 同键异摘要：IDEMPOTENCY_CONFLICT + 完整性事件。 */
    record Conflict(String existingEffectDigest) implements DedupCheck {

        public Conflict {
            if (existingEffectDigest == null || existingEffectDigest.isBlank()) {
                throw new IllegalArgumentException("existingEffectDigest 必选且不得为空白");
            }
        }
    }
}
