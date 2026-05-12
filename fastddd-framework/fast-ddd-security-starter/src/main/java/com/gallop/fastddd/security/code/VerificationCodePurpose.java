package com.gallop.fastddd.security.code;

/**
 * 验证码用途。
 *
 * @author gallop
 */
public enum VerificationCodePurpose {

    LOGIN("login"),
    BIND("bind"),
    RESET_PASSWORD("reset_password");

    private final String code;

    VerificationCodePurpose(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static VerificationCodePurpose fromCode(String code) {
        if (code == null || code.isBlank()) {
            return LOGIN;
        }
        for (VerificationCodePurpose purpose : values()) {
            if (purpose.code.equalsIgnoreCase(code) || purpose.name().equalsIgnoreCase(code)) {
                return purpose;
            }
        }
        return null;
    }
}
