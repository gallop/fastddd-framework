package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT Token提供者
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问Token
     *
     * @param authentication 认证信息
     * @return JWT Token
     */
    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken(authentication, AuthChannel.DEFAULT);
    }

    /**
     * 生成访问Token（含通道标识）
     *
     * @param authentication 认证信息
     * @param channel 认证通道
     * @return JWT Token
     */
    public String generateAccessToken(Authentication authentication, AuthChannel channel) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails, jwtProperties.getExpiration().toMillis(), channel);
    }

    /**
     * 生成刷新Token
     *
     * @param authentication 认证信息
     * @return 刷新Token
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken(authentication, AuthChannel.DEFAULT);
    }

    /**
     * 生成刷新Token（含通道标识）
     *
     * @param authentication 认证信息
     * @param channel 认证通道
     * @return 刷新Token
     */
    public String generateRefreshToken(Authentication authentication, AuthChannel channel) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails, jwtProperties.getRefreshExpiration().toMillis(), channel);
    }

    /**
     * 生成Token
     *
     * @param userDetails 用户详情
     * @param expiration  过期时间（毫秒）
     * @param channel     认证通道
     * @return JWT Token
     */
    private String generateToken(UserDetails userDetails, long expiration, AuthChannel channel) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        claims.put("channel", channel.getCode());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * 从Token中获取权限列表
     *
     * @param token JWT Token
     * @return 权限列表
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (java.util.List<String>) claims.get("authorities");
    }

    /**
     * 从Token中获取认证通道
     * 旧 Token 无 channel 字段时返回 DEFAULT
     *
     * @param token JWT Token
     * @return 认证通道
     */
    public AuthChannel getChannelFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object channelCode = claims.get("channel");
        return AuthChannel.fromCode(channelCode != null ? channelCode.toString() : null);
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查Token是否过期
     *
     * @param token JWT Token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 从Token中获取Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从请求头中提取Token
     *
     * @param authHeader 认证头
     * @return JWT Token
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(jwtProperties.getTokenPrefix())) {
            return authHeader.substring(jwtProperties.getTokenPrefix().length());
        }
        return null;
    }
}