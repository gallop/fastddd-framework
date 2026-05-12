package com.gallop.fastddd.security.dto;

import lombok.Data;

/**
 * 发送验证码请求。
 *
 * @author gallop
 */
@Data
public class SendVerificationCodeRequest {

    /**
     * 验证码通道类型：sms / email。
     */
    private String type;

    /**
     * 接收目标：手机号或邮箱。
     */
    private String target;

    /**
     * 用途：login / bind / reset_password，默认 login。
     */
    private String purpose;
}
