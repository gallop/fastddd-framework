package com.gallop.fastddd.identity.core.error;

/**
 * 主体公开 ID 违反统一协议（CW-02 / W0-2 §3.0）：超长、空白、控制字符等。
 *
 * @author gallop
 * @since 1.1.0
 */
public class InvalidPrincipalIdException extends RuntimeException {

    public InvalidPrincipalIdException(String message) {
        super(message);
    }
}
