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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调试ResponseWrapper行为的测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class DebugResponseWrapperTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void debugResultResponse() throws Exception {
        System.out.println("\n=== DEBUG: Testing /test/success endpoint ===");
        
        MvcResult result = mockMvc.perform(get("/test/success")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Raw Response Body: " + responseBody);
        System.out.println("Response Length: " + responseBody.length());
        
        if (!responseBody.isEmpty()) {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            System.out.println("Parsed JSON: " + jsonNode.toPrettyString());
            
            // 检查是否是双重包装
            if (jsonNode.has("data") && jsonNode.get("data").isObject()) {
                JsonNode dataNode = jsonNode.get("data");
                if (dataNode.has("code") && dataNode.has("message") && dataNode.has("data")) {
                    System.out.println("WARNING: Double wrapping detected!");
                    System.out.println("Outer wrapper: " + jsonNode.get("code") + ", " + jsonNode.get("message"));
                    System.out.println("Inner wrapper: " + dataNode.get("code") + ", " + dataNode.get("message"));
                }
            }
        } else {
            System.out.println("ERROR: Empty response body!");
        }
        
        System.out.println("=== END DEBUG ===");
    }
}