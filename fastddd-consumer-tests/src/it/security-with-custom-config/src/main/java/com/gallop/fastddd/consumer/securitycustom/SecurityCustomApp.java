package com.gallop.fastddd.consumer.securitycustom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SEC-003 消费者应用：验证业务自定义安全 Bean 与框架共存。
 */
@SpringBootApplication
public class SecurityCustomApp {

    public static void main(String[] args) {
        SpringApplication.run(SecurityCustomApp.class, args);
    }
}
