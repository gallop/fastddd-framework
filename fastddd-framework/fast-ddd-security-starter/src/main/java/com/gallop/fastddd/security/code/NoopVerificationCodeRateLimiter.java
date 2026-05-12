package com.gallop.fastddd.security.code;

/**
 * 默认验证码限流器。仅保留扩展点，不额外限制。
 *
 * @author gallop
 */
public class NoopVerificationCodeRateLimiter implements VerificationCodeRateLimiter {

    @Override
    public void checkSendAllowed(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        // no-op
    }
}
