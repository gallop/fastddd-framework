package com.gallop.fastddd.event;

import com.gallop.fastddd.event.outbox.OutboxEventConfiguration;
import com.gallop.fastddd.event.outbox.OutboxEventRepository;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.TransactionalEventPublisher;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件装配边界单测（EVT-001 验收）。
 *
 * <p>刻画并固化 local/outbox 装配边界：
 * <ul>
 *   <li>local 模式（默认）：装配 {@link SpringEventPublisher}，零 Outbox Bean
 *       （OutboxEventConfiguration 整体退让）。</li>
 *   <li>outbox 模式但无 DataSource：Outbox 链路退让（依赖不满足），不装配 Outbox Bean。</li>
 * </ul>
 *
 * <p>历史缺陷：EventAutoConfiguration 的宽 {@code @ComponentScan("com.gallop.fastddd.event")}
 * 无条件拾取 outbox 子包，local 场景下 OutboxEventServiceImpl 因缺 ObjectMapper/DataSource
 * 装配失败（{@code UnsatisfiedDependencyException}）。修复后以 {@code fastddd.event.mode}
 * 显式门禁，OutboxEventConfiguration 仅 outbox 模式 + 依赖满足时装配。
 *
 * <p>注：outbox 模式正向装配（DataSource + MyBatis + SqlSessionFactory 齐备）需完整 MyBatis
 * 基础设施，其端到端映射测试属 EVT-002 范畴；本测试聚焦装配边界（退让语义）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class EventAutoConfigurationBoundaryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    EventAutoConfiguration.class, OutboxEventConfiguration.class));

    /**
     * 验收①：local 模式（默认）无数据库可启动，EventPublisher 装配，零 Outbox Bean。
     */
    @Test
    void localMode_noDataSource_startsAndNoOutboxBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EventPublisher.class);
            // local 兜底发布器（@Async 致 JDK 代理，故以 TransactionalEventPublisher 缺席判定 local 实现）
            assertThat(context).doesNotHaveBean(TransactionalEventPublisher.class);
            // 验收②：conditions 中没有 Outbox Bean
            assertThat(context).doesNotHaveBean(OutboxEventService.class);
            assertThat(context).doesNotHaveBean(OutboxEventRepository.class);
            assertThat(context).doesNotHaveBean(OutboxEventConfiguration.class);
            assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
        });
    }

    /**
     * 显式 fastddd.event.mode=local 与默认等价。
     */
    @Test
    void localModeExplicit_sameAsDefault() {
        runner.withPropertyValues("fastddd.event.mode=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context).doesNotHaveBean(OutboxEventService.class);
        });
    }

    /**
     * outbox 模式但无 DataSource（依赖不满足）：Outbox 链路退让，无 Outbox Bean；
     * 且因 mode!=local，springEventPublisher 也不装配（outbox 期望由 outbox 链路提供）。
     */
    @Test
    void outboxModeWithoutDataSource_outboxBacksOff() {
        runner.withPropertyValues("fastddd.event.mode=outbox").run(context -> {
            assertThat(context).hasNotFailed();
            // @ConditionalOnBean(DataSource) 不满足 → Outbox 整体退让
            assertThat(context).doesNotHaveBean(OutboxEventConfiguration.class);
            assertThat(context).doesNotHaveBean(OutboxEventService.class);
            assertThat(context).doesNotHaveBean(OutboxEventRepository.class);
            assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
        });
    }

    /**
     * 模块专用 executor 名：eventTaskExecutor（非通用 taskExecutor）。
     */
    @Test
    void moduleSpecificExecutorName() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("eventTaskExecutor");
            assertThat(context).doesNotHaveBean("taskExecutor");
        });
    }
}
