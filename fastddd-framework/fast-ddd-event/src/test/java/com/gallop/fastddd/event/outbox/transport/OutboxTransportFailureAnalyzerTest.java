package com.gallop.fastddd.event.outbox.transport;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcherConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OutboxTransportFailureAnalyzer 测试（EVT-003 步骤4 / 步骤7）。
 *
 * <p>双覆盖：
 * <ol>
 *   <li>诊断内容：对 local / rocketmq / 自定义 三种 configured-transport 给出可读的描述与行动指引。</li>
 *   <li>装配守卫：outbox 模式 + OutboxEventService 在场 + 选 rocketmq 传输但无 RocketMQTemplate，
 *       容器启动失败，根因为 {@link MissingOutboxTransportException}（被 FailureAnalyzer 接管）。</li>
 * </ol>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class OutboxTransportFailureAnalyzerTest {

    private final OutboxTransportFailureAnalyzer analyzer = new OutboxTransportFailureAnalyzer();

    @Test
    void analyzer_rocketmuxMissingTransport_suggestsDepsAndLocalFallback() {
        FailureAnalysis analysis = analyzer.analyze(new MissingOutboxTransportException("rocketmq"));

        assertThat(analysis.getDescription()).contains("只入库不投递");
        assertThat(analysis.getAction())
                .contains("rocketmq-spring-boot-starter")
                .contains("rocketmq.enabled=true")
                .contains("fastddd.event.outbox.transport=local");
    }

    @Test
    void analyzer_localTransport_suggestsObjectMapperOrCustom() {
        FailureAnalysis analysis = analyzer.analyze(new MissingOutboxTransportException("local"));

        assertThat(analysis.getAction())
                .contains("ObjectMapper")
                .contains("OutboxTransport");
    }

    @Test
    void analyzer_customTransport_suggestsRegisterBean() {
        FailureAnalysis analysis = analyzer.analyze(new MissingOutboxTransportException("kafka"));

        assertThat(analysis.getAction())
                .contains("kafka")
                .contains("OutboxTransport 接口");
    }

    @Test
    void context_missingTransport_failsWithMissingOutboxTransportException() {
        // outbox 模式 + OutboxEventService 在场 + 选 rocketmq 传输，但无 RocketMQTemplate/依赖 → 无任何传输 → 守卫抛
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(OutboxDispatcherConfiguration.class))
                .withBean(OutboxEventService.class, () -> mock(OutboxEventService.class))
                .withBean(EventProperties.class, EventProperties::new)
                .withPropertyValues(
                        "fastddd.event.mode=outbox",
                        "fastddd.event.outbox.transport=rocketmq")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    // 守卫抛的 MissingOutboxTransportException 在 BeanCreationException 的 cause 链中
                    assertThat(rootCause(failure)).isInstanceOf(MissingOutboxTransportException.class);
                });
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
