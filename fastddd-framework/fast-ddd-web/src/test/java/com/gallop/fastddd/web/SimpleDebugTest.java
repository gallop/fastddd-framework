package com.gallop.fastddd.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.annotation.EnableApiResponseWrapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {TestApplication.class},
        properties = {
                "fast-ddd.web.response-wrapper.enabled=true"
        })
@AutoConfigureMockMvc
@EnableApiResponseWrapper
class SimpleDebugTest {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDebugTest.class);

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void testSimpleDebug() throws Exception {
        logger.info("=== Starting SimpleDebugTest ===");
        
        // 检查所有注册的ResponseBodyAdvice
        logger.info("=== Checking ResponseBodyAdvice beans ===");
        try {
            java.util.Map<String, org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice> adviceBeans = 
                applicationContext.getBeansOfType(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice.class);
            logger.info("Found {} ResponseBodyAdvice beans:", adviceBeans.size());
            for (java.util.Map.Entry<String, org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice> entry : adviceBeans.entrySet()) {
                logger.info("  Bean name: {}, Class: {}", entry.getKey(), entry.getValue().getClass().getName());
            }
        } catch (Exception e) {
            logger.warn("Failed to inspect ResponseBodyAdvice: {}", e.getMessage());
        }
        
        MvcResult result = mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        logger.info("Response Body: {}", responseBody);
        logger.info("Response length: {}", responseBody.length());
        
        if (responseBody.length() > 0) {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            logger.info("JSON Node: {}", jsonNode);
            
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                logger.info("Data field: {}", dataNode);
                logger.info("Data field type: {}", dataNode.getNodeType());
                logger.info("Data field asText: {}", dataNode.asText());
                
                if (dataNode.isObject()) {
                    logger.info("Data is object, checking nested structure:");
                    dataNode.fieldNames().forEachRemaining(fieldName -> {
                        logger.info("  Field '{}': {}", fieldName, dataNode.get(fieldName));
                    });
                }
            }
        } else {
            logger.warn("Response body is empty!");
        }
        
        logger.info("=== End SimpleDebugTest ===");
    }
}