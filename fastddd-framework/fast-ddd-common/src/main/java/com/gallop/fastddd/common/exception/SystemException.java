package com.gallop.fastddd.common.exception;

import lombok.Getter;

/**
 * 系统异常类，用于表示系统级别的异常
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Getter
public class SystemException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private final Integer errorCode;
    
    /**
     * 错误消息
     */
    private final String errorMessage;
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    public SystemException(Integer errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param cause 原因异常
     */
    public SystemException(Integer errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 构造函数（使用默认错误码）
     * 
     * @param errorMessage 错误消息
     */
    public SystemException(String errorMessage) {
        this(500, errorMessage);
    }
    
    /**
     * 构造函数（使用默认错误码）
     * 
     * @param errorMessage 错误消息
     * @param cause 原因异常
     */
    public SystemException(String errorMessage, Throwable cause) {
        this(500, errorMessage, cause);
    }
    
    @Override
    public String toString() {
        return String.format("SystemException{errorCode='%s', errorMessage='%s'}", 
                errorCode, errorMessage);
    }
}