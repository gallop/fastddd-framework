package com.gallop.fastddd.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@SpringBootTest
public class DetailedResponseBodyAdviceTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testAllResponseBodyAdviceBeans() {
        System.out.println("=== Detailed ResponseBodyAdvice Analysis ===");
        
        // 获取所有 ResponseBodyAdvice 类型的 bean
        Map<String, ResponseBodyAdvice> adviceBeans = applicationContext.getBeansOfType(ResponseBodyAdvice.class);
        
        System.out.println("Total ResponseBodyAdvice beans found: " + adviceBeans.size());
        
        for (Map.Entry<String, ResponseBodyAdvice> entry : adviceBeans.entrySet()) {
            String beanName = entry.getKey();
            ResponseBodyAdvice advice = entry.getValue();
            
            System.out.println("\n--- ResponseBodyAdvice Bean ---");
            System.out.println("Bean Name: " + beanName);
            System.out.println("Bean Class: " + advice.getClass().getName());
            System.out.println("Bean Package: " + advice.getClass().getPackage().getName());
            System.out.println("Bean toString: " + advice.toString());
            
            // 检查是否是我们的 ResponseWrapper
            if (advice.getClass().getSimpleName().equals("ResponseWrapper")) {
                System.out.println("*** This is our custom ResponseWrapper ***");
            }
            
            // 检查是否是 Spring 内置的
            if (advice.getClass().getPackage().getName().startsWith("org.springframework")) {
                System.out.println("*** This is a Spring built-in ResponseBodyAdvice ***");
            }
        }
        
        // 也检查所有实现了 ResponseBodyAdvice 的 Object 类型 bean
        System.out.println("\n=== Checking all Object beans for ResponseBodyAdvice implementations ===");
        Map<String, Object> allBeans = applicationContext.getBeansOfType(Object.class);
        int count = 0;
        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            Object bean = entry.getValue();
            if (bean instanceof ResponseBodyAdvice) {
                count++;
                System.out.println("Found ResponseBodyAdvice in Object beans: " + entry.getKey() + " -> " + bean.getClass().getName());
            }
        }
        System.out.println("Total ResponseBodyAdvice found in Object beans: " + count);
    }
}