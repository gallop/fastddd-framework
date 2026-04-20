package com.gallop.fastddd.security.validation;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 安全验证工具类
 * 提供常见的安全验证功能
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
public class SecurityValidator {

    /**
     * SQL注入检测正则表达式
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(select|insert|update|delete|drop|create|alter|exec|execute|union|script)\\b)|" +
            "(--|#|/\\*|\\*/|'|\")|" +
            "(\\bor\\b.*\\b=\\b)|" +
            "(\\band\\b.*\\b=\\b)|" +
            "(\\bxp_cmdshell\\b)|" +
            "(\\bsp_\\w+\\b)"
    );

    /**
     * XSS攻击检测正则表达式
     */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script[^>]*>.*?</script>)|" +
            "(<iframe[^>]*>.*?</iframe>)|" +
            "(<object[^>]*>.*?</object>)|" +
            "(<embed[^>]*>.*?</embed>)|" +
            "(javascript:)|" +
            "(vbscript:)|" +
            "(onload=)|" +
            "(onerror=)|" +
            "(onclick=)|" +
            "(onmouseover=)|" +
            "(<img[^>]*src[^>]*=)|" +
            "(<link[^>]*href[^>]*=)"
    );

    /**
     * 路径遍历攻击检测正则表达式
     */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(\\.\\./)|" +
            "(\\.\\\\)|" +
            "(\\\\\\.\\.)|" +
            "(/\\.\\./)|" +
            "(\\\\\\.\\.\\\\)"
    );

    /**
     * 检测SQL注入攻击
     *
     * @param input 输入字符串
     * @return 是否包含SQL注入
     */
    public static boolean containsSqlInjection(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 检测XSS攻击
     *
     * @param input 输入字符串
     * @return 是否包含XSS攻击
     */
    public static boolean containsXss(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        return XSS_PATTERN.matcher(input).find();
    }

    /**
     * 检测路径遍历攻击
     *
     * @param input 输入字符串
     * @return 是否包含路径遍历攻击
     */
    public static boolean containsPathTraversal(String input) {
        if (!StringUtils.hasText(input)) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(input).find();
    }

    /**
     * 清理XSS攻击字符
     *
     * @param input 输入字符串
     * @return 清理后的字符串
     */
    public static String cleanXss(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        
        String cleaned = input;
        
        // 移除脚本标签
        cleaned = cleaned.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "");
        cleaned = cleaned.replaceAll("(?i)<object[^>]*>.*?</object>", "");
        cleaned = cleaned.replaceAll("(?i)<embed[^>]*>.*?</embed>", "");
        
        // 移除事件处理器
        cleaned = cleaned.replaceAll("(?i)javascript:", "");
        cleaned = cleaned.replaceAll("(?i)vbscript:", "");
        cleaned = cleaned.replaceAll("(?i)onload=", "");
        cleaned = cleaned.replaceAll("(?i)onerror=", "");
        cleaned = cleaned.replaceAll("(?i)onclick=", "");
        cleaned = cleaned.replaceAll("(?i)onmouseover=", "");
        
        // 转义特殊字符
        cleaned = cleaned.replace("<", "&lt;");
        cleaned = cleaned.replace(">", "&gt;");
        cleaned = cleaned.replace("\"", "&quot;");
        cleaned = cleaned.replace("'", "&#x27;");
        cleaned = cleaned.replace("/", "&#x2F;");
        
        return cleaned;
    }

    /**
     * 验证密码强度
     *
     * @param password 密码
     * @return 是否符合强度要求
     */
    public static boolean isStrongPassword(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        
        // 至少8位，包含大小写字母、数字和特殊字符
        String pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        return Pattern.matches(pattern, password);
    }

    /**
     * 验证邮箱格式
     *
     * @param email 邮箱地址
     * @return 是否为有效邮箱
     */
    public static boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        
        String pattern = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$";
        return Pattern.matches(pattern, email);
    }

    /**
     * 验证手机号格式
     *
     * @param phone 手机号
     * @return 是否为有效手机号
     */
    public static boolean isValidPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return false;
        }
        
        String pattern = "^1[3-9]\\d{9}$";
        return Pattern.matches(pattern, phone);
    }
}