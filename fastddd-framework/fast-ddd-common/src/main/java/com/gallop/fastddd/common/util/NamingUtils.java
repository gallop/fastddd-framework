package com.gallop.fastddd.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 命名转换工具类，提供数据库snake_case和Java camelCase之间的转换
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
public final class NamingUtils {
    
    private NamingUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 将snake_case格式的字符串转换为camelCase格式
     * 例如：user_name -> userName
     * 
     * @param str snake_case格式的字符串
     * @return camelCase格式的字符串，如果输入为null或空字符串则返回空字符串
     */
    public static String toCamelCase(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        String result = StrUtil.toCamelCase(str);
        // 确保首字母小写（camelCase格式）
        return StringUtils.uncapitalize(result);
    }
    
    /**
     * 将camelCase格式的字符串转换为snake_case格式
     * 例如：userName -> user_name
     * 
     * @param str camelCase格式的字符串
     * @return snake_case格式的字符串，如果输入为null或空字符串则返回空字符串
     */
    public static String toSnakeCase(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StrUtil.toUnderlineCase(str);
    }
    
    /**
     * 将PascalCase格式的字符串转换为camelCase格式
     * 例如：UserName -> userName
     * 
     * @param str PascalCase格式的字符串
     * @return camelCase格式的字符串，如果输入为null或空字符串则返回空字符串
     */
    public static String pascalToCamelCase(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.uncapitalize(str);
    }
    
    /**
     * 将camelCase格式的字符串转换为PascalCase格式
     * 例如：userName -> UserName
     * 
     * @param str camelCase格式的字符串
     * @return PascalCase格式的字符串，如果输入为null或空字符串则返回空字符串
     */
    public static String camelToPascalCase(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return StringUtils.capitalize(str);
    }
    
    /**
     * 将snake_case格式的字符串转换为PascalCase格式
     * 例如：user_name -> UserName
     * 
     * @param str snake_case格式的字符串
     * @return PascalCase格式的字符串，如果输入为null或空字符串则返回空字符串
     */
    public static String snakeToPascalCase(String str) {
        return camelToPascalCase(toCamelCase(str));
    }
}