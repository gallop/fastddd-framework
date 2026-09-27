package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 租约快照（W3 设计 §6.1）：{@code beginGeneration}/{@code renewLease} 返回的
 * 当前有效期视图。权威值在镜像存储的发布元数据行；本对象只是响应快照。
 *
 * <p>墙钟仅用于存活判定（expiresAt 比较），绝不进入任何顺序语义——顺序由
 * committed_seq 事实序号承担（§6.2）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class Lease {

    private final String leaseToken;
    private final Instant expiresAt;

    public Lease(String leaseToken, Instant expiresAt) {
        this.leaseToken = Objects.requireNonNull(leaseToken, "leaseToken");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** 与 {@link GenerationHandle#leaseToken()} 同源（续租不轮换 token）。 */
    public String leaseToken() {
        return leaseToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Lease
                && leaseToken.equals(((Lease) o).leaseToken)
                && expiresAt.equals(((Lease) o).expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaseToken, expiresAt);
    }
}
