package com.gallop.fastddd.consumer.webrespoff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WEB-002 验收场景①的消费者启动类。
 *
 * <p>不设任何 fastddd opt-in 属性 → 响应包装与 Jackson 扩展均默认关。
 * 组件扫描仅覆盖本包（控制器 DemoController），框架 web bean 经 fast-ddd-starter 自动配置装配。
 *
 * @author gallop
 * @since 2026-08-12
 */
@SpringBootApplication
public class WebRespOffApp {

    public static void main(String[] args) {
        SpringApplication.run(WebRespOffApp.class, args);
    }
}
