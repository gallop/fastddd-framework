package com.gallop.fastddd.consumer.fastdoc;

import com.gallop.fastddd.fastdoc.FastDocService;
import com.gallop.fastddd.fastdoc.web.FastDocController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SPLIT-002 步骤 6 验收：断言 FastDoc Starter 在独立 classpath（从本地仓库制品解析）
 * 下装配成功——FastDocService（依赖 DataSource）与 FastDocController（/api/fastdoc/*）
 * bean 均存在。证明 FastDoc 功能在显式消费者中保持可用。
 */
@SpringBootTest(classes = FastdocMinimalApp.class)
class FastdocStarterActivatesTest {

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    FastDocService fastDocService;

    @Autowired
    FastDocController fastDocController;

    @Test
    void fastDocBeansArePresent() {
        assertNotNull(applicationContext, "Spring 上下文应成功启动");
        assertNotNull(fastDocService, "FastDocService bean 应由 Starter 装配（DataSource 存在）");
        assertNotNull(fastDocController, "FastDocController bean 应由 Starter 装配");
    }
}
