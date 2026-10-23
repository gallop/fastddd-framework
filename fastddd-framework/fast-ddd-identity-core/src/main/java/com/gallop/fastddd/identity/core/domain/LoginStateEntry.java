package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次外部登录授权上下文（v0.2 §7.3 时序：state 签发→回调校验消费）：
 * state 一次性随机值 + 与本次授权绑定的 PKCE code_verifier + 浏览器绑定随机值 +
 * 登录成功后的站内回跳地址。
 *
 * <p>安全契约（W2-2）：state 未消费前 code_verifier 不得出现在任何日志/响应中；
 * {@link #toString} 因此只含 state 前缀指纹与过期时刻，不泄露 verifier/绑定值。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class LoginStateEntry {

    private final String state;
    private final String codeVerifier;
    private final String browserBinding;
    private final String postLoginRedirect;
    private final Instant expiresAt;

    public LoginStateEntry(String state, String codeVerifier, String browserBinding,
                           String postLoginRedirect, Instant expiresAt) {
        this.state = requireNonBlank(state, "state");
        this.codeVerifier = requireNonBlank(codeVerifier, "codeVerifier");
        this.browserBinding = requireNonBlank(browserBinding, "browserBinding");
        this.postLoginRedirect = postLoginRedirect;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LoginStateEntry." + name + " 不能为空");
        }
        return value;
    }

    public String state() {
        return state;
    }

    /** PKCE code_verifier（RFC 7636：43–128 字符，仅本条 state 对应，不入日志）。 */
    public String codeVerifier() {
        return codeVerifier;
    }

    /** 浏览器绑定随机值：授权发起时经 HttpOnly Cookie 下发，回调时必须回带并匹配。 */
    public String browserBinding() {
        return browserBinding;
    }

    /** 登录成功后的站内回跳地址（服务端白名单裁决结果，非客户端原始输入）。 */
    public String postLoginRedirect() {
        return postLoginRedirect;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LoginStateEntry that
                && state.equals(that.state) && codeVerifier.equals(that.codeVerifier)
                && browserBinding.equals(that.browserBinding)
                && Objects.equals(postLoginRedirect, that.postLoginRedirect)
                && expiresAt.equals(that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, codeVerifier, browserBinding, postLoginRedirect, expiresAt);
    }

    @Override
    public String toString() {
        return "LoginStateEntry{state=" + state.substring(0, Math.min(8, state.length())) + "…, expiresAt="
                + expiresAt + "}";
    }
}
