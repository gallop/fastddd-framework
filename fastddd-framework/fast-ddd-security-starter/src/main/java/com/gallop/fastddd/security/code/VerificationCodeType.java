package com.gallop.fastddd.security.code;

/**
 * 验证码通道类型。
 *
 * @author gallop
 */
public enum VerificationCodeType {

    SMS("sms"),
    EMAIL("email");

    private final String code;

    VerificationCodeType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static VerificationCodeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (VerificationCodeType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
