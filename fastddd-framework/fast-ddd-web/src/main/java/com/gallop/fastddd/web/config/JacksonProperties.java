package com.gallop.fastddd.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jackson配置属性类
 * 支持通过配置文件自定义JSON序列化设置
 *
 * @author gallop
 * @since 2026-02-27
 * @version 1.0.0-SNAPSHOT
 */
@ConfigurationProperties(prefix = "fastddd.jackson")
public class JacksonProperties {

    /**
     * 是否启用自定义Jackson配置（默认启用）
     */
    private boolean enabled = true;

    /**
     * 日期时间格式（用于 java.time.LocalDateTime）
     * 默认: yyyy-MM-dd HH:mm:ss
     */
    private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式（用于 java.time.LocalDate）
     * 默认: yyyy-MM-dd
     */
    private String dateFormat = "yyyy-MM-dd";

    /**
     * 时间格式（用于 java.time.LocalTime）
     * 默认: HH:mm:ss
     */
    private String timeFormat = "HH:mm:ss";

    /**
     * 是否将 Long 类型序列化为字符串（防止前端 JS 精度丢失，如雪花算法 ID）
     * 默认: true
     */
    private boolean longToString = true;

    /**
     * 枚举序列化模式
     * ORDINAL: 使用数字（0, 1, 2...）- Jackson默认
     * TO_STRING: 使用枚举名称（"MALE", "FEMALE"...）- 推荐
     * TO_STRING_METHOD: 使用枚举的 toString() 方法返回值
     * 默认: TO_STRING
     */
    private EnumSerializationMode enumSerialization = EnumSerializationMode.TO_STRING;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public boolean isLongToString() {
        return longToString;
    }

    public void setLongToString(boolean longToString) {
        this.longToString = longToString;
    }

    public EnumSerializationMode getEnumSerialization() {
        return enumSerialization;
    }

    public void setEnumSerialization(EnumSerializationMode enumSerialization) {
        this.enumSerialization = enumSerialization;
    }
}
