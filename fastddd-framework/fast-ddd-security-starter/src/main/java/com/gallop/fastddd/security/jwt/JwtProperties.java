package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT配置属性
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Data
@ConfigurationProperties(prefix = "fastddd.security.jwt")
public class JwtProperties {

    /** HS512 requires at least 512 bits of key material. */
    public static final int MINIMUM_HMAC_SECRET_BYTES = 64;

    /**
     * JWT密钥
     */
    private String secret;

    /**
     * JWT过期时间
     */
    private Duration expiration = Duration.ofHours(24);

    /**
     * 刷新Token过期时间
     */
    private Duration refreshExpiration = Duration.ofDays(7);

    /**
     * Token前缀
     */
    private String tokenPrefix = "Bearer ";

    /**
     * Token请求头名称
     */
    private String headerName = "Authorization";

    /**
     * 是否启用JWT
     */
    private boolean enabled = true;

    /**
     * 当前应用的认证通道标识
     * 单端应用默认 DEFAULT，多端子应用配置为 ADMIN/CUSTOMER/MERCHANT
     */
    private AuthChannel channel = AuthChannel.DEFAULT;

    /**
     * 严格通道模式: true 时拒绝 DEFAULT 通道 Token 访问非 DEFAULT 应用
     * 建议迁移完成后启用
     */
    private boolean strictChannel = false;

    /**
     * JWT 签名算法标识（信息性质，框架逻辑不读取此值）。
     * 默认 "HS512"（HmacJwtSigner），使用 RSA 签名时设为 "RS256"。
     */
    private String algorithm = "HS512";

    /**
     * Validates the secret used by the default HMAC signer.
     *
     * <p>No framework-provided fallback is allowed: a predictable default
     * would let an attacker forge tokens for every application that forgot to
     * override it.</p>
     */
    public void validateHmacSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "fastddd.security.jwt.secret must be configured with a random secret; refusing to start with no JWT key");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_SECRET_BYTES) {
            throw new IllegalStateException(
                    "fastddd.security.jwt.secret must contain at least " + MINIMUM_HMAC_SECRET_BYTES
                            + " UTF-8 bytes for HS512");
        }
    }
}
