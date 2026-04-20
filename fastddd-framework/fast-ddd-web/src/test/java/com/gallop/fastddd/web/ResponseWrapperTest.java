package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.gallop.fastddd.web.annotation.EnableApiResponseWrapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ResponseWrapper功能测试
 */
@SpringBootTest
@AutoConfigureWebMvc
@EnableApiResponseWrapper
public class ResponseWrapperTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }
    
    @Test
    public void testStringResponseWrapping() throws Exception {
        System.out.println("=== Testing String Response Wrapping ===");
        
        MvcResult result = mockMvc.perform(get("/test/string"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Response Body: " + responseBody);
        
        // 解析JSON响应
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        
        // 验证ApiResponse结构
        System.out.println("Checking ApiResponse structure:");
        System.out.println("  code: " + jsonNode.get("code").asInt());
        System.out.println("  message: " + jsonNode.get("message").asText());
        System.out.println("  data: " + jsonNode.get("data").asText());
        System.out.println("  timestamp: " + jsonNode.get("timestamp").asText());
        System.out.println("  success: " + jsonNode.get("success").asBoolean());
        
        // 断言
        assert jsonNode.get("code").asInt() == 200;
        assert "操作成功".equals(jsonNode.get("message").asText());
        assert "Hello String!".equals(jsonNode.get("data").asText());
        assert jsonNode.get("success").asBoolean();
        
        System.out.println("✅ String response wrapping test passed!");
    }
    
    @Test
    public void testResultResponseNotWrapped() throws Exception {
        System.out.println("=== Testing Result Response Not Wrapped ===");
        
        MvcResult result = mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Response Body: " + responseBody);
        
        // 解析JSON响应
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        
        // 验证这是直接的Result结构，没有被ApiResponse包装
        System.out.println("Checking Result structure (should NOT be wrapped):");
        System.out.println("  code: " + jsonNode.get("code").asInt());
        System.out.println("  message: " + jsonNode.get("message").asText());
        System.out.println("  data: " + jsonNode.get("data").asText());
        System.out.println("  timestamp: " + jsonNode.get("timestamp").asText());
        System.out.println("  success: " + jsonNode.get("success").asBoolean());
        
        // 断言 - 应该是Result结构，不是ApiResponse结构
        assert jsonNode.get("code").asInt() == 200;
        assert "操作成功".equals(jsonNode.get("message").asText());
        assert "Hello FastDDD!".equals(jsonNode.get("data").asText());
        assert jsonNode.get("success").asBoolean();
        
        // 确保没有被双重包装（没有外层的ApiResponse结构）
        assert !jsonNode.has("data") || !jsonNode.get("data").isObject() || 
               !jsonNode.get("data").has("code"); // 如果data是对象且有code字段，说明被双重包装了
        
        System.out.println("✅ Result response not wrapped test passed!");
    }
}