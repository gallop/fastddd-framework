package com.gallop.fastddd.consumer.webnodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ECO-006C4 纯 Web 无库消费者：仅 fast-ddd-web + fast-ddd-application，
 * 无 H2、无数据源配置、无自动配置排除——"纯 Web 不强制数据库"契约的载体。
 */
@SpringBootApplication
public class WebNodbApp {
    public static void main(String[] args) {
        SpringApplication.run(WebNodbApp.class, args);
    }
}
