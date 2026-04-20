package com.gallop.fastddd.common.constant;

/**
 * 通用常量类，定义框架中使用的通用常量
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public final class CommonConstants {
    
    private CommonConstants() {
        // 常量类不允许实例化
    }
    
    // ========== 通用常量 ==========
    
    /**
     * 成功状态码
     */
    public static final String SUCCESS_CODE = "200";
    
    /**
     * 失败状态码
     */
    public static final String FAILURE_CODE = "500";
    
    /**
     * 参数错误状态码
     */
    public static final String PARAM_ERROR_CODE = "400";
    
    /**
     * 未授权状态码
     */
    public static final String UNAUTHORIZED_CODE = "401";
    
    /**
     * 禁止访问状态码
     */
    public static final String FORBIDDEN_CODE = "403";
    
    /**
     * 资源未找到状态码
     */
    public static final String NOT_FOUND_CODE = "404";
    
    // ========== 字符串常量 ==========
    
    /**
     * 空字符串
     */
    public static final String EMPTY_STRING = "";
    
    /**
     * 逗号分隔符
     */
    public static final String COMMA = ",";
    
    /**
     * 分号分隔符
     */
    public static final String SEMICOLON = ";";
    
    /**
     * 冒号分隔符
     */
    public static final String COLON = ":";
    
    /**
     * 点分隔符
     */
    public static final String DOT = ".";
    
    /**
     * 斜杠分隔符
     */
    public static final String SLASH = "/";
    
    /**
     * 反斜杠分隔符
     */
    public static final String BACKSLASH = "\\";
    
    /**
     * 下划线
     */
    public static final String UNDERSCORE = "_";
    
    /**
     * 连字符
     */
    public static final String HYPHEN = "-";
    
    /**
     * 等号
     */
    public static final String EQUALS = "=";
    
    /**
     * 与符号
     */
    public static final String AMPERSAND = "&";
    
    /**
     * 问号
     */
    public static final String QUESTION_MARK = "?";
    
    /**
     * 井号
     */
    public static final String HASH = "#";
    
    // ========== 数字常量 ==========
    
    /**
     * 零
     */
    public static final int ZERO = 0;
    
    /**
     * 一
     */
    public static final int ONE = 1;
    
    /**
     * 负一
     */
    public static final int NEGATIVE_ONE = -1;
    
    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;
    
    /**
     * 默认页面大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;
    
    /**
     * 最大页面大小
     */
    public static final int MAX_PAGE_SIZE = 1000;
    
    // ========== 时间常量 ==========
    
    /**
     * 一秒的毫秒数
     */
    public static final long SECOND_IN_MILLIS = 1000L;
    
    /**
     * 一分钟的毫秒数
     */
    public static final long MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS;
    
    /**
     * 一小时的毫秒数
     */
    public static final long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;
    
    /**
     * 一天的毫秒数
     */
    public static final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;
    
    // ========== 日期格式常量 ==========
    
    /**
     * 标准日期格式
     */
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    
    /**
     * 标准时间格式
     */
    public static final String TIME_FORMAT = "HH:mm:ss";
    
    /**
     * 标准日期时间格式
     */
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    /**
     * ISO日期时间格式
     */
    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    
    /**
     * 紧凑日期格式
     */
    public static final String COMPACT_DATE_FORMAT = "yyyyMMdd";
    
    /**
     * 紧凑时间格式
     */
    public static final String COMPACT_TIME_FORMAT = "HHmmss";
    
    /**
     * 紧凑日期时间格式
     */
    public static final String COMPACT_DATETIME_FORMAT = "yyyyMMddHHmmss";
    
    // ========== 编码常量 ==========
    
    /**
     * UTF-8编码
     */
    public static final String UTF8 = "UTF-8";
    
    /**
     * GBK编码
     */
    public static final String GBK = "GBK";
    
    /**
     * ISO-8859-1编码
     */
    public static final String ISO_8859_1 = "ISO-8859-1";
    
    // ========== HTTP常量 ==========
    
    /**
     * HTTP GET方法
     */
    public static final String HTTP_GET = "GET";
    
    /**
     * HTTP POST方法
     */
    public static final String HTTP_POST = "POST";
    
    /**
     * HTTP PUT方法
     */
    public static final String HTTP_PUT = "PUT";
    
    /**
     * HTTP DELETE方法
     */
    public static final String HTTP_DELETE = "DELETE";
    
    /**
     * HTTP PATCH方法
     */
    public static final String HTTP_PATCH = "PATCH";
    
    /**
     * Content-Type头
     */
    public static final String CONTENT_TYPE = "Content-Type";
    
    /**
     * JSON Content-Type
     */
    public static final String APPLICATION_JSON = "application/json";
    
    /**
     * XML Content-Type
     */
    public static final String APPLICATION_XML = "application/xml";
    
    /**
     * 表单Content-Type
     */
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
}