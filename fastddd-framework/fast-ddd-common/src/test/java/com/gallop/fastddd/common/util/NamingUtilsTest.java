package com.gallop.fastddd.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NamingUtils测试类
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
class NamingUtilsTest {
    
    @Test
    void testToCamelCase() {
        // 测试snake_case转camelCase
        assertEquals("userName", NamingUtils.toCamelCase("user_name"));
        assertEquals("userId", NamingUtils.toCamelCase("user_id"));
        assertEquals("createTime", NamingUtils.toCamelCase("create_time"));
        assertEquals("userAccountInfo", NamingUtils.toCamelCase("user_account_info"));
        
        // 测试边界情况
        assertEquals("", NamingUtils.toCamelCase(""));
        assertEquals("", NamingUtils.toCamelCase(null));
        assertEquals("user", NamingUtils.toCamelCase("user"));
        assertEquals("user", NamingUtils.toCamelCase("_user"));
        assertEquals("user", NamingUtils.toCamelCase("user_"));
        assertEquals("user", NamingUtils.toCamelCase("__user__"));
    }
    
    @Test
    void testToSnakeCase() {
        // 测试camelCase转snake_case
        assertEquals("user_name", NamingUtils.toSnakeCase("userName"));
        assertEquals("user_id", NamingUtils.toSnakeCase("userId"));
        assertEquals("create_time", NamingUtils.toSnakeCase("createTime"));
        assertEquals("user_account_info", NamingUtils.toSnakeCase("userAccountInfo"));
        
        // 测试PascalCase转snake_case
        assertEquals("user_name", NamingUtils.toSnakeCase("UserName"));
        assertEquals("user_service", NamingUtils.toSnakeCase("UserService"));
        
        // 测试边界情况
        assertEquals("", NamingUtils.toSnakeCase(""));
        assertEquals("", NamingUtils.toSnakeCase(null));
        assertEquals("user", NamingUtils.toSnakeCase("user"));
        assertEquals("user", NamingUtils.toSnakeCase("User"));
        
        // 测试连续大写字母
        assertEquals("HTTP_url", NamingUtils.toSnakeCase("HTTPUrl"));
        assertEquals("XML_parser", NamingUtils.toSnakeCase("XMLParser"));
    }
    
    @Test
    void testPascalToCamelCase() {
        // 测试PascalCase转camelCase
        assertEquals("userName", NamingUtils.pascalToCamelCase("UserName"));
        assertEquals("userService", NamingUtils.pascalToCamelCase("UserService"));
        assertEquals("httpClient", NamingUtils.pascalToCamelCase("HttpClient"));
        
        // 测试边界情况
        assertEquals("", NamingUtils.pascalToCamelCase(""));
        assertEquals("", NamingUtils.pascalToCamelCase(null));
        assertEquals("user", NamingUtils.pascalToCamelCase("User"));
        assertEquals("userName", NamingUtils.pascalToCamelCase("userName")); // 已经是camelCase
    }
    
    @Test
    void testCamelToPascalCase() {
        // 测试camelCase转PascalCase
        assertEquals("UserName", NamingUtils.camelToPascalCase("userName"));
        assertEquals("UserService", NamingUtils.camelToPascalCase("userService"));
        assertEquals("HttpClient", NamingUtils.camelToPascalCase("httpClient"));
        
        // 测试边界情况
        assertEquals("", NamingUtils.camelToPascalCase(""));
        assertEquals("", NamingUtils.camelToPascalCase(null));
        assertEquals("User", NamingUtils.camelToPascalCase("user"));
        assertEquals("UserName", NamingUtils.camelToPascalCase("UserName")); // 已经是PascalCase
    }
    
    @Test
    void testSnakeToPascalCase() {
        // 测试snake_case转PascalCase
        assertEquals("UserName", NamingUtils.snakeToPascalCase("user_name"));
        assertEquals("UserId", NamingUtils.snakeToPascalCase("user_id"));
        assertEquals("CreateTime", NamingUtils.snakeToPascalCase("create_time"));
        assertEquals("UserAccountInfo", NamingUtils.snakeToPascalCase("user_account_info"));
        
        // 测试边界情况
        assertEquals("", NamingUtils.snakeToPascalCase(""));
        assertEquals("", NamingUtils.snakeToPascalCase(null));
        assertEquals("User", NamingUtils.snakeToPascalCase("user"));
        assertEquals("User", NamingUtils.snakeToPascalCase("_user"));
        assertEquals("User", NamingUtils.snakeToPascalCase("user_"));
        assertEquals("User", NamingUtils.snakeToPascalCase("__user__"));
    }
    
    @Test
    void testComplexCases() {
        // 测试复杂的命名转换
        String snakeCase = "user_account_detail_info";
        String camelCase = "userAccountDetailInfo";
        String pascalCase = "UserAccountDetailInfo";
        
        // 循环转换测试
        assertEquals(camelCase, NamingUtils.toCamelCase(snakeCase));
        assertEquals(snakeCase, NamingUtils.toSnakeCase(camelCase));
        assertEquals(pascalCase, NamingUtils.snakeToPascalCase(snakeCase));
        assertEquals(camelCase, NamingUtils.pascalToCamelCase(pascalCase));
        assertEquals(pascalCase, NamingUtils.camelToPascalCase(camelCase));
    }
    
    @Test
    void testSpecialCharacters() {
        // 测试包含数字的情况
        assertEquals("userId1", NamingUtils.toCamelCase("user_id_1"));
        assertEquals("user_id1", NamingUtils.toSnakeCase("userId1"));
        
        // 测试单个字符
        assertEquals("a", NamingUtils.toCamelCase("a"));
        assertEquals("a", NamingUtils.toSnakeCase("A"));
        assertEquals("A", NamingUtils.camelToPascalCase("a"));
        assertEquals("a", NamingUtils.pascalToCamelCase("A"));
    }
}