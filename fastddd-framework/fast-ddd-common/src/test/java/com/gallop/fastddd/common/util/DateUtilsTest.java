package com.gallop.fastddd.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateUtils测试类
 * 
 * @author gallop
 * @since 2025-01-11
 * @version 1.0.0-SNAPSHOT
 */
class DateUtilsTest {
    
    @Test
    void testFormatDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 1, 11, 14, 30, 45);
        
        // 测试默认格式
        assertEquals("2025-01-11 14:30:45", DateUtils.formatDateTime(dateTime));
        
        // 测试自定义格式
        assertEquals("2025/01/11 14:30:45", DateUtils.format(dateTime, "yyyy/MM/dd HH:mm:ss"));
        assertEquals("20250111143045", DateUtils.format(dateTime, "yyyyMMddHHmmss"));
        
        // 测试null值
        assertNull(DateUtils.formatDateTime(null));
        assertNull(DateUtils.format((LocalDateTime) null, "yyyy-MM-dd"));
    }
    
    @Test
    void testFormatDate() {
        LocalDate date = LocalDate.of(2025, 1, 11);
        
        // 测试默认格式
        assertEquals("2025-01-11", DateUtils.formatDate(date));
        
        // 测试自定义格式
        assertEquals("2025/01/11", DateUtils.format(date, "yyyy/MM/dd"));
        assertEquals("20250111", DateUtils.format(date, "yyyyMMdd"));
        
        // 测试null值
        assertNull(DateUtils.formatDate(null));
        assertNull(DateUtils.format((LocalDate) null, "yyyy-MM-dd"));
    }
    
    @Test
    void testParseDateTime() {
        // 测试默认格式解析
        LocalDateTime expected = LocalDateTime.of(2025, 1, 11, 14, 30, 45);
        assertEquals(expected, DateUtils.parseDateTime("2025-01-11 14:30:45"));
        
        // 测试自定义格式解析
        assertEquals(expected, DateUtils.parse("2025/01/11 14:30:45", "yyyy/MM/dd HH:mm:ss"));
        assertEquals(expected, DateUtils.parse("20250111143045", "yyyyMMddHHmmss"));
        
        // 测试null和空字符串
        assertNull(DateUtils.parseDateTime(null));
        assertNull(DateUtils.parseDateTime(""));
        assertNull(DateUtils.parseDateTime("   "));
        
        // 测试无效格式
        assertThrows(DateTimeParseException.class, () -> DateUtils.parseDateTime("invalid-date"));
        assertThrows(DateTimeParseException.class, () -> DateUtils.parseDateTime("2025-13-01 25:00:00"));
    }
    
    @Test
    void testParseDate() {
        // 测试默认格式解析
        LocalDate expected = LocalDate.of(2025, 1, 11);
        assertEquals(expected, DateUtils.parseDate("2025-01-11"));
        
        // 测试自定义格式解析
        assertEquals(expected, DateUtils.parseDate("2025/01/11", "yyyy/MM/dd"));
        assertEquals(expected, DateUtils.parseDate("20250111", "yyyyMMdd"));
        
        // 测试null和空字符串
        assertNull(DateUtils.parseDate(null));
        assertNull(DateUtils.parseDate(""));
        assertNull(DateUtils.parseDate("   "));
        
        // 测试无效格式
        assertThrows(DateTimeParseException.class, () -> DateUtils.parseDate("invalid-date"));
        assertThrows(DateTimeParseException.class, () -> DateUtils.parseDate("2025-13-01"));
    }
    
    @Test
    void testNowDateTime() {
        LocalDateTime now = DateUtils.nowDateTime();
        assertNotNull(now);
        
        // 验证返回的时间是当前时间（允许1秒误差）
        LocalDateTime currentTime = LocalDateTime.now();
        assertTrue(Math.abs(now.toLocalTime().toSecondOfDay() - currentTime.toLocalTime().toSecondOfDay()) <= 1);
    }
    
    @Test
    void testNowDate() {
        LocalDate today = DateUtils.nowDate();
        assertNotNull(today);
        assertEquals(LocalDate.now(), today);
    }
    
    @Test
    void testNowDateTimeString() {
        String nowStr = DateUtils.nowDateTimeString();
        assertNotNull(nowStr);
        assertTrue(nowStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        
        // 验证可以解析回LocalDateTime
        LocalDateTime parsed = DateUtils.parseDateTime(nowStr);
        assertNotNull(parsed);
    }
    
    @Test
    void testNowDateString() {
        String todayStr = DateUtils.nowDateString();
        assertNotNull(todayStr);
        assertTrue(todayStr.matches("\\d{4}-\\d{2}-\\d{2}"));
        
        // 验证可以解析回LocalDate
        LocalDate parsed = DateUtils.parseDate(todayStr);
        assertNotNull(parsed);
    }
    
    @Test
    void testToDate() {
        LocalDateTime dateTime = LocalDateTime.of(2025, 1, 11, 14, 30, 45);
        Date date = DateUtils.toDate(dateTime);
        
        assertNotNull(date);
        
        // 验证转换后的Date对象
        LocalDateTime converted = DateUtils.toLocalDateTime(date);
        assertEquals(dateTime, converted);
        
        // 测试null值
        assertNull(DateUtils.toDate(null));
    }
    
    @Test
    void testToLocalDateTime() {
        Date date = new Date();
        LocalDateTime localDateTime = DateUtils.toLocalDateTime(date);
        
        assertNotNull(localDateTime);
        
        // 验证转换后的LocalDateTime对象
        Date converted = DateUtils.toDate(localDateTime);
        // 由于精度问题，允许1秒误差
        assertTrue(Math.abs(date.getTime() - converted.getTime()) < 1000);
        
        // 测试null值
        assertNull(DateUtils.toLocalDateTime(null));
    }
    
    @Test
    void testIsSameDay() {
        LocalDateTime dateTime1 = LocalDateTime.of(2025, 1, 11, 10, 30, 0);
        LocalDateTime dateTime2 = LocalDateTime.of(2025, 1, 11, 20, 45, 30);
        LocalDateTime dateTime3 = LocalDateTime.of(2025, 1, 12, 10, 30, 0);
        
        // 测试同一天的不同时间
        assertTrue(DateUtils.isSameDay(dateTime1, dateTime2));
        
        // 测试不同天
        assertFalse(DateUtils.isSameDay(dateTime1, dateTime3));
        assertFalse(DateUtils.isSameDay(dateTime2, dateTime3));
        
        // 测试null值
        assertFalse(DateUtils.isSameDay((LocalDateTime) null, dateTime1));
        assertFalse(DateUtils.isSameDay(dateTime1, (LocalDateTime) null));
        assertFalse(DateUtils.isSameDay((LocalDateTime) null, (LocalDateTime) null));
        
        // 测试相同对象
        assertTrue(DateUtils.isSameDay(dateTime1, dateTime1));
    }
    
    @Test
    void testFormatConstants() {
        // 验证格式常量的正确性
        LocalDateTime dateTime = LocalDateTime.of(2025, 1, 11, 14, 30, 45);
        
        assertEquals("2025-01-11 14:30:45", dateTime.format(DateUtils.DEFAULT_DATETIME_FORMATTER));
        assertEquals("2025-01-11", dateTime.toLocalDate().format(DateUtils.DEFAULT_DATE_FORMATTER));
        assertEquals("14:30:45", dateTime.toLocalTime().format(DateUtils.DEFAULT_TIME_FORMATTER));
        assertEquals("20250111143045", dateTime.format(DateUtils.COMPACT_DATETIME_FORMATTER));
        assertEquals("20250111", dateTime.toLocalDate().format(DateUtils.COMPACT_DATE_FORMATTER));
    }
    
    @Test
    void testEdgeCases() {
        // 测试闰年
        LocalDate leapYear = LocalDate.of(2024, 2, 29);
        assertEquals("2024-02-29", DateUtils.formatDate(leapYear));
        assertEquals(leapYear, DateUtils.parseDate("2024-02-29"));
        
        // 测试年末年初
        LocalDateTime yearEnd = LocalDateTime.of(2024, 12, 31, 23, 59, 59);
        LocalDateTime yearStart = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        
        assertFalse(DateUtils.isSameDay(yearEnd, yearStart));
        
        // 测试格式化和解析的一致性
        String formatted = DateUtils.formatDateTime(yearEnd);
        LocalDateTime parsed = DateUtils.parseDateTime(formatted);
        assertEquals(yearEnd, parsed);
    }
}