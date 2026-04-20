package com.gallop.fastddd.security.jwt;

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
@ConfigurationProperties(prefix = "fast-ddd.security.jwt")
public class JwtProperties {

    /**
     * JWT密钥
     */
    private String secret = "fastDDD-security-jwt-secret-key-2025";

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
}