package com.gallop.fastddd.web.advice;

import com.gallop.fastddd.web.TestApplication;
import com.gallop.fastddd.web.controller.TestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringBootTest(classes = TestApplication.class)
@SpringJUnitConfig
public class SimpleResponseWrapperTest {

    @Autowired
    private TestController testController;

    @Test
    public void testDirectControllerCall() {
        try {
            System.out.println("Calling testController.testObjectWrapper()...");
            Object result = testController.testObjectWrapper();
            System.out.println("Result: " + result);
            System.out.println("Result class: " + result.getClass().getName());
        } catch (Exception e) {
            System.err.println("Exception occurred: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}