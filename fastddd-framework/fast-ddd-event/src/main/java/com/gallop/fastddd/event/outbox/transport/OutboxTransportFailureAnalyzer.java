package com.gallop.fastddd.event.outbox.transport;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * outbox 模式无传输时的启动失败诊断（EVT-003 步骤4 / ADR-003 §2.2）。
 *
 * <p>拦截 {@link MissingOutboxTransportException}，输出可读的行动指引，取代默认的裸堆栈——
 * 指引用户核对 {@code fastddd.event.outbox.transport} 取值与对应依赖/bean。
 *
 * <p>注册方式：{@code META-INF/spring/org.springframework.boot.diagnostics.FailureAnalyzer.imports}。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
public class OutboxTransportFailureAnalyzer extends AbstractFailureAnalyzer<MissingOutboxTransportException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingOutboxTransportException cause) {
        String transport = cause.getConfiguredTransport();
        return new FailureAnalysis(
                getDescription(transport),
                getAction(transport),
                cause);
    }

    private static String getDescription(String transport) {
        return "FastDDD 事件模块运行在 outbox 模式（fastddd.event.mode=outbox），"
                + "但容器内未装配任何 OutboxTransport（configured transport='" + transport + "'）。"
                + "此时领域事件会被写入 outbox 表却不会被投递，造成『只入库不投递』。";
    }

    private static String getAction(String transport) {
        if ("rocketmq".equalsIgnoreCase(transport)) {
            return "已选择 rocketmq 传输，请确认：\n"
                    + "  1) fastddd-event 的 RocketMQ 依赖（rocketmq-spring-boot-starter）在 classpath；\n"
                    + "  2) rocketmq.enabled=true；\n"
                    + "  3) RocketMQTemplate bean 可正常装配（nameserver/producer 配置正确）。\n"
                    + "或改用 fastddd.event.outbox.transport=local 走本地 ApplicationEventPublisher 投递。";
        }
        if ("local".equalsIgnoreCase(transport)) {
            return "默认 local 传输未能装配，通常因 ObjectMapper bean 缺失（极少见，Spring Boot 默认提供）。"
                    + "可显式提供 com.fasterxml.jackson.databind.ObjectMapper bean，"
                    + "或注册自定义 OutboxTransport 实现覆盖默认。";
        }
        return "自定义传输 '" + transport + "' 未注册为 bean。请提供实现 OutboxTransport 接口的 Spring bean，"
                + "或改用 fastddd.event.outbox.transport=local / rocketmq。";
    }
}
