package com.gallop.fastddd.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StringUtils测试类
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
class StringUtilsTest {
    
    @Test
    void testIsEmpty() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertFalse(StringUtils.isEmpty("test"));
    }
    
    @Test
    void testIsNotEmpty() {
        assertFalse(StringUtils.isNotEmpty(null));
        assertFalse(StringUtils.isNotEmpty(""));
        assertTrue(StringUtils.isNotEmpty(" "));
        assertTrue(StringUtils.isNotEmpty("test"));
    }
    
    @Test
    void testIsBlank() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank(" "));
        assertTrue(StringUtils.isBlank("\t\n"));
        assertFalse(StringUtils.isBlank("test"));
        assertFalse(StringUtils.isBlank(" test "));
    }
    
    @Test
    void testIsNotBlank() {
        assertFalse(StringUtils.isNotBlank(null));
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank(" "));
        assertFalse(StringUtils.isNotBlank("\t\n"));
        assertTrue(StringUtils.isNotBlank("test"));
        assertTrue(StringUtils.isNotBlank(" test "));
    }
    
    @Test
    void testTrim() {
        assertNull(StringUtils.trim(null));
        assertEquals("", StringUtils.trim(""));
        assertEquals("", StringUtils.trim(" "));
        assertEquals("test", StringUtils.trim(" test "));
        assertEquals("test", StringUtils.trim("\t\ntest\t\n"));
    }
    
    @Test
    void testTrimToEmpty() {
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("", StringUtils.trimToEmpty(""));
        assertEquals("", StringUtils.trimToEmpty(" "));
        assertEquals("test", StringUtils.trimToEmpty(" test "));
    }
    
    @Test
    void testTrimToNull() {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull(""));
        assertNull(StringUtils.trimToNull(" "));
        assertEquals("test", StringUtils.trimToNull(" test "));
    }
    
    @Test
    void testDefaultIfNull() {
        assertEquals("default", StringUtils.defaultIfNull(null, "default"));
        assertEquals("", StringUtils.defaultIfNull("", "default"));
        assertEquals("test", StringUtils.defaultIfNull("test", "default"));
    }
    
    @Test
    void testDefaultIfEmpty() {
        assertEquals("default", StringUtils.defaultIfEmpty(null, "default"));
        assertEquals("default", StringUtils.defaultIfEmpty("", "default"));
        assertEquals(" ", StringUtils.defaultIfEmpty(" ", "default"));
        assertEquals("test", StringUtils.defaultIfEmpty("test", "default"));
    }
    
    @Test
    void testDefaultIfBlank() {
        assertEquals("default", StringUtils.defaultIfBlank(null, "default"));
        assertEquals("default", StringUtils.defaultIfBlank("", "default"));
        assertEquals("default", StringUtils.defaultIfBlank(" ", "default"));
        assertEquals("test", StringUtils.defaultIfBlank("test", "default"));
    }
    
    @Test
    void testJoinArray() {
        assertEquals("", StringUtils.join(","));
        assertEquals("a", StringUtils.join(",", "a"));
        assertEquals("a,b,c", StringUtils.join(",", "a", "b", "c"));
        assertEquals("abc", StringUtils.join(null, "a", "b", "c"));
    }
    
    @Test
    void testJoinCollection() {
        assertEquals("", StringUtils.join(",", List.of()));
        assertEquals("a", StringUtils.join(",", List.of("a")));
        assertEquals("a,b,c", StringUtils.join(",", List.of("a", "b", "c")));
    }
    
    @Test
    void testCapitalize() {
        assertNull(StringUtils.capitalize(null));
        assertEquals("", StringUtils.capitalize(""));
        assertEquals("Test", StringUtils.capitalize("test"));
        assertEquals("Test", StringUtils.capitalize("Test"));
        assertEquals("TEST", StringUtils.capitalize("tEST"));
    }
    
    @Test
    void testUncapitalize() {
        assertNull(StringUtils.uncapitalize(null));
        assertEquals("", StringUtils.uncapitalize(""));
        assertEquals("test", StringUtils.uncapitalize("Test"));
        assertEquals("test", StringUtils.uncapitalize("test"));
        assertEquals("tEST", StringUtils.uncapitalize("TEST"));
    }
    
    @Test
    void testMatches() {
        assertTrue(StringUtils.matches("test", "test"));
        assertTrue(StringUtils.matches("test123", "test\\d+"));
        assertFalse(StringUtils.matches("test", "\\d+"));
        assertFalse(StringUtils.matches(null, "test"));
        assertFalse(StringUtils.matches("test", null));
    }
}