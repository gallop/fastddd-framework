package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SmartResponseAdvice测试
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
public class SmartResponseAdviceTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testStringEndpointShouldBeWrapped() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/string"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        
        // String类型应该被包装为ApiResponse
        ApiResponse<?> apiResponse = objectMapper.readValue(responseBody, ApiResponse.class);
        assertNotNull(apiResponse);
        assertEquals(200, apiResponse.getCode());
        assertEquals(Messages.get("common.success"), apiResponse.getMessage());
        assertEquals("Hello String!", apiResponse.getData());
        assertNotNull(apiResponse.getTimestamp());
        
        // 验证没有双重包装 - 只应该有一个timestamp字段
        long timestampCount = responseBody.chars().mapToObj(c -> (char) c)
                .map(String::valueOf)
                .reduce("", String::concat)
                .split("timestamp").length - 1;
        assertEquals(1, timestampCount, "Should have exactly one timestamp field, but found: " + timestampCount);
    }
    
    @Test
    void testResultEndpointShouldNotBeWrapped() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Result endpoint response: " + responseBody);
        
        // 验证没有双重包装 - 只应该有一个timestamp字段
        long timestampCount = responseBody.chars().mapToObj(c -> (char) c)
                .map(String::valueOf)
                .reduce("", String::concat)
                .split("timestamp").length - 1;
        
        if (timestampCount > 1) {
            fail("Double wrapping detected! Found " + timestampCount + " timestamp fields. Response: " + responseBody);
        }
        
        assertEquals(1, timestampCount, "Should have exactly one timestamp field, but found: " + timestampCount);
    }
    
    @Test
    void testApiResponseEndpointShouldNotBeWrapped() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/wrapper/api-response"))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        
        // ApiResponse类型不应该被包装，直接返回
        ApiResponse<?> apiResponse = objectMapper.readValue(responseBody, ApiResponse.class);
        assertNotNull(apiResponse);
        assertEquals(200, apiResponse.getCode());
        assertEquals(Messages.get("common.success"), apiResponse.getMessage());
        assertEquals("已包装的ApiResponse", apiResponse.getData());
        assertNotNull(apiResponse.getTimestamp());
        
        // 验证没有双重包装 - 只应该有一个timestamp字段
        long timestampCount = responseBody.chars().mapToObj(c -> (char) c)
                .map(String::valueOf)
                .reduce("", String::concat)
                .split("timestamp").length - 1;
        assertEquals(1, timestampCount, "Should have exactly one timestamp field, but found: " + timestampCount);
    }
}
