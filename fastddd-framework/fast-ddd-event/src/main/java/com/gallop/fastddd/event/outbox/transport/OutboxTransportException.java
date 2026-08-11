package com.gallop.fastddd.event.outbox.transport;

import com.gallop.fastddd.event.outbox.OutboxEvent;

/**
 * Outbox 传输失败异常（EVT-003）。
 *
 * <p>由 {@link OutboxTransport#send} 在投递失败（含超时、连接异常、broker 拒绝）时抛出。
 * {@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher} 捕获后调
 * {@code OutboxEventService.markAsFailed}（重试耗尽自动入死信，见 EVT-002）。
 *
 * <p>携带 {@code eventId} 便于诊断；{@code timeout} 标记区分超时与一般失败（供运维指标分类）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class OutboxTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final boolean timeout;

    /**
     * 构造传输失败异常。
     *
     * @param event   失败的事件（取其 eventId 入诊断）
     * @param message 错误信息
     * @param cause   根因
     * @param timeout 是否为超时类失败
     */
    public OutboxTransportException(OutboxEvent event, String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.eventId = event != null ? event.getEventId() : null;
        this.timeout = timeout;
    }

    /** 便捷构造：非超时失败。 */
    public OutboxTransportException(OutboxEvent event, String message, Throwable cause) {
        this(event, message, cause, false);
    }

    /** 关联的 eventId（可能为 null）。 */
    public String getEventId() {
        return eventId;
    }

    /** 是否为超时类失败。 */
    public boolean isTimeout() {
        return timeout;
    }
}
