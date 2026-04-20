package com.gallop.fastddd.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 统一API响应包装类
 * 提供标准化的JSON响应格式，包含响应码、消息、数据和时间戳
 * 
 * @param <T> 响应数据类型
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 响应时间戳（毫秒）
     */
    private Long timestamp;
    
    /**
     * 全参数构造函数
     */
    public ApiResponse(Integer code, String message, T data, Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }
    
    /**
     * 创建成功响应（无数据）
     * 
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }
    
    /**
     * 创建成功响应（带数据）
     * 
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "操作成功");
    }
    
    /**
     * 创建成功响应（带数据和消息）
     * 
     * @param data 响应数据
     * @param message 响应消息
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<T>(200, message, data, System.currentTimeMillis());
    }
    
    /**
     * 创建错误响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<T>(code, message, null, System.currentTimeMillis());
    }
    
    /**
     * 创建错误响应（默认500错误码）
     * 
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>error(500, message);
    }
    
    /**
     * 创建错误响应（带数据）
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(Integer code, String message, T data) {
        return new ApiResponse<T>(code, message, data, System.currentTimeMillis());
    }
    
    /**
     * 判断是否成功
     * 
     * @return 是否成功（响应码为200）
     */
    public boolean isSuccess() {
        return Integer.valueOf(200).equals(code);
    }
    
    /**
     * 判断是否失败
     * 
     * @return 是否失败
     */
    public boolean isFailure() {
        return !isSuccess();
    }
    
    /**
     * 创建失败响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(Integer code, String message) {
        return error(code, message);
    }
    
    /**
     * 创建失败响应（默认500错误码）
     * 
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(String message) {
        return error(500, message);
    }
    
    /**
     * 创建失败响应（带数据）
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ApiResponse<T> failure(Integer code, String message, T data) {
        return error(code, message, data);
    }
}