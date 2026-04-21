package com.gallop.fastddd.security.channel;

/**
 * 认证通道枚举
 * 单端应用使用 DEFAULT，多端子应用各自使用对应通道
 *
 * @author gallop
 * @date 2026-04-21
 */
public enum AuthChannel {

    DEFAULT("default", "默认通道"),
    ADMIN("admin", "后台管理"),
    CUSTOMER("customer", "C端用户"),
    MERCHANT("merchant", "商户端");

    private final String code;
    private final String description;

    AuthChannel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 查找枚举值，未匹配时返回 DEFAULT
     */
    public static AuthChannel fromCode(String code) {
        if (code == null) {
            return DEFAULT;
        }
        for (AuthChannel channel : values()) {
            if (channel.code.equals(code)) {
                return channel;
            }
        }
        return DEFAULT;
    }
}
