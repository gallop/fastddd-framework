package com.gallop.fastddd.consumer.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FRM-002 预置场景（ungated）：Search 模块空消费者。
 * 预设 shell——实际启动/失败行为由 SEARCH-001 刻画（审计 P0-5：搜索后端缺失时应可诊断，而非崩溃）。
 */
@SpringBootTest(classes = SearchConsumerTest.App.class)
class SearchConsumerTest {

    @SpringBootApplication
    static class App {
    }

    @Test
    void presetShell() {
        assertTrue(true, "预设 shell；SEARCH-001 将补后端缺失可诊断刻画");
    }
}
