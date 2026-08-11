package com.gallop.fastddd.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 默认 HMAC-SHA512 JWT 签名实现。
 * 行为与原 JwtTokenProvider 完全一致，确保向后兼容。
 * <p>
 * 使用 {@code signWith(key)} 让 JJWT 根据密钥类型自动推断算法，
 * 兼容 JJWT 0.11.x 和 0.12.x。
 *
 * @author gallop
 */
public class HmacJwtSigner implements JwtSigner {

    private final SecretKey secretKey;

    public HmacJwtSigner(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.validateHmacSecret();
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String sign(JwtBuilder builder) {
        return builder.signWith(secretKey).compact();
    }

    @Override
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
