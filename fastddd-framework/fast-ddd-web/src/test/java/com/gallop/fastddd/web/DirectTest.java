package com.gallop.fastddd.web;

import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.web.controller.TestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 直接测试控制器方法
 */
@SpringBootTest
@ActiveProfiles("test")
public class DirectTest {
    
    @Autowired
    private TestController testController;
    
    @Test
    void testDirectCall() {
        Result<String> result = testController.testSuccess();
        System.out.println("Direct call result: " + result);
        System.out.println("Data: " + result.getData());
        System.out.println("Data type: " + result.getData().getClass().getName());
    }
}