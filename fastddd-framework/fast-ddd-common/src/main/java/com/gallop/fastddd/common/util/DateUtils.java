package com.gallop.fastddd.common.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类，提供标准化的日期时间格式化和解析方法
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
public final class DateUtils {
    
    private DateUtils() {
        // 工具类不允许实例化
    }
    
    /**
     * 标准日期时间格式：yyyy-MM-dd HH:mm:ss
     */
    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    
    /**
     * 标准日期格式：yyyy-MM-dd
     */
    public static final String YYYY_MM_DD = "yyyy-MM-dd";
    
    /**
     * 标准时间格式：HH:mm:ss
     */
    public static final String HH_MM_SS = "HH:mm:ss";
    
    /**
     * 紧凑日期时间格式：yyyyMMddHHmmss
     */
    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";
    
    /**
     * 紧凑日期格式：yyyyMMdd
     */
    public static final String YYYYMMDD = "yyyyMMdd";
    
    /**
     * ISO 8601日期时间格式：yyyy-MM-dd'T'HH:mm:ss
     */
    public static final String ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss";
    
    /**
     * 默认日期时间格式化器：yyyy-MM-dd HH:mm:ss
     */
    public static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS);
    
    /**
     * 默认日期格式化器：yyyy-MM-dd
     */
    public static final DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD);
    
    /**
     * 默认时间格式化器：HH:mm:ss
     */
    public static final DateTimeFormatter DEFAULT_TIME_FORMATTER = DateTimeFormatter.ofPattern(HH_MM_SS);
    
    /**
     * 紧凑日期时间格式化器：yyyyMMddHHmmss
     */
    public static final DateTimeFormatter COMPACT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(YYYYMMDDHHMMSS);
    
    /**
     * 紧凑日期格式化器：yyyyMMdd
     */
    public static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern(YYYYMMDD);
    
    /**
     * 使用默认格式（yyyy-MM-dd HH:mm:ss）格式化LocalDateTime
     * 
     * @param dateTime 要格式化的LocalDateTime对象
     * @return 格式化后的字符串，如果输入为null则返回null
     */
    public static String formatDefault(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DEFAULT_DATETIME_FORMATTER);
    }
    
    /**
     * 使用默认格式（yyyy-MM-dd）格式化LocalDate
     * 
     * @param date 要格式化的LocalDate对象
     * @return 格式化后的字符串，如果输入为null则返回null
     */
    public static String formatDefault(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DEFAULT_DATE_FORMATTER);
    }
    
    /**
     * 使用指定格式格式化LocalDateTime
     * 
     * @param dateTime 要格式化的LocalDateTime对象
     * @param pattern 格式模式
     * @return 格式化后的字符串，如果输入为null则返回null
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null || StringUtils.isBlank(pattern)) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 使用指定格式格式化LocalDate
     * 
     * @param date 要格式化的LocalDate对象
     * @param pattern 格式模式
     * @return 格式化后的字符串，如果输入为null则返回null
     */
    public static String format(LocalDate date, String pattern) {
        if (date == null || StringUtils.isBlank(pattern)) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 使用默认格式（yyyy-MM-dd HH:mm:ss）解析字符串为LocalDateTime
     * 
     * @param dateTimeStr 日期时间字符串
     * @return 解析后的LocalDateTime对象，如果输入为null或空字符串则返回null
     */
    public static LocalDateTime parseDefault(String dateTimeStr) {
        if (StringUtils.isBlank(dateTimeStr)) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DEFAULT_DATETIME_FORMATTER);
    }
    
    /**
     * 使用默认格式（yyyy-MM-dd）解析字符串为LocalDate
     * 
     * @param dateStr 日期字符串
     * @return 解析后的LocalDate对象，如果输入为null或空字符串则返回null
     */
    public static LocalDate parseDateDefault(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        return LocalDate.parse(dateStr, DEFAULT_DATE_FORMATTER);
    }
    
    /**
     * 使用默认格式解析日期字符串（重载方法）
     * 
     * @param dateStr 日期字符串
     * @return LocalDate对象
     */
    public static LocalDate parseDate(String dateStr) {
        return parseDateDefault(dateStr);
    }
    
    /**
     * 使用指定格式解析字符串为LocalDateTime
     * 
     * @param dateTimeStr 日期时间字符串
     * @param pattern 格式模式
     * @return 解析后的LocalDateTime对象，如果输入为null或空字符串则返回null
     */
    public static LocalDateTime parse(String dateTimeStr, String pattern) {
        if (StringUtils.isBlank(dateTimeStr) || StringUtils.isBlank(pattern)) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 使用指定格式解析字符串为LocalDate
     * 
     * @param dateStr 日期字符串
     * @param pattern 格式模式
     * @return 解析后的LocalDate对象，如果输入为null或空字符串则返回null
     */
    public static LocalDate parseDate(String dateStr, String pattern) {
        if (StringUtils.isBlank(dateStr) || StringUtils.isBlank(pattern)) {
            return null;
        }
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 获取当前日期时间的默认格式字符串
     * 
     * @return 当前日期时间的字符串表示（yyyy-MM-dd HH:mm:ss格式）
     */
    public static String nowDefault() {
        return formatDefault(LocalDateTime.now());
    }
    
    /**
     * 获取当前日期时间字符串（默认格式）
     * 
     * @return 当前日期时间字符串
     */
    public static String nowDateTimeString() {
        return nowDefault();
    }
    
    /**
     * 获取当前日期时间对象
     * 
     * @return 当前LocalDateTime对象
     */
    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now();
    }
    
    /**
     * 获取当前日期的默认格式字符串
     * 
     * @return 当前日期的字符串表示（yyyy-MM-dd格式）
     */
    public static String todayDefault() {
        return formatDefault(LocalDate.now());
    }
    
    /**
     * 获取今天日期字符串（默认格式）
     * 
     * @return 今天日期字符串
     */
    public static String nowDateString() {
        return todayDefault();
    }
    
    /**
     * 获取今天日期对象
     * 
     * @return 今天LocalDate对象
     */
    public static LocalDate nowDate() {
        return LocalDate.now();
    }
    
    /**
     * 将Date转换为LocalDateTime
     * 
     * @param date Date对象
     * @return LocalDateTime对象，如果输入为null则返回null
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    
    /**
     * 将LocalDateTime转换为Date
     * 
     * @param localDateTime LocalDateTime对象
     * @return Date对象，如果输入为null则返回null
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * 判断两个日期是否是同一天
     * 
     * @param date1 第一个日期
     * @param date2 第二个日期
     * @return 是否是同一天，如果任一参数为null则返回false
     */
    public static boolean isSameDay(LocalDate date1, LocalDate date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return date1.equals(date2);
    }
    
    /**
     * 判断两个日期时间是否是同一天
     * 
     * @param dateTime1 第一个日期时间
     * @param dateTime2 第二个日期时间
     * @return 是否是同一天，如果任一参数为null则返回false
     */
    public static boolean isSameDay(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            return false;
        }
        return dateTime1.toLocalDate().equals(dateTime2.toLocalDate());
    }
    
    /**
     * 使用默认格式格式化LocalDate为字符串
     * 
     * @param date LocalDate对象
     * @return 格式化后的日期字符串
     */
    public static String formatDate(LocalDate date) {
        return formatDefault(date);
    }
    
    /**
     * 使用默认格式格式化LocalDateTime为字符串
     * 
     * @param dateTime LocalDateTime对象
     * @return 格式化后的日期时间字符串
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return formatDefault(dateTime);
    }
    
    /**
     * 使用默认格式解析日期时间字符串
     * 
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime对象
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        return parseDefault(dateTimeStr);
    }
}