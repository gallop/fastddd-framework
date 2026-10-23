package com.gallop.fastddd.identity.core.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * 目录事实回收保留策略（W5 设计 §8.3 M-26；W5-f 设计 §3.2，W5-f f-1 交付）。
 *
 * <p>两键均由 §10 键族注入（缺省 {@code inbox-processed=7d} / {@code change-log}
 * = 不限期）：{@code inboxProcessedTtl} 约束回收面②（Inbox PROCESSED 行到期清理）；
 * {@code changeLogRetention} 约束回收面③（变更日志清理）——<b>null = 不限期 =
 * 面③恒零清理</b>（已发历史事件 changeLogRef 无限追溯的缺省安全形态；维护者
 * 收窄前须确认宿主投递延迟上界，W5-f 设计 §10 交付说明义务）。</p>
 *
 * <p>面①（终结非活动代镜像回收）不受本策略约束——可回收判据纯由代状态 ∧
 * 非 active 谓词承载（C-1 单语句自含联读形态），一经执行即回收。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public record RetentionPolicy(Duration inboxProcessedTtl, Duration changeLogRetention) {

    public RetentionPolicy {
        Objects.requireNonNull(inboxProcessedTtl, "inboxProcessedTtl");
        if (inboxProcessedTtl.isZero() || inboxProcessedTtl.isNegative()) {
            throw new IllegalArgumentException("inboxProcessedTtl 必须为正时长：" + inboxProcessedTtl);
        }
        if (changeLogRetention != null && (changeLogRetention.isZero() || changeLogRetention.isNegative())) {
            throw new IllegalArgumentException(
                    "changeLogRetention 为 null（不限期）或正时长，不接受：" + changeLogRetention);
        }
    }

    /** 面③是否不限期（null = 恒零清理）。 */
    public boolean isChangeLogRetentionUnlimited() {
        return changeLogRetention == null;
    }
}
