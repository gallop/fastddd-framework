package com.gallop.fastddd.event.outbox.dispatcher;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.transport.LocalOutboxTransport;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OutboxDispatcherConfiguration 正向装配测试（EVT-003 步骤7）。
 *
 * <p>默认 outbox 配置（transport=local、dispatcher 启用）下：LocalOutboxTransport 与
 * OutboxDispatcher 均装配为单例，守卫不触发——证明 happy-path 自动配置链路完整。
 * 与 {@code OutboxTransportFailureAnalyzerTest} 的缺传输失败用例互补。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class OutboxDispatcherWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OutboxDispatcherConfiguration.class))
            .withBean(OutboxEventService.class, () -> mock(OutboxEventService.class))
            .withBean(EventProperties.class, EventProperties::new)
            .withPropertyValues("fastddd.event.mode=outbox");  // 默认 transport=local、dispatcher 启用

    @Test
    void defaultOutboxConfig_wiresLocalTransportAndDispatcher() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxTransport.class);
            assertThat(context.getBean(OutboxTransport.class)).isInstanceOf(LocalOutboxTransport.class);
            assertThat(context).hasSingleBean(OutboxDispatcher.class);
        });
    }

    @Test
    void dispatcherDisabled_doesNotWireDispatcher() {
        runner.withPropertyValues("fastddd.event.mode=outbox",
                        "fastddd.event.outbox.dispatcher.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                });
    }
}
