package com.gallop.fastddd.consumer.fastdoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FastDoc 最小消费者：仅依赖 fastddd-fastdoc-spring-boot-starter（+ jdbc + H2），
 * 验证 FastDoc HTTP 适配在独立 classpath 下可装配（SPLIT-002 步骤 6）。
 */
@SpringBootApplication
public class FastdocMinimalApp {
    public static void main(String[] args) {
        SpringApplication.run(FastdocMinimalApp.class, args);
    }
}
