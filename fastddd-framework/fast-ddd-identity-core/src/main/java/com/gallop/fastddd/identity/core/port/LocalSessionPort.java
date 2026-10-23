package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.IssuedSession;
import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.UnknownSessionFormatException;

import java.time.Duration;
import java.util.Optional;

/**
 * 本地会话端口（v0.2 §6/§7）：建立/查询/退出/撤销会话；会话记录携带签发时
 * authEpoch 快照与 session_format_version。
 *
 * <p>事务责任：MANDATORY——T2 建、T3 撤单、T4 撤全，均加入外层事务不各自提交。
 * 每请求读取权威状态：validateSession 以会话行 JOIN 主体行（未过期/未撤销 AND
 * 主体 ACTIVE AND authEpoch 相等）判定，M1 不引入认证缓存。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface LocalSessionPort {

    /** 在 T2 内签发会话：INSERT 携带 epoch 快照与格式版本。 */
    IssuedSession issueSession(VerifiedPrincipal principal, Duration ttl);

    /** 每请求权威复验：任一条件不满足返回 empty（停用/撤销/过期/epoch 不匹配）。 */
    Optional<VerifiedPrincipal> validateSession(SessionId sessionId);

    /** T3 撤销单会话（幂等）。 */
    void revokeSession(SessionId sessionId);

    /** T4 组成部分：撤销主体全部会话（与停用同事务）。 */
    int revokeAllSessions(String principalId);
}
