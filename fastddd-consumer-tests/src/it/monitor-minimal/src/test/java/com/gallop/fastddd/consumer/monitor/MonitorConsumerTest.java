package com.gallop.fastddd.consumer.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRM-002 预置场景（ungated）：Monitor 模块空消费者。
 * 预设 shell——实际启动/失败行为由 MON-001 刻画（审计 P0-4：SQL 监控属性 Bean 修复）。
 */
@SpringBootTest(classes = MonitorConsumerTest.App.class)
class MonitorConsumerTest {

    @SpringBootApplication
    static class App {
    }

    @Test
    void presetShell() {
        assertTrue(true, "预设 shell；MON-001 将补 SQL 监控属性 Bean 刻画");
    }
}
