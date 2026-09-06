package com.gallop.fastddd.consumer.starterdefaultdruid;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ECO-006C4 聚合 starter 消费者：fast-ddd-starter（聚合显式携带 Druid）+ H2，
 * 不另行选池，验证默认池类型与既有功能不退化。@MapperScan 为消费者自身职责。
 */
@SpringBootApplication
@MapperScan("com.gallop.fastddd.consumer.starterdefaultdruid.mapper")
public class StarterDefaultDruidApp {
    public static void main(String[] args) {
        SpringApplication.run(StarterDefaultDruidApp.class, args);
    }
}
