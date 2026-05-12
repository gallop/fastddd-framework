package com.gallop.fastddd.security.code;

/**
 * 验证码限流 SPI。
 *
 * @author gallop
 */
public interface VerificationCodeRateLimiter {

    void checkSendAllowed(VerificationCodeType type, VerificationCodePurpose purpose, String target);
}
