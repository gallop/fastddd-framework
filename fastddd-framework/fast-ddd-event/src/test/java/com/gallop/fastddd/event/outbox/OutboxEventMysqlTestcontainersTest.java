package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.domain.DomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox 事件 MySQL 真实映射测试（EVT-002 步骤5：MySQL/Testcontainers 映射测试）。
 *
 * <p><b>环境门禁</b>：仅当系统属性 {@code -Dfastddd.test.docker=true} 时启用——本仓库 CI
 * 无 Docker runner（内网 Gitea），开发者本机需显式开启以避免无 Docker 环境下 reactor 构建失败。
 * 默认休眠（disabled），不影响 {@code clean verify -Ptest}。
 *
 * <p>H2 兼容映射的覆盖由 {@link OutboxEventIntegrationTest} 承载；本类为 MySQL 专有映射
 * （CHECK 约束、InnoDB 行锁、utf8mb4）的权威验证，运行命令：
 * <pre>{@code
 * export JAVA_HOME="<path-to-jdk-21>"
 * ./mvnw -B test -pl fastddd-framework/fast-ddd-event -Dtest=OutboxEventMysqlTestcontainersTest -Dfastddd.test.docker=true
 * }</pre>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Testcontainers
@SpringBootTest(classes = OutboxEventMysqlTestcontainersTest.TestApp.class,
        properties = {
                "fastddd.event.mode=outbox",
                "fastddd.event.outbox.dispatcher.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:sql/outbox_events_mysql.sql"
        })
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "fastddd.test.docker", matches = "true",
        disabledReason = "需 Docker（-Dfastddd.test.docker=true 显式开启）；CI 无 runner，详见类 Javadoc")
class OutboxEventMysqlTestcontainersTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    static class TestDomainEvent extends DomainEvent {
        TestDomainEvent(Serializable aggregateId, String aggregateType) {
            super(aggregateId, aggregateType);
        }
    }

    @Test
    void mysqlRealMapping_claimPublishDeadLetter() {
        OutboxEvent saved = outboxEventService.saveEvent(
                new TestDomainEvent("agg-1", "TestAggregate"), "agg-1", "TestAggregate");
        String eventId = saved.getEventId();

        // claim → PROCESSING
        List<String> claimed = outboxEventService.claimEvents(5);
        assertTrue(claimed.contains(eventId));
        assertEquals(OutboxEventStatus.PROCESSING,
                outboxEventService.findByEventId(eventId).orElseThrow().getStatus());

        // mark-sent → PUBLISHED
        assertTrue(outboxEventService.markAsPublished(eventId));
        assertEquals(OutboxEventStatus.PUBLISHED,
                outboxEventService.findByEventId(eventId).orElseThrow().getStatus());

        // 死信查询在 MySQL 下空集（本用例无死信）
        assertTrue(outboxEventService.findDeadLetterEvents(new Page<>(1, 10)).getRecords().isEmpty());
    }
}
