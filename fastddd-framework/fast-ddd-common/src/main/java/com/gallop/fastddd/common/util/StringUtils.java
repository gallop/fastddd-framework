package com.gallop.fastddd.common.util;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 字符串工具类，提供常用的字符串操作方法
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public final class StringUtils {
    
    private StringUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 空字符串
     */
    public static final String EMPTY = "";
    
    /**
     * 空格字符串
     */
    public static final String SPACE = " ";
    
    /**
     * 换行符
     */
    public static final String LF = "\n";
    
    /**
     * 回车换行符
     */
    public static final String CRLF = "\r\n";
    
    /**
     * 判断字符串是否为空（null或空字符串）
     * 
     * @param str 字符串
     * @return 是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * 判断字符串是否不为空
     * 
     * @param str 字符串
     * @return 是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * 判断字符串是否为空白（null、空字符串或只包含空白字符）
     * 
     * @param str 字符串
     * @return 是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 判断字符串是否不为空白
     * 
     * @param str 字符串
     * @return 是否不为空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
    
    /**
     * 去除字符串两端的空白字符
     * 
     * @param str 字符串
     * @return 去除空白后的字符串
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }
    
    /**
     * 去除字符串两端的空白字符，如果为null则返回空字符串
     * 
     * @param str 字符串
     * @return 去除空白后的字符串
     */
    public static String trimToEmpty(String str) {
        return str == null ? EMPTY : str.trim();
    }
    
    /**
     * 去除字符串两端的空白字符，如果为空白则返回null
     * 
     * @param str 字符串
     * @return 去除空白后的字符串或null
     */
    public static String trimToNull(String str) {
        String trimmed = trim(str);
        return isEmpty(trimmed) ? null : trimmed;
    }
    
    /**
     * 如果字符串为null则返回默认值
     * 
     * @param str 字符串
     * @param defaultStr 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfNull(String str, String defaultStr) {
        return str == null ? defaultStr : str;
    }
    
    /**
     * 如果字符串为空则返回默认值
     * 
     * @param str 字符串
     * @param defaultStr 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }
    
    /**
     * 如果字符串为空白则返回默认值
     * 
     * @param str 字符串
     * @param defaultStr 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }
    
    /**
     * 连接字符串数组
     * 
     * @param separator 分隔符
     * @param elements 字符串数组
     * @return 连接后的字符串
     */
    public static String join(String separator, String... elements) {
        if (elements == null || elements.length == 0) {
            return EMPTY;
        }
        return String.join(separator == null ? EMPTY : separator, elements);
    }
    
    /**
     * 连接字符串集合
     * 
     * @param separator 分隔符
     * @param elements 字符串集合
     * @return 连接后的字符串
     */
    public static String join(String separator, Collection<String> elements) {
        if (elements == null || elements.isEmpty()) {
            return EMPTY;
        }
        return String.join(separator == null ? EMPTY : separator, elements);
    }
    
    /**
     * 判断字符串是否匹配正则表达式
     * 
     * @param str 字符串
     * @param regex 正则表达式
     * @return 是否匹配
     */
    public static boolean matches(String str, String regex) {
        return str != null && regex != null && Pattern.matches(regex, str);
    }
    
    /**
     * 首字母大写
     * 
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 首字母小写
     * 
     * @param str 字符串
     * @return 首字母小写的字符串
     */
    public static String uncapitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}