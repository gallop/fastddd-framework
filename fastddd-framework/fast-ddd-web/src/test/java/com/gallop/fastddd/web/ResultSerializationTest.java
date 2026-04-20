package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result序列化测试
 */
@SpringBootTest
class ResultSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testResultSerialization() throws Exception {
        // 创建Result对象
        Result<String> result = Result.success("Hello FastDDD!");
        
        // 打印Result对象的字段值
        System.out.println("=== Result Object Fields ===");
        System.out.println("Code: " + result.getCode());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Data: " + result.getData());
        System.out.println("Success: " + result.getSuccess());
        System.out.println("Timestamp: " + result.getTimestamp());
        
        // 序列化为JSON
        String json = objectMapper.writeValueAsString(result);
        System.out.println("=== Serialized JSON ===");
        System.out.println(json);
        
        // 解析JSON
        JsonNode jsonNode = objectMapper.readTree(json);
        System.out.println("=== Parsed JSON Fields ===");
        System.out.println("Code: " + jsonNode.get("code"));
        System.out.println("Message: " + jsonNode.get("message"));
        System.out.println("Data: " + jsonNode.get("data"));
        System.out.println("Success: " + jsonNode.get("success"));
        System.out.println("Timestamp: " + jsonNode.get("timestamp"));
        
        // 验证序列化结果
        assertNotNull(result.getData());
        assertEquals("Hello FastDDD!", result.getData());
        assertEquals("Hello FastDDD!", jsonNode.get("data").asText());
    }
}