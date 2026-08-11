package com.gallop.fastddd.monitor.sql;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 监控自动配置装配测试（MON-001 + MON-002 验收）。
 *
 * <p>MON-001：P0-4 双注册不复现；业务自定义 Bean 时框架退让。
 *
 * <p>MON-002：
 * <ul>
 *   <li>核心链路 bean 装配（collector / recorder / service / endpoint）。</li>
 *   <li>DataSource 后置处理器仅在单候选 {@link DataSource} 在场时装配——无数据源时退让。</li>
 *   <li>disabled 时整个自动配置退让。</li>
 * </ul>
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
            assertThat(context).hasSingleBean(SqlMetricsCollector.class);
            assertThat(context).hasSingleBean(SqlMonitoringService.class);
        });
    }

    /**
     * MON-002：核心链路 bean（含 recorder / endpoint）装配。
     */
    @Test
    void coreBeansAssembled_whenConditionsMet() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlMonitoringProperties.class);
            assertThat(context).hasSingleBean(SqlMetricsCollector.class);
            assertThat(context).hasSingleBean(SqlMonitoringExecutionRecorder.class);
            assertThat(context).hasSingleBean(SqlMonitoringService.class);
            assertThat(context).hasSingleBean(SqlMonitoringEndpoint.class);
        });
    }

    /**
     * MON-002：无 DataSource 时，DataSource 后置处理器退让（不装配）。
     */
    @Test
    void dataSourcePostProcessor_backsOff_whenNoDataSource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SqlMonitoringDataSourcePostProcessor.class);
        });
    }

    /**
     * 业务自定义 SqlMonitoringService 时，框架退让。
     */
    @Test
    void customSqlMonitoringService_frameworkBacksOff() {
        runner.withUserConfiguration(CustomServiceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            Map<String, SqlMonitoringService> serviceBeans =
                    context.getBeansOfType(SqlMonitoringService.class);
            assertThat(serviceBeans).hasSize(1);
            assertThat(serviceBeans).containsKey("customSqlMonitoringService");
        });
    }

    /**
     * disabled 时整个自动配置退让。
     */
    @Test
    void beanGraphEmpty_whenDisabled() {
        runner.withPropertyValues("fastddd.monitor.sql.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SqlMonitoringProperties.class);
            assertThat(context).doesNotHaveBean(SqlMetricsCollector.class);
            assertThat(context).doesNotHaveBean(SqlMonitoringService.class);
            assertThat(context).doesNotHaveBean(SqlMonitoringEndpoint.class);
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
