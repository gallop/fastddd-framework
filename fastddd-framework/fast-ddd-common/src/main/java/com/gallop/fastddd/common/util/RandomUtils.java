package com.gallop.fastddd.common.util;

import cn.hutool.core.util.RandomUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数据生成工具类，提供各种类型的随机数据生成方法
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
public final class RandomUtils {
    
    private RandomUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 数字字符集
     */
    private static final String NUMERIC_CHARS = "0123456789";
    
    /**
     * 字母字符集（小写）
     */
    private static final String ALPHA_CHARS = "abcdefghijklmnopqrstuvwxyz";
    
    /**
     * 字母字符集（大写）
     */
    private static final String ALPHA_UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    /**
     * 字母数字字符集
     */
    private static final String ALPHANUMERIC_CHARS = ALPHA_CHARS + ALPHA_UPPER_CHARS + NUMERIC_CHARS;
    
    /**
     * 生成指定长度的随机数字字符串
     * 
     * @param length 字符串长度，必须大于0
     * @return 随机数字字符串
     * @throws IllegalArgumentException 如果长度小于等于0
     */
    public static String randomNumeric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        return RandomUtil.randomString(NUMERIC_CHARS, length);
    }
    
    /**
     * 生成指定长度的随机字母字符串（小写）
     * 
     * @param length 字符串长度，必须大于0
     * @return 随机字母字符串
     * @throws IllegalArgumentException 如果长度小于等于0
     */
    public static String randomAlpha(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        return RandomUtil.randomString(ALPHA_CHARS, length);
    }
    
    /**
     * 生成指定长度的随机字母字符串（大写）
     * 
     * @param length 字符串长度，必须大于0
     * @return 随机字母字符串
     * @throws IllegalArgumentException 如果长度小于等于0
     */
    public static String randomAlphaUpper(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        return RandomUtil.randomString(ALPHA_UPPER_CHARS, length);
    }
    
    /**
     * 生成指定长度的随机字母数字字符串
     * 
     * @param length 字符串长度，必须大于0
     * @return 随机字母数字字符串
     * @throws IllegalArgumentException 如果长度小于等于0
     */
    public static String randomString(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        return RandomUtil.randomString(ALPHANUMERIC_CHARS, length);
    }
    
    /**
     * 生成指定字符集的随机字符串
     * 
     * @param chars 字符集
     * @param length 字符串长度，必须大于0
     * @return 随机字符串
     * @throws IllegalArgumentException 如果长度小于等于0或字符集为空
     */
    public static String randomString(String chars, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }
        if (StringUtils.isBlank(chars)) {
            throw new IllegalArgumentException("Chars cannot be null or empty");
        }
        return RandomUtil.randomString(chars, length);
    }
    
    /**
     * 生成指定范围内的随机整数（包含边界）
     * 
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     * @throws IllegalArgumentException 如果min大于max
     */
    public static int randomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        return RandomUtil.randomInt(min, max + 1);
    }
    
    /**
     * 生成指定范围内的随机长整数（包含边界）
     * 
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机长整数
     * @throws IllegalArgumentException 如果min大于max
     */
    public static long randomLong(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
    
    /**
     * 生成指定范围内的随机双精度浮点数
     * 
     * @param min 最小值（包含）
     * @param max 最大值（不包含）
     * @return 随机双精度浮点数
     * @throws IllegalArgumentException 如果min大于等于max
     */
    public static double randomDouble(double min, double max) {
        if (min >= max) {
            throw new IllegalArgumentException("Min value must be less than max value");
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
    
    /**
     * 生成随机布尔值
     * 
     * @return 随机布尔值
     */
    public static boolean randomBoolean() {
        return RandomUtil.randomBoolean();
    }
    
    /**
     * 生成随机UUID字符串（不包含连字符）
     * 
     * @return 32位随机UUID字符串
     */
    public static String randomUuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成标准格式的随机UUID字符串（包含连字符）
     * 
     * @return 36位标准格式UUID字符串
     */
    public static String randomUuidWithHyphen() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * 生成随机手机号码（中国大陆格式）
     * 
     * @return 11位随机手机号码
     */
    public static String randomMobile() {
        String[] prefixes = {"130", "131", "132", "133", "134", "135", "136", "137", "138", "139",
                            "150", "151", "152", "153", "155", "156", "157", "158", "159",
                            "180", "181", "182", "183", "184", "185", "186", "187", "188", "189"};
        String prefix = prefixes[randomInt(0, prefixes.length - 1)];
        return prefix + randomNumeric(8);
    }
    
    /**
     * 生成随机邮箱地址
     * 
     * @return 随机邮箱地址
     */
    public static String randomEmail() {
        String[] domains = {"gmail.com", "163.com", "qq.com", "sina.com", "hotmail.com", "outlook.com"};
        String username = randomString(randomInt(5, 10));
        String domain = domains[randomInt(0, domains.length - 1)];
        return username + "@" + domain;
    }
    
    /**
     * 生成随机中文姓名
     * 
     * @return 随机中文姓名
     */
    public static String randomChineseName() {
        String[] surnames = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", 
                            "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗"};
        String[] names = {"伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", 
                         "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "霞"};
        
        String surname = surnames[randomInt(0, surnames.length - 1)];
        String name = names[randomInt(0, names.length - 1)];
        
        // 随机决定是否添加第二个名字字符
        if (randomBoolean()) {
            String secondName = names[randomInt(0, names.length - 1)];
            return surname + name + secondName;
        }
        
        return surname + name;
    }
}