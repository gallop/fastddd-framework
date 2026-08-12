package com.gallop.fastddd.web;

import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.controller.TestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 直接测试TestController
 */
@SpringBootTest
class TestControllerDirectTest {

    @Autowired
    private TestController testController;

    @Test
    void testControllerDirectCall() {
        System.out.println("=== Direct Controller Call Test ===");
        
        Result<String> result = testController.testSuccess();
        
        System.out.println("Result: " + result);
        System.out.println("Code: " + result.getCode());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Data: " + result.getData());
        System.out.println("Success: " + result.getSuccess());
        System.out.println("Timestamp: " + result.getTimestamp());
        
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(Messages.get("common.success"), result.getMessage());
        assertEquals("Hello FastDDD!", result.getData());
        assertTrue(result.getSuccess());
    }
}
