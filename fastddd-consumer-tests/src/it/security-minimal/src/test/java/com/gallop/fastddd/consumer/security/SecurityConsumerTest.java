package com.gallop.fastddd.consumer.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRM-002 预置场景（ungated）：Security 模块空消费者。
 * 预设 shell——实际启动/失败行为由 SEC-001 刻画（审计 P0-7：jwt.enabled=false 仍装配 bean）。
 */
@SpringBootTest(classes = SecurityConsumerTest.App.class)
class SecurityConsumerTest {

    @SpringBootApplication
    static class App {
    }

    @Test
    void presetShell() {
        assertTrue(true, "预设 shell；SEC-001 将补充 P0-7 关闭开关的刻画断言");
    }
}
