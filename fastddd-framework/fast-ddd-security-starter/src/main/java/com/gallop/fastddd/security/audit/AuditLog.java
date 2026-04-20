package com.gallop.fastddd.security.audit;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 用于标记需要记录审计日志的方法
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作描述
     */
    String value() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;

    /**
     * 是否记录返回结果
     */
    boolean logResult() default false;

    /**
     * 是否异步记录
     */
    boolean async() default true;

    /**
     * 操作类型枚举
     */
    enum OperationType {
        /**
         * 查询操作
         */
        SELECT,
        
        /**
         * 新增操作
         */
        INSERT,
        
        /**
         * 更新操作
         */
        UPDATE,
        
        /**
         * 删除操作
         */
        DELETE,
        
        /**
         * 登录操作
         */
        LOGIN,
        
        /**
         * 登出操作
         */
        LOGOUT,
        
        /**
         * 其他操作
         */
        OTHER
    }
}