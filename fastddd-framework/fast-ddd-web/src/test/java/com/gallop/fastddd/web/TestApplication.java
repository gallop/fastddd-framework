package com.gallop.fastddd.web;

import com.gallop.fastddd.web.annotation.EnableApiResponseWrapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * 测试应用程序
 */
@SpringBootApplication(exclude = SecurityAutoConfiguration.class, scanBasePackages = "com.gallop.fastddd.web")
@EnableApiResponseWrapper
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}