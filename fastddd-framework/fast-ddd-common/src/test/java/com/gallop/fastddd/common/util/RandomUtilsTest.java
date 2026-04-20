package com.gallop.fastddd.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RandomUtils测试类
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
class RandomUtilsTest {
    
    @Test
    void testRandomNumeric() {
        // 测试生成指定长度的数字字符串
        String numeric5 = RandomUtils.randomNumeric(5);
        assertEquals(5, numeric5.length());
        assertTrue(numeric5.matches("\\d{5}"));
        
        String numeric10 = RandomUtils.randomNumeric(10);
        assertEquals(10, numeric10.length());
        assertTrue(numeric10.matches("\\d{10}"));
        
        // 测试生成的随机性（多次生成应该不同）
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(RandomUtils.randomNumeric(6));
        }
        assertTrue(results.size() > 50, "生成的随机数字字符串应该有足够的随机性");
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomNumeric(0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomNumeric(-1));
    }
    
    @Test
    void testRandomAlpha() {
        // 测试生成指定长度的小写字母字符串
        String alpha5 = RandomUtils.randomAlpha(5);
        assertEquals(5, alpha5.length());
        assertTrue(alpha5.matches("[a-z]{5}"));
        
        String alpha8 = RandomUtils.randomAlpha(8);
        assertEquals(8, alpha8.length());
        assertTrue(alpha8.matches("[a-z]{8}"));
        
        // 测试生成的随机性
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(RandomUtils.randomAlpha(6));
        }
        assertTrue(results.size() > 50, "生成的随机字母字符串应该有足够的随机性");
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomAlpha(0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomAlpha(-1));
    }
    
    @Test
    void testRandomAlphaUpper() {
        // 测试生成指定长度的大写字母字符串
        String alphaUpper5 = RandomUtils.randomAlphaUpper(5);
        assertEquals(5, alphaUpper5.length());
        assertTrue(alphaUpper5.matches("[A-Z]{5}"));
        
        String alphaUpper8 = RandomUtils.randomAlphaUpper(8);
        assertEquals(8, alphaUpper8.length());
        assertTrue(alphaUpper8.matches("[A-Z]{8}"));
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomAlphaUpper(0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomAlphaUpper(-1));
    }
    
    @Test
    void testRandomString() {
        // 测试生成指定长度的字母数字字符串
        String str5 = RandomUtils.randomString(5);
        assertEquals(5, str5.length());
        assertTrue(str5.matches("[a-zA-Z0-9]{5}"));
        
        String str10 = RandomUtils.randomString(10);
        assertEquals(10, str10.length());
        assertTrue(str10.matches("[a-zA-Z0-9]{10}"));
        
        // 测试生成的随机性
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(RandomUtils.randomString(8));
        }
        assertTrue(results.size() > 50, "生成的随机字符串应该有足够的随机性");
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString(0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString(-1));
    }
    
    @Test
    void testRandomStringWithChars() {
        // 测试使用自定义字符集生成随机字符串
        String customChars = "ABC123";
        String result = RandomUtils.randomString(customChars, 10);
        assertEquals(10, result.length());
        
        // 验证生成的字符串只包含指定字符
        for (char c : result.toCharArray()) {
            assertTrue(customChars.indexOf(c) >= 0, "生成的字符应该在指定字符集中");
        }
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString("ABC", 0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString("ABC", -1));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString("", 5));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString(null, 5));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomString("   ", 5));
    }
    
    @Test
    void testRandomInt() {
        // 测试生成指定范围内的随机整数
        for (int i = 0; i < 100; i++) {
            int result = RandomUtils.randomInt(1, 10);
            assertTrue(result >= 1 && result <= 10, "随机整数应该在指定范围内");
        }
        
        // 测试边界值
        assertEquals(5, RandomUtils.randomInt(5, 5));
        
        // 测试负数范围
        for (int i = 0; i < 50; i++) {
            int result = RandomUtils.randomInt(-10, -1);
            assertTrue(result >= -10 && result <= -1, "负数范围的随机整数应该在指定范围内");
        }
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomInt(10, 5));
    }
    
    @Test
    void testRandomLong() {
        // 测试生成指定范围内的随机长整数
        for (int i = 0; i < 100; i++) {
            long result = RandomUtils.randomLong(1L, 100L);
            assertTrue(result >= 1L && result <= 100L, "随机长整数应该在指定范围内");
        }
        
        // 测试边界值
        assertEquals(5L, RandomUtils.randomLong(5L, 5L));
        
        // 测试大数值范围
        for (int i = 0; i < 50; i++) {
            long result = RandomUtils.randomLong(1000000000L, 2000000000L);
            assertTrue(result >= 1000000000L && result <= 2000000000L, "大数值范围的随机长整数应该在指定范围内");
        }
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomLong(100L, 50L));
    }
    
    @Test
    void testRandomDouble() {
        // 测试生成指定范围内的随机双精度浮点数
        for (int i = 0; i < 100; i++) {
            double result = RandomUtils.randomDouble(1.0, 10.0);
            assertTrue(result >= 1.0 && result < 10.0, "随机双精度浮点数应该在指定范围内");
        }
        
        // 测试小数范围
        for (int i = 0; i < 50; i++) {
            double result = RandomUtils.randomDouble(0.1, 0.9);
            assertTrue(result >= 0.1 && result < 0.9, "小数范围的随机双精度浮点数应该在指定范围内");
        }
        
        // 测试异常情况
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomDouble(10.0, 5.0));
        assertThrows(IllegalArgumentException.class, () -> RandomUtils.randomDouble(5.0, 5.0));
    }
    
    @Test
    void testRandomBoolean() {
        // 测试生成随机布尔值
        Set<Boolean> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(RandomUtils.randomBoolean());
        }
        
        // 应该包含true和false
        assertEquals(2, results.size(), "随机布尔值应该包含true和false");
        assertTrue(results.contains(true));
        assertTrue(results.contains(false));
    }
    
    @Test
    void testRandomUuid() {
        // 测试生成32位UUID字符串
        String uuid1 = RandomUtils.randomUuid();
        String uuid2 = RandomUtils.randomUuid();
        
        assertEquals(32, uuid1.length());
        assertEquals(32, uuid2.length());
        assertNotEquals(uuid1, uuid2, "生成的UUID应该不同");
        
        // 验证UUID格式（32位十六进制字符）
        assertTrue(uuid1.matches("[0-9a-f]{32}"));
        assertTrue(uuid2.matches("[0-9a-f]{32}"));
    }
    
    @Test
    void testRandomUuidWithHyphen() {
        // 测试生成36位标准格式UUID字符串
        String uuid1 = RandomUtils.randomUuidWithHyphen();
        String uuid2 = RandomUtils.randomUuidWithHyphen();
        
        assertEquals(36, uuid1.length());
        assertEquals(36, uuid2.length());
        assertNotEquals(uuid1, uuid2, "生成的UUID应该不同");
        
        // 验证UUID格式（标准格式：8-4-4-4-12）
        assertTrue(uuid1.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertTrue(uuid2.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    
    @Test
    void testRandomMobile() {
        // 测试生成随机手机号码
        for (int i = 0; i < 50; i++) {
            String mobile = RandomUtils.randomMobile();
            assertEquals(11, mobile.length(), "手机号码应该是11位");
            assertTrue(mobile.matches("1[3-9]\\d{9}"), "手机号码格式应该正确");
        }
        
        // 测试生成的随机性
        Set<String> mobiles = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            mobiles.add(RandomUtils.randomMobile());
        }
        assertTrue(mobiles.size() > 50, "生成的手机号码应该有足够的随机性");
    }
    
    @Test
    void testRandomEmail() {
        // 测试生成随机邮箱地址
        Pattern emailPattern = Pattern.compile("^[a-zA-Z0-9]+@(gmail\\.com|163\\.com|qq\\.com|sina\\.com|hotmail\\.com|outlook\\.com)$");
        
        for (int i = 0; i < 50; i++) {
            String email = RandomUtils.randomEmail();
            assertTrue(emailPattern.matcher(email).matches(), "邮箱格式应该正确: " + email);
        }
        
        // 测试生成的随机性
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            emails.add(RandomUtils.randomEmail());
        }
        assertTrue(emails.size() > 50, "生成的邮箱地址应该有足够的随机性");
    }
    
    @Test
    void testRandomChineseName() {
        // 测试生成随机中文姓名
        for (int i = 0; i < 50; i++) {
            String name = RandomUtils.randomChineseName();
            assertTrue(name.length() >= 2 && name.length() <= 3, 
                "中文姓名长度应该在2-3个字符之间，实际长度：" + name.length() + "，姓名：" + name);
            
            // 验证是否包含中文字符（简单验证）
            boolean containsChinese = false;
            for (char c : name.toCharArray()) {
                if (c >= 0x4e00 && c <= 0x9fff) {
                    containsChinese = true;
                    break;
                }
            }
            assertTrue(containsChinese, "姓名应该包含中文字符，实际姓名：" + name);
        }
        
        // 测试生成的随机性
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            names.add(RandomUtils.randomChineseName());
        }
        assertTrue(names.size() > 30, "生成的中文姓名应该有一定的随机性，实际生成了 " + names.size() + " 个不同的姓名");
    }
    
    @Test
    void testRandomnessDistribution() {
        // 测试随机数分布的均匀性
        int[] counts = new int[10];
        for (int i = 0; i < 1000; i++) {
            int value = RandomUtils.randomInt(0, 9);
            counts[value]++;
        }
        
        // 每个数字出现的次数应该相对均匀（允许一定偏差）
        for (int count : counts) {
            assertTrue(count > 50 && count < 150, "随机数分布应该相对均匀");
        }
    }
}