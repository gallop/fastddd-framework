package com.gallop.fastddd.security.code;

/**
 * 验证码发送 SPI。业务项目接入真实短信或邮件服务时实现此接口即可。
 *
 * @author gallop
 */
public interface VerificationCodeSender {

    VerificationCodeType getType();

    void send(String target, String code, VerificationCodePurpose purpose);
}
