package com.gallop.fastddd.identity.core.error;

/**
 * 租约已过期（W3 设计 §6.1）：联合校验中 {@code lease_expires_at <= now}。
 *
 * <p><b>不静默延期</b>：过期后续租（renewLease）与发布（publish，即使无人抢占）
 * 均被拒——须重新 acquire（走接管，产生新 token）。墙钟仅用于本存活判定，
 * 不进入顺序语义。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryLeaseExpiredException extends RuntimeException {

    public DirectoryLeaseExpiredException(String message) {
        super(message);
    }
}
