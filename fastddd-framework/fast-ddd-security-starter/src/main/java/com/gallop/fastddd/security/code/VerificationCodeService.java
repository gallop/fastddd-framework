package com.gallop.fastddd.security.code;

/**
 * 验证码服务。
 *
 * @author gallop
 */
public interface VerificationCodeService {

    void send(VerificationCodeType type, VerificationCodePurpose purpose, String target);

    void verifyAndConsume(VerificationCodeType type, VerificationCodePurpose purpose, String target, String code);
}
