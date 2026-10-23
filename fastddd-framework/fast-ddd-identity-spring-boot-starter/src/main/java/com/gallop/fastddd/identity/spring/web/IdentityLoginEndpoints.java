package com.gallop.fastddd.identity.spring.web;

/**
 * identity 浏览器登录端点路径（W2-2，v0.2 §4.3 C 链放行面）。
 *
 * <p>仅这两个 GET 路径在 C 链 permitAll（发起授权是无认证跳转；回调消费一次性
 * state + 浏览器绑定后自行完成信任裁决）；登出 POST 走已认证 + CSRF。
 * 其余路径维持 anyRequest().authenticated()。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IdentityLoginEndpoints {

    /** 发起授权：302 至外部授权页 + 种浏览器绑定 Cookie。 */
    public static final String AUTHORIZE_PATH = "/api/identity/login/authorize";

    /** 外部回调：消费 state + 验证绑定 → 外部凭证验证 → T2 → 会话 Cookie + 站内回跳。 */
    public static final String CALLBACK_PATH = "/api/identity/login/callback";

    /** 登出（已认证 + CSRF）：撤销服务端会话 + 清除同名同路径 Cookie。 */
    public static final String LOGOUT_PATH = "/api/identity/logout";

    private IdentityLoginEndpoints() {
    }
}
