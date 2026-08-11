package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT Token提供者
 *
 * <p>签名和验签委托给 {@link JwtSigner} 策略接口，
 * 默认使用 HMAC-SHA512，业务层可替换为 RSA 等非对称实现。
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Slf4j
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";

    private final JwtProperties jwtProperties;
    private final JwtSigner jwtSigner;
    private final JwtTokenRevocationStore tokenRevocationStore;

    /**
     * 向后兼容构造函数。内部创建 HmacJwtSigner，
     * 已有代码和单元测试无需任何改动。
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this(jwtProperties, new HmacJwtSigner(jwtProperties.getSecret()), new InMemoryJwtTokenRevocationStore());
    }

    /**
     * 主构造函数，接受显式的 JwtSigner 策略。
     */
    public JwtTokenProvider(JwtProperties jwtProperties, JwtSigner jwtSigner) {
        this(jwtProperties, jwtSigner, new InMemoryJwtTokenRevocationStore());
    }

    public JwtTokenProvider(JwtProperties jwtProperties,
                            JwtSigner jwtSigner,
                            JwtTokenRevocationStore tokenRevocationStore) {
        this.jwtProperties = jwtProperties;
        this.jwtSigner = jwtSigner;
        this.tokenRevocationStore = tokenRevocationStore;
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
        return generateToken(userDetails, jwtProperties.getExpiration().toMillis(), channel, JwtTokenType.ACCESS);
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
        return generateToken(userDetails, jwtProperties.getRefreshExpiration().toMillis(), channel, JwtTokenType.REFRESH);
    }

    /**
     * 生成Token
     *
     * @param userDetails 用户详情
     * @param expiration  过期时间（毫秒）
     * @param channel     认证通道
     * @return JWT Token
     */
    private String generateToken(UserDetails userDetails,
                                 long expiration,
                                 AuthChannel channel,
                                 JwtTokenType tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        claims.put("channel", channel.getCode());
        claims.put(TOKEN_TYPE_CLAIM, tokenType.getClaimValue());
        if (userDetails instanceof SecurityUserDetails) {
            String userId = ((SecurityUserDetails) userDetails).getUserId();
            if (userId != null) {
                claims.put("userId", userId);
            }
        }

        JwtBuilder builder = Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(now)
                .setId(UUID.randomUUID().toString())
                .setExpiration(expiryDate);
        return jwtSigner.sign(builder);
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
     * 从Token中获取用户ID。
     * 旧 Token 无 userId 字段时返回 null。
     *
     * @param token JWT Token
     * @return 用户ID，旧 Token 返回 null
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object userId = claims.get("userId");
        if (userId == null) {
            return null;
        }
        return userId.toString();
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
        return validateToken(token, null);
    }

    /** Validates a token intended to authenticate API requests. */
    public boolean validateAccessToken(String token) {
        return validateToken(token, JwtTokenType.ACCESS);
    }

    /** Validates a token intended only for the refresh endpoint. */
    public boolean validateRefreshToken(String token) {
        return validateToken(token, JwtTokenType.REFRESH);
    }

    private boolean validateToken(String token, JwtTokenType expectedType) {
        try {
            Claims claims = getClaimsFromToken(token);
            JwtTokenType actualType = JwtTokenType.fromClaim(claims.get(TOKEN_TYPE_CLAIM));
            if (actualType == null || (expectedType != null && actualType != expectedType)) {
                return false;
            }
            return !tokenRevocationStore.isRevoked(claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Consumes a refresh token exactly once. This atomically revokes its jti
     * before issuing a rotated replacement.
     */
    public boolean consumeRefreshToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (JwtTokenType.fromClaim(claims.get(TOKEN_TYPE_CLAIM)) != JwtTokenType.REFRESH
                    || tokenRevocationStore.isRevoked(claims.getId())) {
                return false;
            }
            return tokenRevocationStore.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** Revokes a valid access or refresh token until its natural expiry. */
    public boolean revokeToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return JwtTokenType.fromClaim(claims.get(TOKEN_TYPE_CLAIM)) != null
                    && tokenRevocationStore.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
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
    protected Claims getClaimsFromToken(String token) {
        return jwtSigner.parse(token);
    }

    /**
     * 使用已配置的 JwtSigner 对 JWT Builder 执行签名。
     * 子类构建自定义 claims 时可通过此方法签名。
     *
     * @param builder 已设置 claims 但尚未签名的 JWT builder
     * @return 签名后的 compact JWT 字符串
     */
    protected String signToken(JwtBuilder builder) {
        return jwtSigner.sign(builder);
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
