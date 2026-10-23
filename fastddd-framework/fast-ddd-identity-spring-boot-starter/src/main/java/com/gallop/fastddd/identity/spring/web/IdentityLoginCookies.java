package com.gallop.fastddd.identity.spring.web;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * 浏览器登录辅助 Cookie 契约（W2-2）：
 *
 * <ul>
 *   <li><b>登录绑定 Cookie</b>（{@link #BINDING_COOKIE_NAME}）：授权发起时种下的
 *       HttpOnly 随机值，回调时必须回带并与已消费 state 的 browser_binding 匹配
 *       ——把"回调来自发起时的同一浏览器"落到 HTTP 事实（飞书→回调是顶层 GET
 *       跳转，SameSite=Lax 下浏览器会携带）。回调结束（成功/失败）即清除；</li>
 *   <li><b>会话 Cookie 清除</b>：登出与失败路径都必须以同名同路径同属性下发
 *       maxAge=0，避免残留旧值（与 {@code IdentitySessionCookies.issue} 签发面配对）。</li>
 * </ul>
 *
 * <p>属性与 {@code IdentitySessionCookies} 同口径：HttpOnly + Secure（可显式放宽）+
 * SameSite=Lax + Path=/；绑定 Cookie maxAge 取 state 有效期（过期后绑定也随之失效）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IdentityLoginCookies {

    /** 登录浏览器绑定 Cookie 名（固定；与 state 生命周期同步）。 */
    public static final String BINDING_COOKIE_NAME = "FASTDDD_LOGIN_STATE";

    private IdentityLoginCookies() {
    }

    /** 种绑定 Cookie（授权发起响应）。 */
    static ResponseCookie bindingIssue(String browserBinding, Duration maxAge, boolean secure) {
        return ResponseCookie.from(BINDING_COOKIE_NAME, browserBinding)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /** 清除绑定 Cookie（回调结束/登出；同名同路径同属性 maxAge=0）。 */
    static ResponseCookie bindingClear(boolean secure) {
        return ResponseCookie.from(BINDING_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
