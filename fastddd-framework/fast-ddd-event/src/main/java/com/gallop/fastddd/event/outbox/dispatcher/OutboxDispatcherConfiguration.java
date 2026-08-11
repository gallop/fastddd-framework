package com.gallop.fastddd.event.outbox.dispatcher;

import com.gallop.fastddd.event.EventProperties;
import com.gallop.fastddd.event.outbox.OutboxEventConfiguration;
import com.gallop.fastddd.event.outbox.OutboxEventService;
import com.gallop.fastddd.event.outbox.transport.LocalOutboxTransport;
import com.gallop.fastddd.event.outbox.transport.MissingOutboxTransportException;
import com.gallop.fastddd.event.outbox.transport.OutboxTransport;
import com.gallop.fastddd.event.rocketmq.RocketMQEventAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Outbox Dispatcher 自动配置（EVT-003 / ADR-003 §2.1）。
 *
 * <p>仅 <b>outbox 模式</b>（{@code fastddd.event.mode=outbox}）且 {@link OutboxEventService} bean
 * 在场时装配——把传输与调度纳入 Spring 容器：
 * <ul>
 *   <li>{@link LocalOutboxTransport}：默认传输（{@code fastddd.event.outbox.transport=local}）。
 *       业务/模块提供自定义 {@link OutboxTransport} bean 时整体退让；
 *       {@code transport=rocketmq} 时由 {@code RocketMQEventAutoConfiguration} 提供
 *       {@code RocketMqOutboxTransport}。</li>
 *   <li>{@link OutboxDispatcher}：编程式调度（JDK 守护线程池），周期取自
 *       {@link EventProperties.Outbox#getDispatcher()}。</li>
 * </ul>
 *
 * <p><b>无传输即启动失败</b>：outbox 模式下若容器内不存在任何 {@link OutboxTransport} bean
 * （既无 local 默认、又无 rocketmq，且无自定义实现），本配置不装配 dispatcher，
 * 上下文最终因缺 OutboxDispatcher / 缺 OutboxTransport 由
 * {@code OutboxTransportFailureAnalyzer}（EVT-003 步骤4）给出可读诊断。
 *
 * <p>调度不需要 Spring 的 @EnableScheduling（Dispatcher 自带 ScheduledExecutorService）；
 * 原 EventAutoConfiguration 上的全局 @EnableScheduling 随 EVT-003 移除（local 模式无需调度）。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@AutoConfiguration(after = {OutboxEventConfiguration.class, RocketMQEventAutoConfiguration.class})
@ConditionalOnProperty(prefix = "fastddd.event", name = "mode", havingValue = "outbox")
@ConditionalOnBean(OutboxEventService.class)
public class OutboxDispatcherConfiguration {

    /**
     * 默认本地传输（{@code transport=local} 或缺省）。业务方提供自定义 {@link OutboxTransport}
     * bean 或选择 {@code rocketmq} 传输时退让。
     */
    @Bean
    @ConditionalOnMissingBean(OutboxTransport.class)
    @ConditionalOnProperty(prefix = "fastddd.event.outbox", name = "transport",
            havingValue = "local", matchIfMissing = true)
    public OutboxTransport localOutboxTransport(ApplicationEventPublisher applicationEventPublisher) {
        return new LocalOutboxTransport(applicationEventPublisher);
    }

    /**
     * Outbox 调度器（仅在存在 {@link OutboxTransport} bean 且 dispatcher.enabled=true 时装配）。
     * 无传输则不启动调度，由下方守卫 bean 诊断，避免"静默只入库不投递"。
     */
    @Bean
    @ConditionalOnMissingBean(OutboxDispatcher.class)
    @ConditionalOnBean(OutboxTransport.class)
    @ConditionalOnProperty(prefix = "fastddd.event.outbox.dispatcher", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public OutboxDispatcher outboxDispatcher(OutboxEventService outboxEventService,
                                             OutboxTransport outboxTransport,
                                             EventProperties eventProperties) {
        return new OutboxDispatcher(outboxEventService, outboxTransport, eventProperties);
    }

    /**
     * 守卫 bean（EVT-003 步骤4）：outbox 模式 + dispatcher 启用 + OutboxEventService 在场但容器内
     * <b>无任何</b> {@link OutboxTransport} bean 时触发——抛 {@link MissingOutboxTransportException}
     * 使上下文启动失败，由 {@code OutboxTransportFailureAnalyzer} 输出可读诊断。
     *
     * <p>本配置在 {@code OutboxEventConfiguration} 与 {@code RocketMQEventAutoConfiguration}
     * 之后求值，故 @ConditionalOnMissingBean 此时能见到两者可能注册的全部传输——保证判定可靠。
     * 抛出异常即终止 bean 创建（方法不实际返回），不会产生一个“永远用不到”的传输 bean。
     * 用户显式 {@code dispatcher.enabled=false} 接管投递时不触发本守卫。
     */
    @Bean
    @ConditionalOnMissingBean(OutboxTransport.class)
    @ConditionalOnProperty(prefix = "fastddd.event.outbox.dispatcher", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public OutboxTransport outboxTransportGuard(EventProperties eventProperties) {
        throw new MissingOutboxTransportException(eventProperties.getOutbox().getTransport());
    }
}
