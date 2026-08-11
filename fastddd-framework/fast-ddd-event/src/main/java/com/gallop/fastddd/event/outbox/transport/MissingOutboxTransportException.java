package com.gallop.fastddd.event.outbox.transport;

/**
 * outbox 模式下无可用 {@link OutboxTransport} 时抛出（EVT-003 步骤4）。
 *
 * <p>由 {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcherConfiguration} 的守卫 bean
 * 在 outbox 模式 + {@code OutboxEventService} 在场但容器内不存在任何 {@link OutboxTransport}
 * 时抛出，触发 {@link OutboxTransportFailureAnalyzer} 给出可读诊断——避免应用静默启动后
 * "只入库不投递"（ADR-003 §2.2 验收红线）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class MissingOutboxTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 用户配置的传输选择（local/rocketmq/自定义），用于诊断提示。 */
    private final String configuredTransport;

    public MissingOutboxTransportException(String configuredTransport) {
        super("outbox 模式下未装配任何 OutboxTransport：configured transport='" + configuredTransport + "'");
        this.configuredTransport = configuredTransport;
    }

    public String getConfiguredTransport() {
        return configuredTransport;
    }
}
