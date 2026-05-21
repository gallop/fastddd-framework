package com.gallop.fastddd.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;

/**
 * JWT 签名策略接口。
 * 封装签名密钥和算法细节，上层业务可提供 RSA/EC 等非对称实现，
 * 框架默认提供 HMAC-SHA512 实现。
 *
 * @author gallop
 */
public interface JwtSigner {

    /**
     * 对 JWT Builder 执行签名并返回 compact token。
     *
     * @param builder 已设置 claims 但尚未签名的 JWT builder
     * @return 签名后的 compact JWT 字符串
     */
    String sign(JwtBuilder builder);

    /**
     * 解析并验证 JWT token，返回其 Claims。
     *
     * @param token compact JWT 字符串
     * @return 验证后的 Claims
     * @throws JwtException token 无效、过期或签名不匹配时抛出
     */
    Claims parse(String token) throws JwtException;
}
