package com.gallop.fastddd.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 事件模块配置属性。
 *
 * <p>EVT-001：显式 local/outbox 模式开关。此前 EventAutoConfiguration 通过宽
 * {@code @ComponentScan("com.gallop.fastddd.event")} 无条件拾取 outbox 子包，
 * 导致 local 场景（无 DataSource）下 OutboxEventServiceImpl 因缺 ObjectMapper/DataSource
 * 装配失败。现以 {@code fastddd.event.mode} 显式门禁：仅 {@code outbox} 模式且依赖满足
 * （DataSource + MyBatis 在场）时才装配 Outbox 链路，local 模式（默认）零 Outbox Bean。
 *
 * <p>EVT-003：新增 {@code fastddd.event.outbox.*} 子节，统一 Outbox 传输选择与 Dispatcher
 * 调度参数（批次/间隔/最大重试/租约超时/退避基数），取代此前散落在 {@code rocketmq.publisher.*}
 * 的传输无关调度配置（ADR-003 §1：配置命名空间混乱）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Data
@ConfigurationProperties(prefix = "fastddd.event")
public class EventProperties {

    /**
     * 事件模式：
     * <ul>
     *   <li>{@code local}（默认）：基于 Spring {@link org.springframework.context.ApplicationEventPublisher}
     *       的本地事件发布，无数据库依赖。</li>
     *   <li>{@code outbox}：事务性 Outbox 持久化，需 DataSource + MyBatis 在场。</li>
     * </ul>
     */
    private String mode = "local";

    /**
     * Outbox 子配置（仅 outbox 模式生效）。
     */
    private Outbox outbox = new Outbox();

    @Data
    public static class Outbox {

        /**
         * 传输实现选择（ADR-003 §4）：
         * <ul>
         *   <li>{@code local}（默认）：{@link com.gallop.fastddd.event.outbox.transport.LocalOutboxTransport}
         *       ——基于 Spring ApplicationEventPublisher，无需外部 broker。</li>
         *   <li>{@code rocketmq}：{@code RocketMqOutboxTransport}——需 RocketMQ 在 classpath
         *       且 {@code RocketMQTemplate} bean 在场；否则启动失败（见 OutboxTransportFailureAnalyzer）。</li>
         * </ul>
         * 业务方提供自定义 {@link com.gallop.fastddd.event.outbox.transport.OutboxTransport} bean
         * 时覆盖默认。
         */
        private String transport = "local";

        /**
         * Dispatcher 调度参数。
         */
        private Dispatcher dispatcher = new Dispatcher();
    }

    @Data
    public static class Dispatcher {

        /**
         * 是否启用框架内置的自动投递调度（{@link com.gallop.fastddd.event.outbox.dispatcher.OutboxDispatcher}）。
         *
         * <p>默认 {@code true}——outbox 模式下框架自动周期投递，满足 ADR-003 "不再只入库不投递"。
         * 置 {@code false} 适用于：业务方自带独立 dispatcher、或仅需 outbox 持久化（写侧）而由
         * 外部流程排空。关闭后框架不投递、不回收租约，{@code OutboxTransportFailureAnalyzer}
         * 也不再对缺传输报错（用户显式接管投递）。
         */
        private boolean enabled = true;

        /**
         * 单次 claim 批次大小（每轮调度抢占的事件上限）。
         */
        private int batchSize = 100;

        /**
         * 待处理事件轮询间隔。
         */
        private Duration pollInterval = Duration.ofSeconds(5);

        /**
         * 失败事件重试轮询间隔。
         */
        private Duration retryInterval = Duration.ofSeconds(30);

        /**
         * 租约超时：事件被 claim（置 PROCESSING）后若超过此时长仍未 ack，
         * 视为 dispatcher 崩溃，reclaim 回 PENDING 重新投递（ADR-003 §2.2）。
         */
        private Duration leaseTimeout = Duration.ofMinutes(2);

        /**
         * 租约回收轮询间隔（扫描过期 PROCESSING）。
         */
        private Duration reclaimInterval = Duration.ofSeconds(60);
    }
}
