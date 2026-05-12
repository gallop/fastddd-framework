package com.gallop.fastddd.security.code;

import java.util.Optional;

/**
 * 验证码存储 SPI。
 *
 * @author gallop
 */
public interface VerificationCodeStore {

    Optional<VerificationCodeEntry> get(VerificationCodeType type, VerificationCodePurpose purpose, String target);

    void put(VerificationCodeType type, VerificationCodePurpose purpose, String target, VerificationCodeEntry entry);

    void remove(VerificationCodeType type, VerificationCodePurpose purpose, String target);
}
