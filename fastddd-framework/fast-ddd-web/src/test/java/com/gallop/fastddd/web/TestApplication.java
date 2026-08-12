package com.gallop.fastddd.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * 测试应用程序
 *
 * <p>响应包装 opt-in 由 test {@code application.yml} 的
 * {@code fastddd.web.response-wrapper.enabled=true} 驱动（不再使用已废弃的注解）。
 */
@SpringBootApplication(exclude = SecurityAutoConfiguration.class, scanBasePackages = "com.gallop.fastddd.web")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
