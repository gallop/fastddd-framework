package com.gallop.fastddd.security.channel;

/**
 * 认证通道上下文（ThreadLocal）
 * 由 JwtAuthenticationFilter 在请求时设置，finally 块中清理
 *
 * @author gallop
 * @date 2026-04-21
 */
public class AuthChannelContext {

    private static final ThreadLocal<AuthChannel> CURRENT = new ThreadLocal<>();

    private AuthChannelContext() {
    }

    public static void set(AuthChannel channel) {
        CURRENT.set(channel);
    }

    public static AuthChannel get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
