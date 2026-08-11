package com.gallop.fastddd.event.outbox.transport;

import com.gallop.fastddd.event.outbox.OutboxEvent;

/**
 * Outbox 传输 SPI（EVT-003 / ADR-003 §2.1）。
 *
 * <p>传输与调度分离：{@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher} 负责
 * claim / lease / 状态转换 / 重试 / 死信（操作 outbox 表）；本 SPI 仅负责把单个已落库的
 * {@link OutboxEvent} 投递到外部消息系统，<b>不扫描 outbox 表、不创建存储、不改事件状态</b>。
 *
 * <p>1.0 GA 内置实现：
 * <ul>
 *   <li>{@link LocalOutboxTransport}——基于 Spring {@code ApplicationEventPublisher}，
 *       {@code fastddd.event.outbox.transport=local}（默认）。</li>
 *   <li>{@code RocketMqOutboxTransport}——基于 RocketMQ {@code RocketMQTemplate}，
 *       {@code fastddd.event.outbox.transport=rocketmq}（需 RocketMQ 在 classpath）。</li>
 * </ul>
 * 业务方/模块可提供自定义实现注册为 bean，覆盖默认。
 *
 * <p><b>实现契约</b>：
 * <ul>
 *   <li>{@link #send} 在投递失败（含超时）时抛 {@link OutboxTransportException}，
 *       由 Dispatcher 捕获后调 {@code markAsFailed}（重试耗尽自动入死信，见 EVT-002）。</li>
 *   <li>投递成功正常返回（不抛异常）。</li>
 *   <li>实现应尽量幂等——同一事件可能因 at-least-once 语义被重复投递，
 *       下游消费者须自行去重（以 {@code eventId} 为幂等键）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public interface OutboxTransport {

    /**
     * 投递单个 outbox 事件到外部消息系统。
     *
     * @param event 已落库的 outbox 事件（非 null）
     * @throws OutboxTransportException 投递失败（含超时、连接异常、broker 拒绝等）
     */
    void send(OutboxEvent event) throws OutboxTransportException;
}
