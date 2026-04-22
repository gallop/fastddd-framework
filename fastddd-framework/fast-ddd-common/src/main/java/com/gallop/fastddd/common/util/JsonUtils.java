package com.gallop.fastddd.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON工具类，提供JSON序列化和反序列化的便捷方法
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
public final class JsonUtils {
    
    private JsonUtils() {
        // 工具类不允许实例化
    }
    
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    /**
     * 获取全局 ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
    
    /**
     * 创建ObjectMapper实例
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 配置ObjectMapper以处理日期时间
        mapper.findAndRegisterModules();
        
        // 配置序列化特性
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        // 配置反序列化特性
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        
        return mapper;
    }
    
    /**
     * 将对象转换为JSON字符串
     * 
     * @param obj 对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert object to JSON: {}", obj, e);
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
    
    /**
     * 将对象转换为格式化的JSON字符串
     * 
     * @param obj 对象
     * @return 格式化的JSON字符串
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert object to pretty JSON: {}", obj, e);
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为对象
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 目标类型
     * @return 对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to object: {}, target class: {}", json, clazz, e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为对象（使用TypeReference）
     * 
     * @param json JSON字符串
     * @param typeReference 类型引用
     * @param <T> 目标类型
     * @return 对象
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to object: {}, type reference: {}", json, typeReference, e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为List
     * 
     * @param json JSON字符串
     * @param elementClass 元素类型
     * @param <T> 元素类型
     * @return List对象
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> elementClass) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, 
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to List: {}, element class: {}", json, elementClass, e);
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为Map
     * 
     * @param json JSON字符串
     * @return Map对象
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        return fromJson(json, new TypeReference<Map<String, Object>>() {});
    }
    
    /**
     * 将对象转换为Map
     * 
     * @param obj 对象
     * @return Map对象
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        
        return OBJECT_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
    
    /**
     * 将Map转换为对象
     * 
     * @param map Map对象
     * @param clazz 目标类型
     * @param <T> 目标类型
     * @return 对象
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        
        return OBJECT_MAPPER.convertValue(map, clazz);
    }
    
    /**
     * 深度复制对象
     * 
     * @param obj 源对象
     * @param clazz 目标类型
     * @param <T> 目标类型
     * @return 复制后的对象
     */
    public static <T> T deepCopy(Object obj, Class<T> clazz) {
        if (obj == null) {
            return null;
        }
        
        String json = toJson(obj);
        return fromJson(json, clazz);
    }
    
    /**
     * 判断字符串是否为有效的JSON
     * 
     * @param json JSON字符串
     * @return 是否为有效的JSON
     */
    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}