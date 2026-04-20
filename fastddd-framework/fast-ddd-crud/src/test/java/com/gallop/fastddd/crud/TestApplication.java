package com.gallop.fastddd.crud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BaseRepository集成测试的测试应用程序。
 * 为测试上下文提供Spring Boot配置。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootApplication
@MapperScan("com.gallop.fastddd.crud.repository")
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}