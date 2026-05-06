package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    /**
     * JWT密钥
     */
    private String secret = "fastDDD-security-jwt-secret-key-2025-MUST-BE-CHANGED-IN-PRODUCTION-MINIMUM-64-BYTES!!";

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
}