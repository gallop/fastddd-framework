package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 外部拒绝行域形态（v0.2 §8.2 / W5 设计 §3.3，W5-b 新增）：external_rejection
 * 表（V1 已交付 uk 五元组）的行载体。
 *
 * <p>活跃性 = {@link #active()}（cleared_at 为 null）。登录门禁
 * （ExternalRejectionGate.lockActive）与 readEffective 剔除（§8.1(c) 键映射，
 * 仅 open_id 域）均以活跃行为准。清除恰两支：受控解除（clearControlled）与
 * 权威恢复（clearByAuthoritativeRestoration）——两者置 cleared_at，操作者/理由
 * 入审计日志不入本表（V1 无对应列）。写入幂等由 uk 承载（重复 record 落回
 * 既有行）。列表重现不解除。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class RejectionRecord {

    private final ExternalIdentity identity;
    private final String reason;
    private final Instant rejectedAt;
    private final Instant clearedAt;

    public RejectionRecord(ExternalIdentity identity,
                           String reason,
                           Instant rejectedAt,
                           Instant clearedAt) {
        this.identity = Objects.requireNonNull(identity, "identity");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("RejectionRecord.reason 不能为空");
        }
        this.reason = reason;
        this.rejectedAt = Objects.requireNonNull(rejectedAt, "rejectedAt");
        this.clearedAt = clearedAt;
    }

    /** 拒绝键五元组（uk 列值域）。 */
    public ExternalIdentity identity() {
        return identity;
    }

    public String reason() {
        return reason;
    }

    public Instant rejectedAt() {
        return rejectedAt;
    }

    /** 清除时点（null = 活跃拒绝行）。 */
    public Instant clearedAt() {
        return clearedAt;
    }

    /** 活跃性判定：cleared_at 为 null 即活跃。 */
    public boolean active() {
        return clearedAt == null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RejectionRecord)) {
            return false;
        }
        RejectionRecord that = (RejectionRecord) o;
        return identity.equals(that.identity)
                && reason.equals(that.reason)
                && rejectedAt.equals(that.rejectedAt)
                && Objects.equals(clearedAt, that.clearedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, reason, rejectedAt, clearedAt);
    }

    @Override
    public String toString() {
        return "RejectionRecord[" + identity + ", active=" + active() + "]";
    }
}
