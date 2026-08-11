package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 监控自动配置装配测试（MON-001 验收）。
 *
 * <p>刻画并固化：
 * <ul>
 *   <li>P0-4 根因不复现 —— {@link SqlMonitoringProperties} 仅注册一个 bean，
 *       不再出现 {@code NoUniqueBeanDefinitionException}。</li>
 *   <li>业务自定义 Bean 时框架退让（{@code @ConditionalOnMissingBean}）。</li>
 *   <li>disabled 时整个自动配置退让（步骤 5 Bean 图）。</li>
 * </ul>
 *
 * <p>历史缺陷（FRM-002 审计 P0-4）：{@link SqlMonitoringAutoConfiguration} 同时用
 * {@code @EnableConfigurationProperties(SqlMonitoringProperties.class)} 与
 * {@code @Bean sqlMonitoringProperties()} 注册属性类，导致按类型注入时找到 2 个候选
 * （{@code sqlMonitoringProperties} 与 {@code fastddd.monitor.sql-...SqlMonitoringProperties}），
 * 抛 {@code NoUniqueBeanDefinitionException}。修复后属性类仅由
 * {@code @EnableConfigurationProperties} 单一方式注册。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class SqlMonitoringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SqlMonitoringAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    /**
     * P0-4 验收：上下文正常启动，且 SqlMonitoringProperties 只有一个 bean。
     */
    @Test
    void singlePropertiesBean_noNoUniqueException() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            Map<String, SqlMonitoringProperties> propsBeans =
                    context.getBeansOfType(SqlMonitoringProperties.class);
            assertThat(propsBeans)
                    .as("SqlMonitoringProperties 应只注册一次（修复 P0-4 双注册）")
                    .hasSize(1);
            // 核心链路 bean 均装配
            assertThat(context).hasSingleBean(SqlMetricsCollector.class);
            assertThat(context).hasSingleBean(SqlMonitoringService.class);
            assertThat(context).hasSingleBean(SqlMonitoringMessageFormatter.class);
        });
    }

    /**
     * 步骤 4 退让：业务自定义 SqlMonitoringService 时，框架不注册默认实现。
     */
    @Test
    void customSqlMonitoringService_frameworkBacksOff() {
        runner.withUserConfiguration(CustomServiceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            // 框架默认 SqlMonitoringService 退让，业务自定义的那个是唯一实例
            Map<String, SqlMonitoringService> serviceBeans =
                    context.getBeansOfType(SqlMonitoringService.class);
            assertThat(serviceBeans).hasSize(1);
            assertThat(serviceBeans).containsKey("customSqlMonitoringService");
        });
    }

    /**
     * 步骤 5：Bean 图确认——在满足条件（单 MeterRegistry、enabled）时，
     * SqlMonitoringAutoConfiguration 正向匹配，核心链路四个 bean 均装配。
     * （条件求值结果直接反映在 bean 在/缺席上，故以 bean 在场性作为 conditions 报告的实证。）
     */
    @Test
    void beanGraphFullyAssembled_whenConditionsMet() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlMonitoringProperties.class);
            assertThat(context).hasSingleBean(SqlMetricsCollector.class);
            assertThat(context).hasSingleBean(SqlMonitoringService.class);
            assertThat(context).hasSingleBean(SqlMonitoringMessageFormatter.class);
        });
    }

    /**
     * 步骤 5 补充：disabled 时整个自动配置退让，核心 bean 全部缺席。
     */
    @Test
    void beanGraphEmpty_whenDisabled() {
        runner.withPropertyValues("fastddd.monitor.sql.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SqlMonitoringProperties.class);
            assertThat(context).doesNotHaveBean(SqlMetricsCollector.class);
            assertThat(context).doesNotHaveBean(SqlMonitoringService.class);
        });
    }

    /** 业务自定义 SqlMonitoringService（验证 @ConditionalOnMissingBean 退让）。 */
    @Configuration
    static class CustomServiceConfig {
        @Bean
        SqlMonitoringService customSqlMonitoringService(SqlMetricsCollector collector,
                                                        SqlMonitoringProperties properties) {
            return new SqlMonitoringService(collector, properties);
        }
    }
}
