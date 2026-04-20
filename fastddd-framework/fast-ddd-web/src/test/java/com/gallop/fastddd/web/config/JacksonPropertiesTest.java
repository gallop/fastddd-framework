package com.gallop.fastddd.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 时间格式配置测试
 *
 * @author gallop
 * @since 2026-02-27
 */
class JacksonPropertiesTest {

    @Test
    void testDefaultDateTimeFormat() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        ObjectMapper objectMapper = createObjectMapper(properties);

        LocalDateTime now = LocalDateTime.of(2026, 2, 27, 14, 30, 45);
        String json = objectMapper.writeValueAsString(now);

        // 默认格式: yyyy-MM-dd HH:mm:ss
        System.out.println("LocalDateTime JSON: " + json);
        assertThat(json).contains("2026-02-27 14:30:45");
    }

    @Test
    void testDefaultDateFormat() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        ObjectMapper objectMapper = createObjectMapper(properties);

        LocalDate date = LocalDate.of(2026, 2, 27);
        String json = objectMapper.writeValueAsString(date);

        // 默认格式: yyyy-MM-dd
        System.out.println("LocalDate JSON: " + json);
        assertThat(json).contains("2026-02-27");
    }

    @Test
    void testDefaultTimeFormat() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        ObjectMapper objectMapper = createObjectMapper(properties);

        LocalTime time = LocalTime.of(14, 30, 45);
        String json = objectMapper.writeValueAsString(time);

        // 默认格式: HH:mm:ss
        System.out.println("LocalTime JSON: " + json);
        assertThat(json).contains("14:30:45");
    }

    @Test
    void testLongToStringSerialization() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        properties.setLongToString(true);
        ObjectMapper objectMapper = createObjectMapper(properties);

        Map<String, Object> data = new HashMap<>();
        data.put("longValue", 9007199254740992L);
        data.put("normalValue", 123456);

        String json = objectMapper.writeValueAsString(data);

        // Long 类型应该序列化为字符串
        System.out.println("Long JSON: " + json);
        assertThat(json).contains("\"longValue\":\"9007199254740992\"");
    }

    @Test
    void testCustomDateTimeFormat() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        properties.setDateTimeFormat("yyyy/MM/dd HH:mm:ss");
        properties.setDateFormat("yyyy/MM/dd");
        properties.setTimeFormat("HH:mm:ss.SSS");

        ObjectMapper objectMapper = createObjectMapper(properties);

        LocalDateTime dateTime = LocalDateTime.of(2026, 2, 27, 14, 30, 45);
        String json = objectMapper.writeValueAsString(dateTime);
        System.out.println("Custom LocalDateTime JSON: " + json);
        assertThat(json).contains("2026/02/27 14:30:45");

        LocalDate date = LocalDate.of(2026, 2, 27);
        json = objectMapper.writeValueAsString(date);
        System.out.println("Custom LocalDate JSON: " + json);
        assertThat(json).contains("2026/02/27");

        LocalTime time = LocalTime.of(14, 30, 45);
        json = objectMapper.writeValueAsString(time);
        System.out.println("Custom LocalTime JSON: " + json);
        assertThat(json).contains("14:30:45.000");
    }

    @Test
    void testLongAsNumber() throws Exception {
        JacksonProperties properties = new JacksonProperties();
        properties.setLongToString(false);

        ObjectMapper objectMapper = createObjectMapper(properties);

        // Long 类型不转为字符串
        Map<String, Object> data = new HashMap<>();
        data.put("longValue", 9007199254740992L);
        String json = objectMapper.writeValueAsString(data);
        System.out.println("Long as number JSON: " + json);
        assertThat(json).contains("\"longValue\":9007199254740992");
    }

    /**
     * 根据 JacksonProperties 创建 ObjectMapper
     */
    private ObjectMapper createObjectMapper(JacksonProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();

        // 注册自定义 JavaTimeModule
        JavaTimeModule module = new JavaTimeModule();

        // LocalDateTime
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(properties.getDateTimeFormat());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

        // LocalDate
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(properties.getDateFormat());
        module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));

        // LocalTime
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(properties.getTimeFormat());
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

        objectMapper.registerModule(module);

        // Long 类型序列化为字符串
        if (properties.isLongToString()) {
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(longModule);
        }

        return objectMapper;
    }
}
