package com.gallop.fastddd.consumer.webwithoutapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * P0-2 复现载体：依赖 fast-ddd-starter 但不引 fast-ddd-application。
 * 启动时 GlobalExceptionHandler 装配触发 CommandValidationException 类加载失败。
 */
@SpringBootApplication
public class WebWithoutApp {
    public static void main(String[] args) {
        SpringApplication.run(WebWithoutApp.class, args);
    }
}
