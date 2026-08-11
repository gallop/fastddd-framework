package com.gallop.fastddd.consumer.webwithoutapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WEB-001 验收载体：依赖 fast-ddd-starter 但不引 fast-ddd-application。
 * WEB-001 修复后 context 正常启动（GlobalExceptionHandler 已 application-free）。
 */
@SpringBootApplication
public class WebWithoutApp {
    public static void main(String[] args) {
        SpringApplication.run(WebWithoutApp.class, args);
    }
}
