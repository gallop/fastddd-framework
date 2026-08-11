package com.gallop.fastddd.consumer.webminimal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FRM-002 验收①：断言最小 Web 消费者在独立 classpath 下 Spring 上下文启动成功。
 * 启动即证明 fast-ddd-starter（含 web/crud/mybatis-plus）从本地仓库制品可解析、可装配。
 */
@SpringBootTest(classes = WebMinimalApp.class)
class ContextLoadsTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Spring 上下文应成功启动");
    }
}
