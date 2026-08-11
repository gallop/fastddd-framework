package com.gallop.fastddd.consumer.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRM-002 预置场景（ungated）：Event 模块空消费者。
 * 预设 shell——实际启动/失败行为由 EVT 任务刻画（ADR-003 OutboxTransport SPI / 审计 P0-3 多实例重复投递）。
 */
@SpringBootTest(classes = EventConsumerTest.App.class)
class EventConsumerTest {

    @SpringBootApplication
    static class App {
    }

    @Test
    void presetShell() {
        assertTrue(true, "预设 shell；EVT 任务将补 Outbox 传输边界刻画");
    }
}
