package com.gallop.fastddd.security.code;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 验证码认证配置。
 *
 * @author gallop
 */
@Data
@Validated
@ConfigurationProperties(prefix = "fastddd.security.code")
public class VerificationCodeProperties {

    /**
     * 是否启用框架内置验证码能力。
     */
    private boolean enabled = true;

    /**
     * 验证码长度。
     */
    @Min(value = 4, message = "验证码长度至少 4 位")
    @Max(value = 10, message = "验证码长度至多 10 位")
    private int length = 6;

    /**
     * 验证码有效期。
     */
    private Duration ttl = Duration.ofMinutes(5);

    /**
     * 同一验证码最大尝试次数。
     */
    @Min(value = 1, message = "同一验证码最大尝试次数至少 1")
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
