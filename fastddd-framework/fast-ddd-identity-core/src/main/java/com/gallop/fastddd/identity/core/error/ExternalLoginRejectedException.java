package com.gallop.fastddd.identity.core.error;

/**
 * 外部登录提供者拒绝（W0-4 §3 错误分类）：用户状态/范围类拒绝不是重试类；
 * reasonCode 携带按端点分类的错误码。
 *
 * @author gallop
 * @since 1.1.0
 */
public class ExternalLoginRejectedException extends RuntimeException {

    private final String reasonCode;

    public ExternalLoginRejectedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
