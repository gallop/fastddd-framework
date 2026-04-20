package com.gallop.fastddd.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.util.List;

@SpringBootTest
public class RequestMappingHandlerAdapterTest {

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    public void testRequestMappingHandlerAdapterAdvice() {
        System.out.println("=== RequestMappingHandlerAdapter Analysis ===");
        
        try {
            // 使用反射获取 requestResponseBodyAdvice 字段
            Field adviceField = RequestMappingHandlerAdapter.class.getDeclaredField("requestResponseBodyAdvice");
            adviceField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<Object> adviceList = (List<Object>) adviceField.get(handlerAdapter);
            
            System.out.println("Total advice objects in RequestMappingHandlerAdapter: " + adviceList.size());
            
            for (int i = 0; i < adviceList.size(); i++) {
                Object advice = adviceList.get(i);
                System.out.println("\n--- Advice Object " + (i + 1) + " ---");
                System.out.println("Class: " + advice.getClass().getName());
                System.out.println("Package: " + advice.getClass().getPackage().getName());
                System.out.println("toString: " + advice.toString());
                
                if (advice instanceof ResponseBodyAdvice) {
                    System.out.println("*** This is a ResponseBodyAdvice ***");
                    ResponseBodyAdvice<?> responseAdvice = (ResponseBodyAdvice<?>) advice;
                    System.out.println("ResponseBodyAdvice class: " + responseAdvice.getClass().getSimpleName());
                } else {
                    System.out.println("*** This is NOT a ResponseBodyAdvice (might be RequestBodyAdvice) ***");
                }
                
                // 检查是否是 Spring 内置的
                if (advice.getClass().getPackage().getName().startsWith("org.springframework")) {
                    System.out.println("*** This is a Spring built-in advice ***");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error accessing RequestMappingHandlerAdapter advice: " + e.getMessage());
            e.printStackTrace();
        }
    }
}