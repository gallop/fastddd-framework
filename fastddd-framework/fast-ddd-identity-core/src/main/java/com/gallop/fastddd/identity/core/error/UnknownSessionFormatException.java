package com.gallop.fastddd.identity.core.error;

/**
 * 未知会话格式版本（R6/§13.8）：session_format_version 不在支持矩阵内的会话记录拒绝认证。
 *
 * @author gallop
 * @since 1.1.0
 */
public class UnknownSessionFormatException extends RuntimeException {

    public UnknownSessionFormatException(String message) {
        super(message);
    }
}
