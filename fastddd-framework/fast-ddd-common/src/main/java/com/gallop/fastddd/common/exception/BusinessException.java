package com.gallop.fastddd.common.exception;

import lombok.Getter;

/**
 * 业务异常类，用于表示业务逻辑相关的异常
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final Integer errorCode;
    private final String errorMessage;
    private final String code;

    public BusinessException(Integer errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.code = null;
    }

    public BusinessException(Integer errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.code = null;
    }

    public BusinessException(String errorMessage) {
        super(errorMessage);
        this.errorCode = 400;
        this.errorMessage = errorMessage;
        this.code = null;
    }

    public BusinessException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = 400;
        this.errorMessage = errorMessage;
        this.code = null;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
        this.errorMessage = message;
    }

    @Override
    public String toString() {
        return String.format("BusinessException{code='%s', errorCode='%s', errorMessage='%s'}",
                code, errorCode, errorMessage);
    }
}