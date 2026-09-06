package com.gallop.fastddd.consumer.webcruddruid;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ECO-006C4 Web/CRUD + 显式 Druid 消费者：fast-ddd-web（传递 crud/model）+
 * druid-spring-boot-3-starter（显式选池）。@MapperScan 为消费者自身职责（框架不代管）。
 */
@SpringBootApplication
@MapperScan("com.gallop.fastddd.consumer.webcruddruid.mapper")
public class WebCrudDruidApp {
    public static void main(String[] args) {
        SpringApplication.run(WebCrudDruidApp.class, args);
    }
}
