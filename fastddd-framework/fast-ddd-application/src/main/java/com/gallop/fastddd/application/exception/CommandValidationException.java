package com.gallop.fastddd.application.exception;

import com.gallop.fastddd.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command 验证异常
 *
 * 当 Command 验证失败时抛出此异常。
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
public class CommandValidationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 验证错误列表
     */
    private final List<ValidationError> errors;

    public CommandValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
    }

    public CommandValidationException(Integer errorCode, String message) {
        super(errorCode, message);
        this.errors = new ArrayList<>();
    }

    public CommandValidationException(List<ValidationError> errors) {
        super("Command validation failed with " + errors.size() + " errors");
        this.errors = new ArrayList<>(errors);
    }

    public CommandValidationException(Integer errorCode, List<ValidationError> errors) {
        super(errorCode, "Command validation failed with " + errors.size() + " errors");
        this.errors = new ArrayList<>(errors);
    }

    /**
     * 获取验证错误列表
     *
     * @return 错误列表（不可修改）
     */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * 添加验证错误
     *
     * @param field   字段名
     * @param message 错误消息
     */
    public void addError(String field, String message) {
        this.errors.add(new ValidationError(field, message));
    }

    /**
     * 验证错误
     */
    public static class ValidationError {
        private final String field;
        private final String message;

        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return field + ": " + message;
        }
    }
}
