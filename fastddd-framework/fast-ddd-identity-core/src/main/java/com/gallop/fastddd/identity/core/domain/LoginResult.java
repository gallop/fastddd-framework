package com.gallop.fastddd.identity.core.domain;

/**
 * 本地登录结果（T2 提交后的对外产物）：主体 + 会话。
 *
 * @author gallop
 * @since 1.1.0
 */
public record LoginResult(VerifiedPrincipal principal, IssuedSession session) {
}
