package com.gallop.fastddd.consumer.monitor;

import com.gallop.fastddd.monitor.sql.SqlMonitoringProperties;
import com.gallop.fastddd.monitor.sql.SqlMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MON-001 验收消费者：fast-ddd-monitor 在最小 classpath（actuator/micrometer 自动提供
 * 单一 {@code MeterRegistry}）下可启动，且 SQL 监控属性 Bean 不再重复注册。
 *
 * <p>历史（FRM-002 审计 P0-4）：{@code SqlMonitoringProperties} 被
 * {@code @EnableConfigurationProperties} 与 {@code @Bean sqlMonitoringProperties()} 双注册，
 * 按类型注入时找到 2 个候选，抛 {@code NoUniqueBeanDefinitionException}，上下文无法启动。
 * MON-001 移除 {@code @Bean} 重复注册（保留 {@code @EnableConfigurationProperties} 单一方式），
 * 并清理 {@code SqlMetricsCollector}/{@code SqlMonitoringMessageFormatter}/
 * {@code SqlMonitoringService} 的 {@code @Component}/{@code @Service} 刻板注解，
 * 消除"组件扫描 + AutoConfig @Bean"双重注册隐患；移除依赖固定 Bean 名的 {@code @DependsOn}。
 *
 * <p>验收：① 无 NoUnique，上下文启动成功；② SqlMonitoringProperties 仅一个实例；
 * ③ 核心服务 bean 装配可用。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@SpringBootTest(classes = MonitorConsumerTest.App.class)
class MonitorConsumerTest {

    @Autowired
    SqlMonitoringService sqlMonitoringService;

    @Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @SpringBootApplication
    static class App {
    }

    @Test
    void contextLoadsWithoutNoUniqueException() {
        // ① P0-4 验收：无 NoUnique，上下文启动成功（@SpringBootTest 加载完成即证明）
        assertNotNull(sqlMonitoringService, "SqlMonitoringService 应装配可用");
    }

    @Test
    void singleSqlMonitoringPropertiesBean() {
        // ② SqlMonitoringProperties 仅注册一次（修复双注册根因）
        String[] names = applicationContext
                .getBeanNamesForType(SqlMonitoringProperties.class);
        assertEquals(1, names.length,
                "SqlMonitoringProperties 应只注册一个 bean，实际: " + java.util.Arrays.toString(names));
    }
}
