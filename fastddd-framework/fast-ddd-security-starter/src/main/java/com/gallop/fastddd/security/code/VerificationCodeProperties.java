package com.gallop.fastddd.security.code;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 验证码认证配置。
 *
 * @author gallop
 */
@Data
@ConfigurationProperties(prefix = "fastddd.security.code")
public class VerificationCodeProperties {

    /**
     * 是否启用框架内置验证码能力。
     */
    private boolean enabled = true;

    /**
     * 验证码长度。
     */
    private int length = 6;

    /**
     * 验证码有效期。
     */
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * 同一验证码最大尝试次数。
     */
    private int maxAttempts = 5;

    /**
     * 同一目标重复发送间隔。
     */
    private Duration resendInterval = Duration.ofSeconds(60);

    /**
     * 开发模式下允许使用 Noop sender 记录日志。
     */
    private boolean devMode = false;
}
