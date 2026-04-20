package com.gallop.fastddd.common.util;

import com.gallop.fastddd.common.exception.BusinessException;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 断言工具类，提供参数验证和业务逻辑断言的便捷方法
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public final class Assert {
    
    private Assert() {
        // 工具类不允许实例化
    }
    
    /**
     * 断言对象不为null
     * 
     * @param object 对象
     * @param message 错误消息
     * @throws BusinessException 如果对象为null
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言对象不为null
     * 
     * @param object 对象
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果对象为null
     */
    public static void notNull(Object object, Supplier<String> messageSupplier) {
        if (object == null) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言字符串不为空
     * 
     * @param text 字符串
     * @param message 错误消息
     * @throws BusinessException 如果字符串为空
     */
    public static void hasText(String text, String message) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言字符串不为空
     * 
     * @param text 字符串
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果字符串为空
     */
    public static void hasText(String text, Supplier<String> messageSupplier) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言集合不为空
     * 
     * @param collection 集合
     * @param message 错误消息
     * @throws BusinessException 如果集合为空
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言集合不为空
     * 
     * @param collection 集合
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果集合为空
     */
    public static void notEmpty(Collection<?> collection, Supplier<String> messageSupplier) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言Map不为空
     * 
     * @param map Map
     * @param message 错误消息
     * @throws BusinessException 如果Map为空
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言Map不为空
     * 
     * @param map Map
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果Map为空
     */
    public static void notEmpty(Map<?, ?> map, Supplier<String> messageSupplier) {
        if (CollectionUtils.isEmpty(map)) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言数组不为空
     * 
     * @param array 数组
     * @param message 错误消息
     * @throws BusinessException 如果数组为空
     */
    public static void notEmpty(Object[] array, String message) {
        if (array == null || array.length == 0) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言数组不为空
     * 
     * @param array 数组
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果数组为空
     */
    public static void notEmpty(Object[] array, Supplier<String> messageSupplier) {
        if (array == null || array.length == 0) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言表达式为true
     * 
     * @param expression 表达式
     * @param message 错误消息
     * @throws BusinessException 如果表达式为false
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言表达式为true
     * 
     * @param expression 表达式
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果表达式为false
     */
    public static void isTrue(boolean expression, Supplier<String> messageSupplier) {
        if (!expression) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言表达式为false
     * 
     * @param expression 表达式
     * @param message 错误消息
     * @throws BusinessException 如果表达式为true
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言表达式为false
     * 
     * @param expression 表达式
     * @param messageSupplier 错误消息提供者
     * @throws BusinessException 如果表达式为true
     */
    public static void isFalse(boolean expression, Supplier<String> messageSupplier) {
        if (expression) {
            throw new BusinessException(messageSupplier.get());
        }
    }
    
    /**
     * 断言数字为正数
     * 
     * @param number 数字
     * @param message 错误消息
     * @throws BusinessException 如果数字不为正数
     */
    public static void isPositive(Number number, String message) {
        notNull(number, message);
        if (number.doubleValue() <= 0) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言数字为非负数
     * 
     * @param number 数字
     * @param message 错误消息
     * @throws BusinessException 如果数字为负数
     */
    public static void isNotNegative(Number number, String message) {
        notNull(number, message);
        if (number.doubleValue() < 0) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言两个对象相等
     * 
     * @param expected 期望值
     * @param actual 实际值
     * @param message 错误消息
     * @throws BusinessException 如果两个对象不相等
     */
    public static void equals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言两个对象不相等
     * 
     * @param unexpected 不期望的值
     * @param actual 实际值
     * @param message 错误消息
     * @throws BusinessException 如果两个对象相等
     */
    public static void notEquals(Object unexpected, Object actual, String message) {
        if (java.util.Objects.equals(unexpected, actual)) {
            throw new BusinessException(message);
        }
    }
}