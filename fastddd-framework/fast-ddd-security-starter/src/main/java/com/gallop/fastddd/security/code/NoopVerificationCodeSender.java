package com.gallop.fastddd.security.code;

import lombok.extern.slf4j.Slf4j;

/**
 * 开发模式验证码发送器，仅打印日志。
 *
 * @author gallop
 */
@Slf4j
public class NoopVerificationCodeSender implements VerificationCodeSender {

    private final VerificationCodeType type;

    public NoopVerificationCodeSender(VerificationCodeType type) {
        this.type = type;
    }

    @Override
    public VerificationCodeType getType() {
        return type;
    }

    @Override
    public void send(String target, String code, VerificationCodePurpose purpose) {
        log.warn("FastDDD verification code dev sender: type={}, target={}, purpose={}, code={}",
                type.getCode(), target, purpose.getCode(), code);
    }
}
