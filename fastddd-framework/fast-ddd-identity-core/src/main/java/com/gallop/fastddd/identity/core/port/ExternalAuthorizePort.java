package com.gallop.fastddd.identity.core.port;

/**
 * 外部授权发起端口（W2-2）：构造"跳转到外部身份提供者授权页"的 URL。
 *
 * <p>与 {@link ExternalLoginProvider} 分离（provider 只负责回调后的服务端凭证验证）：
 * 授权发起是纯 URL 构造（无网络调用、无事务），由真实适配器实现；URL 必须携带
 * 调用方传入的 state 与 PKCE code_challenge（S256），redirect_uri 使用适配器自身
 * 登记的回调配置（与凭证验证一致，不由调用方传参——避免两处漂移）。
 * 测试桩仅存在于测试夹具。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface ExternalAuthorizePort {

    /**
     * 构造授权页 URL（302 目标）。
     *
     * @param state         一次性随机 state（服务端生成）
     * @param codeChallenge PKCE code_challenge（S256；对应 verifier 由服务端随 state 保存）
     */
    String authorizeUrl(String state, String codeChallenge);
}
