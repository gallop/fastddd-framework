package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Field;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试ResponseBodyAdvice的执行顺序
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdviceOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;
    
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void testAdviceExecutionOrder() throws Exception {
        System.out.println("\n=== Testing Advice Execution Order ===");
        
        // 检查SmartResponseAdvice Bean是否存在
        try {
            Object smartAdvice = applicationContext.getBean("smartResponseAdvice");
            System.out.println("SmartResponseAdvice Bean found: " + smartAdvice.getClass().getName());
        } catch (Exception e) {
            System.out.println("SmartResponseAdvice Bean NOT found: " + e.getMessage());
        }
        
        // 首先检查advice的注册顺序
        try {
            Field adviceField = RequestMappingHandlerAdapter.class.getDeclaredField("requestResponseBodyAdvice");
            adviceField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<Object> adviceList = (List<Object>) adviceField.get(handlerAdapter);
            
            System.out.println("=== Advice Registration Order ===");
            for (int i = 0; i < adviceList.size(); i++) {
                Object advice = adviceList.get(i);
                System.out.println((i + 1) + ". " + advice.getClass().getSimpleName() + " - " + advice.getClass().getName());
                
                // 检查是否有@Order注解
                if (advice.getClass().isAnnotationPresent(org.springframework.core.annotation.Order.class)) {
                    org.springframework.core.annotation.Order orderAnnotation = 
                        advice.getClass().getAnnotation(org.springframework.core.annotation.Order.class);
                    System.out.println("   @Order value: " + orderAnnotation.value());
                }
                
                // 检查是否实现了Ordered接口
                if (advice instanceof org.springframework.core.Ordered) {
                    org.springframework.core.Ordered ordered = (org.springframework.core.Ordered) advice;
                    System.out.println("   Ordered.getOrder(): " + ordered.getOrder());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error accessing advice list: " + e.getMessage());
        }
        
        System.out.println("\n=== Making Request to /test/success ===");
        
        MvcResult result = mockMvc.perform(get("/test/success")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Final Response: " + responseBody);
        
        // 分析响应结构
        if (!responseBody.isEmpty()) {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            System.out.println("\n=== Response Analysis ===");
            System.out.println("Has 'code' field: " + jsonNode.has("code"));
            System.out.println("Has 'message' field: " + jsonNode.has("message"));
            System.out.println("Has 'data' field: " + jsonNode.has("data"));
            System.out.println("Has 'timestamp' field: " + jsonNode.has("timestamp"));
            System.out.println("Has 'success' field: " + jsonNode.has("success"));
            
            if (jsonNode.has("code")) {
                System.out.println("Code value: " + jsonNode.get("code"));
                System.out.println("Code type: " + jsonNode.get("code").getNodeType());
            }
            
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                System.out.println("Data value: " + dataNode);
                System.out.println("Data type: " + dataNode.getNodeType());
                System.out.println("Data is object: " + dataNode.isObject());
                
                if (dataNode.isObject()) {
                    System.out.println("*** DOUBLE WRAPPING DETECTED ***");
                    System.out.println("Inner data has 'code': " + dataNode.has("code"));
                    System.out.println("Inner data has 'message': " + dataNode.has("message"));
                    System.out.println("Inner data has 'data': " + dataNode.has("data"));
                    
                    if (dataNode.has("code")) {
                        System.out.println("Inner code value: " + dataNode.get("code"));
                        System.out.println("Inner code type: " + dataNode.get("code").getNodeType());
                    }
                }
            }
        }
        
        System.out.println("=== End Advice Order Test ===");
    }
}