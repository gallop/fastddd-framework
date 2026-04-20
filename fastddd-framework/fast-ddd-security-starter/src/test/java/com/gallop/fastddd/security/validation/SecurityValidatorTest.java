package com.gallop.fastddd.security.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全验证工具类测试
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
class SecurityValidatorTest {

    @Test
    void testSqlInjectionDetection() {
        // 测试SQL注入攻击
        assertTrue(SecurityValidator.containsSqlInjection("SELECT * FROM users"));
        assertTrue(SecurityValidator.containsSqlInjection("' OR '1'='1"));
        assertTrue(SecurityValidator.containsSqlInjection("admin'--"));
        assertTrue(SecurityValidator.containsSqlInjection("1; DROP TABLE users"));
        assertTrue(SecurityValidator.containsSqlInjection("UNION SELECT password FROM users"));
        
        // 测试正常输入
        assertFalse(SecurityValidator.containsSqlInjection("normal input"));
        assertFalse(SecurityValidator.containsSqlInjection("user@example.com"));
        assertFalse(SecurityValidator.containsSqlInjection(""));
        assertFalse(SecurityValidator.containsSqlInjection(null));
    }

    @Test
    void testXssDetection() {
        // 测试XSS攻击
        assertTrue(SecurityValidator.containsXss("<script>alert('xss')</script>"));
        assertTrue(SecurityValidator.containsXss("<iframe src='javascript:alert(1)'></iframe>"));
        assertTrue(SecurityValidator.containsXss("javascript:alert('xss')"));
        assertTrue(SecurityValidator.containsXss("<img src='x' onerror='alert(1)'>"));
        assertTrue(SecurityValidator.containsXss("<div onclick='alert(1)'>click me</div>"));
        
        // 测试正常输入
        assertFalse(SecurityValidator.containsXss("normal text"));
        assertFalse(SecurityValidator.containsXss("<p>normal html</p>"));
        assertFalse(SecurityValidator.containsXss(""));
        assertFalse(SecurityValidator.containsXss(null));
    }

    @Test
    void testPathTraversalDetection() {
        // 测试路径遍历攻击
        assertTrue(SecurityValidator.containsPathTraversal("../../../etc/passwd"));
        assertTrue(SecurityValidator.containsPathTraversal("..\\..\\windows\\system32"));
        assertTrue(SecurityValidator.containsPathTraversal("/../../etc/shadow"));
        assertTrue(SecurityValidator.containsPathTraversal("\\..\\..\\config"));
        
        // 测试正常路径
        assertFalse(SecurityValidator.containsPathTraversal("/normal/path"));
        assertFalse(SecurityValidator.containsPathTraversal("file.txt"));
        assertFalse(SecurityValidator.containsPathTraversal(""));
        assertFalse(SecurityValidator.containsPathTraversal(null));
    }

    @Test
    void testXssCleaning() {
        // 测试XSS清理
        assertEquals("", SecurityValidator.cleanXss("<script>alert('xss')</script>"));
        assertEquals("Hello World", SecurityValidator.cleanXss("Hello World"));
        // The implementation escapes '/' as '&#x2F;'
        assertEquals("&lt;p&gt;safe html&lt;&#x2F;p&gt;", SecurityValidator.cleanXss("<p>safe html</p>"));
        assertEquals("", SecurityValidator.cleanXss("<iframe src='javascript:alert(1)'></iframe>"));

        // 测试特殊字符转义
        assertEquals("&lt;div&gt;content&lt;&#x2F;div&gt;", SecurityValidator.cleanXss("<div>content</div>"));
        assertEquals("&quot;quoted&quot;", SecurityValidator.cleanXss("\"quoted\""));
        assertEquals("&#x27;single&#x27;", SecurityValidator.cleanXss("'single'"));

        // 测试空值
        assertNull(SecurityValidator.cleanXss(null));
        assertEquals("", SecurityValidator.cleanXss(""));
    }

    @Test
    void testPasswordStrength() {
        // 测试强密码 - 确保包含所有要求的特殊字符
        assertTrue(SecurityValidator.isStrongPassword("StrongP@ss1"));  // 最小8位，包含所有要求
        assertTrue(SecurityValidator.isStrongPassword("MySecure$Pass1")); // 包含 $
        assertTrue(SecurityValidator.isStrongPassword("Complex!Pass9"));  // 包含 !
        assertTrue(SecurityValidator.isStrongPassword("Test*Pass1234")); // 包含 *

        // 测试弱密码
        assertFalse(SecurityValidator.isStrongPassword("password"));
        assertFalse(SecurityValidator.isStrongPassword("12345678"));
        assertFalse(SecurityValidator.isStrongPassword("PASSWORD"));
        assertFalse(SecurityValidator.isStrongPassword("Pass123")); // 缺少特殊字符
        assertFalse(SecurityValidator.isStrongPassword("Pass@")); // 太短

        // 测试空值
        assertFalse(SecurityValidator.isStrongPassword(null));
        assertFalse(SecurityValidator.isStrongPassword(""));
    }

    @Test
    void testEmailValidation() {
        // 测试有效邮箱
        assertTrue(SecurityValidator.isValidEmail("user@example.com"));
        assertTrue(SecurityValidator.isValidEmail("test.email@domain.org"));
        assertTrue(SecurityValidator.isValidEmail("user+tag@example.co.uk"));
        
        // 测试无效邮箱
        assertFalse(SecurityValidator.isValidEmail("invalid-email"));
        assertFalse(SecurityValidator.isValidEmail("@example.com"));
        assertFalse(SecurityValidator.isValidEmail("user@"));
        assertFalse(SecurityValidator.isValidEmail("user@.com"));
        
        // 测试空值
        assertFalse(SecurityValidator.isValidEmail(null));
        assertFalse(SecurityValidator.isValidEmail(""));
    }

    @Test
    void testPhoneValidation() {
        // 测试有效手机号
        assertTrue(SecurityValidator.isValidPhone("13812345678"));
        assertTrue(SecurityValidator.isValidPhone("15987654321"));
        assertTrue(SecurityValidator.isValidPhone("18612345678"));
        
        // 测试无效手机号
        assertFalse(SecurityValidator.isValidPhone("12812345678")); // 不是1开头的有效号段
        assertFalse(SecurityValidator.isValidPhone("1381234567")); // 位数不够
        assertFalse(SecurityValidator.isValidPhone("138123456789")); // 位数过多
        assertFalse(SecurityValidator.isValidPhone("abcdefghijk"));
        
        // 测试空值
        assertFalse(SecurityValidator.isValidPhone(null));
        assertFalse(SecurityValidator.isValidPhone(""));
    }
}