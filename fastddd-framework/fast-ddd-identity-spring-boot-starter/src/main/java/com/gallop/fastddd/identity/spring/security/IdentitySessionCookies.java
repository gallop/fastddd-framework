package com.gallop.fastddd.identity.spring.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * identity 会话 Cookie 签发契约（v0.2 §4.4"安全 Cookie"、§4.2 CSRF 行 SameSite=Lax）。
 *
 * <p>W2-1 无登录端点（浏览器回调 W2-2 交付），本类先把签发口径固定为代码契约：
 * <b>HttpOnly + Secure（可按环境显式放宽）+ SameSite=Lax + Path=/ + maxAge=会话 TTL</b>；
 * 过滤器读取的 cookie 名与此处签发同名（缺省 {@link #DEFAULT_COOKIE_NAME}）。
 * W2-2 回调必须经 {@link #issue} 签发（不得手写 {@code jakarta.servlet.http.Cookie}），
 * 漏设安全属性由 {@code IdentitySessionCookieTest} 契约锚定报警（复评 RV-03）。</p>
 *
 * @author gallop
 * @since 1.1.0（W2-1 修复轮）
 */
public final class IdentitySessionCookies {

    /** 与 {@code FastDddIdentityProperties.Login.Session.cookieName} 缺省一致（读写同名的锚点）。 */
    public static final String DEFAULT_COOKIE_NAME = "FASTDDD_SESSION";

    private IdentitySessionCookies() {
    }

    /**
     * 签发会话 Cookie（W2-2 回调在 T2 提交成功后调用；响应丢失不回滚数据库，见 §8.3 条 6）。
     *
     * @param cookieName cookie 名（来自 fastddd.identity.login.session.cookie-name）
     * @param sessionId  不透明会话 ID
     * @param maxAge     会话 TTL（与 local_session.expires_at 同源）
     * @param secure     Secure 属性（来自 cookie-secure，缺省 true）
     */
    public static ResponseCookie issue(String cookieName, String sessionId, Duration maxAge, boolean secure) {
        return ResponseCookie.from(cookieName, sessionId)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
