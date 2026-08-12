package com.gallop.fastddd.consumer.webrespwrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WEB-002 验收场景②的消费者启动类。
 *
 * <p>application.yml 显式 opt-in（response-wrapper.enabled=true + jackson.enabled=true）→
 * 响应包装与 Jackson 扩展生效。
 *
 * @author gallop
 * @since 2026-08-12
 */
@SpringBootApplication
public class WebRespWrapApp {

    public static void main(String[] args) {
        SpringApplication.run(WebRespWrapApp.class, args);
    }
}
