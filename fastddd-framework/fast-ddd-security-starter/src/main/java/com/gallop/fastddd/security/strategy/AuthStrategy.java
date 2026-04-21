package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.channel.AuthChannel;
import org.springframework.security.core.Authentication;

/**
 * 认证策略 SPI
 * 应用层实现此接口并标注 @Component 即可自动注册
 *
 * @author gallop
 * @date 2026-04-21
 */
public interface AuthStrategy {

    /**
     * 策略类型标识（如 "password", "sms", "wechat-mini"）
     */
    String getType();

    /**
     * 执行认证
     *
     * @param request 统一认证请求
     * @return 认证成功的 Authentication
     */
    Authentication authenticate(AuthenticationRequest request);

    /**
     * 是否支持该通道（默认支持所有通道）
     */
    default boolean supports(AuthChannel channel) {
        return true;
    }
}
