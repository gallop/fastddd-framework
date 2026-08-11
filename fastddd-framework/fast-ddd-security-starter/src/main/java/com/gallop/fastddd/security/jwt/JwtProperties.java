package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
     * SEC-002：明显的示例/占位符密钥片段（大小写不敏感）。
     *
     * <p>长度足够但仍是已知占位文案的 secret 同样视为不安全，触发 fail-fast。
     * 注意：本清单不得包含测试 harness 自标识文案（如 "please-not-use-in-production"），
     * 否则会破坏消费方测试契约。
     */
    private static final List<String> PLACEHOLDER_SECRET_FRAGMENTS = List.of(
            "your-secret", "your_secret", "your-secret-here",
            "change-me", "changeme", "changeit", "change-it",
            "placeholder", "replace-me", "replace_me",
            "example", "dummy", "sample-secret");

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
     *
     * <p>SEC-002：仅在 JWT 启用（{@code enabled=true}）时由 {@link HmacJwtSigner} 构造链触发。
     * 三类不安全取值均 fail-fast 并指明配置路径 {@code fastddd.security.jwt.secret}
     * （环境变量 {@code JWT_SECRET}）：缺失、长度不足、明显为示例/占位符值。</p>
     */
    public void validateHmacSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "fastddd.security.jwt.secret must be configured with a random secret "
                            + "(env JWT_SECRET); refusing to start with no JWT key");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_SECRET_BYTES) {
            throw new IllegalStateException(
                    "fastddd.security.jwt.secret must contain at least " + MINIMUM_HMAC_SECRET_BYTES
                            + " UTF-8 bytes for HS512 (env JWT_SECRET)");
        }
        String lower = secret.toLowerCase();
        for (String placeholder : PLACEHOLDER_SECRET_FRAGMENTS) {
            if (lower.contains(placeholder)) {
                throw new IllegalStateException(
                        "fastddd.security.jwt.secret looks like an example/placeholder value "
                                + "(contains '" + placeholder + "'); refusing to start with a non-secret "
                                + "JWT key. Provide a strong random secret via env JWT_SECRET.");
            }
        }
    }
}
