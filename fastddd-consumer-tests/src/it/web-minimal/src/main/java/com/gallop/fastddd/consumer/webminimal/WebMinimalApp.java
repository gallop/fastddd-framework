package com.gallop.fastddd.consumer.webminimal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 最小 Web 消费者：仅依赖 fast-ddd-starter（+ 显式 fast-ddd-application + H2），
 * 验证框架在独立 classpath 下可启动 Spring 上下文（FRM-002 验收①）。
 */
@SpringBootApplication
public class WebMinimalApp {
    public static void main(String[] args) {
        SpringApplication.run(WebMinimalApp.class, args);
    }
}
