package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 追踪ResponseBodyAdvice执行过程的测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResponseBodyAdviceTraceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void traceResponseBodyAdviceExecution() throws Exception {
        System.out.println("\n=== Tracing ResponseBodyAdvice Execution ===");
        
        // 列出所有ResponseBodyAdvice beans
        Map<String, ResponseBodyAdvice> adviceBeans = applicationContext.getBeansOfType(ResponseBodyAdvice.class);
        System.out.println("\n=== All ResponseBodyAdvice Beans ===");
        for (Map.Entry<String, ResponseBodyAdvice> entry : adviceBeans.entrySet()) {
            ResponseBodyAdvice advice = entry.getValue();
            System.out.println("Bean name: " + entry.getKey());
            System.out.println("Bean class: " + advice.getClass().getName());
            
            // 检查@Order注解
            if (advice.getClass().isAnnotationPresent(org.springframework.core.annotation.Order.class)) {
                org.springframework.core.annotation.Order orderAnnotation = 
                    advice.getClass().getAnnotation(org.springframework.core.annotation.Order.class);
                System.out.println("@Order value: " + orderAnnotation.value());
            }
            
            // 检查Ordered接口
            if (advice instanceof org.springframework.core.Ordered) {
                org.springframework.core.Ordered ordered = (org.springframework.core.Ordered) advice;
                System.out.println("Ordered.getOrder(): " + ordered.getOrder());
            }
            
            System.out.println("---");
        }
        
        System.out.println("\n=== Making Request to /test/success ===");
        
        MvcResult result = mockMvc.perform(get("/test/success")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("\n=== Final Response ===");
        System.out.println(responseBody);
        
        // 分析响应结构
        if (!responseBody.isEmpty()) {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            System.out.println("\n=== Response Structure Analysis ===");
            analyzeJsonStructure(jsonNode, "");
        }
        
        System.out.println("\n=== End Trace ===");
    }
    
    private void analyzeJsonStructure(JsonNode node, String prefix) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = node.get(fieldName);
                System.out.println(prefix + fieldName + ": " + fieldValue.getNodeType() + " = " + 
                    (fieldValue.isValueNode() ? fieldValue.asText() : "[" + fieldValue.getNodeType() + "]"));
                
                if (fieldValue.isObject() || fieldValue.isArray()) {
                    analyzeJsonStructure(fieldValue, prefix + "  ");
                }
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                System.out.println(prefix + "[" + i + "]: " + node.get(i).getNodeType());
                analyzeJsonStructure(node.get(i), prefix + "  ");
            }
        }
    }
}