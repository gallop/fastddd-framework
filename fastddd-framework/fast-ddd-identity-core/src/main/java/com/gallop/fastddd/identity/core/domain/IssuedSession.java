package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;

/**
 * 已签发的本地会话（T2 产物）：携带签发时 authEpoch 快照与 session_format_version。
 *
 * @author gallop
 * @since 1.1.0
 */
public record IssuedSession(SessionId sessionId, String tenantId, PrincipalId principalId,
                            long authEpochAtIssue, int sessionFormatVersion,
                            Instant issuedAt, Instant expiresAt) {

    /** M1 唯一已知会话格式版本；未知版本的会话记录拒绝认证（§13.8/R6）。 */
    public static final int FORMAT_VERSION = 1;
}
