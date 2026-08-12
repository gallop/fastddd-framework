package com.gallop.fastddd.consumer.search;

import com.gallop.fastddd.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEARCH-001 独立消费者：disabled 是唯一允许无 SQL/Meilisearch 后端的显式模式；
 * SQL 缺少 MyBatis-Plus 时必须给出可执行诊断，不能静默返回空结果。
 */
class SearchConsumerTest {

    @SpringBootApplication
    static class App {
    }

    @Test
    void disabledModeStartsWithoutSearchBackend() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("fastddd.search.backend=disabled")
                .run()) {
            assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean(SearchService.class));
        }
    }

    @Test
    void sqlModeWithoutMyBatisPlusFailsWithActionableConfiguration() {
        BeanCreationException failure = assertThrows(BeanCreationException.class,
                () -> new SpringApplicationBuilder(App.class)
                        .web(WebApplicationType.NONE)
                        .run("--fastddd.search.backend=sql"));
        assertTrue(failure.getMessage().contains("fastddd.search.backend=sql"),
                "错误应明确指向 SQL 后端配置");
        assertTrue(failure.getMessage().contains("fast-ddd-crud"),
                "错误应提供依赖修复建议");
    }
}
