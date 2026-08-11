package com.gallop.fastddd.monitor.sql;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 监控 Actuator Endpoint（MON-002）。
 *
 * <p>取代原 {@code SqlMonitoringController}（挂在 {@code /actuator/sql-monitoring/**} 的伪
 * {@code @RestController}——不在 Actuator 体系内，不受 {@code management.endpoints.web.exposure} 治理）。
 * 本类为真正的 {@link Endpoint}（id = {@code sqlmonitoring}），由 Actuator 的 exposure/权限体系统一治理：
 * <ul>
 *   <li>默认<b>不暴露</b>（Actuator 仅 {@code health} 默认暴露）——满足"未暴露 Endpoint 时不可访问"。</li>
 *   <li>消费者经 {@code management.endpoints.web.exposure.include=sqlmonitoring} 显式启用后，
 *       路径 {@code /actuator/sqlmonitoring} 可访问。</li>
 * </ul>
 *
 * <p>操作映射（Actuator {@code @Endpoint} 语义：每类操作各一）：
 * <ul>
 *   <li>{@link ReadOperation} GET {@code /actuator/sqlmonitoring} —— 统计 + 配置 + 健康的聚合视图。</li>
 *   <li>{@link WriteOperation} POST {@code /actuator/sqlmonitoring} —— 合并的配置变更，
 *       各参数可选（null 表示不变）：{@code enabled}/{@code prometheusEnabled}/{@code elkEnabled}/
 *       {@code slowQueryThreshold}/{@code reset}。</li>
 * </ul>
 *
 * <p>由 {@link SqlMonitoringAutoConfiguration} 注册为 {@code @Bean}，无需消费者组件扫描。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Endpoint(id = "sqlmonitoring")
@RequiredArgsConstructor
public class SqlMonitoringEndpoint {

    private final SqlMonitoringService sqlMonitoringService;
    private final SqlMonitoringProperties properties;

    /**
     * 聚合读：统计 + 配置 + 健康。
     */
    @ReadOperation
    public Map<String, Object> readAll() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("statistics", sqlMonitoringService.getStatistics());
        view.put("config", sqlMonitoringService.getMonitoringConfig());
        view.put("health", sqlMonitoringService.getHealthStatus());
        return view;
    }

    /**
     * 合并写：仅应用非 null 参数。返回更新后的配置视图。
     */
    @WriteOperation
    public Map<String, Object> configure(@Nullable Boolean enabled,
                                         @Nullable Boolean prometheusEnabled,
                                         @Nullable Boolean elkEnabled,
                                         @Nullable Long slowQueryThreshold,
                                         @Nullable Boolean reset) {
        if (reset != null && reset) {
            sqlMonitoringService.resetStatistics();
        }
        if (slowQueryThreshold != null && slowQueryThreshold > 0) {
            sqlMonitoringService.updateSlowQueryThreshold(slowQueryThreshold);
        }
        if (enabled != null) {
            sqlMonitoringService.setMonitoringEnabled(enabled);
        }
        if (prometheusEnabled != null) {
            sqlMonitoringService.setPrometheusEnabled(prometheusEnabled);
        }
        if (elkEnabled != null) {
            sqlMonitoringService.setElkEnabled(elkEnabled);
        }
        return sqlMonitoringService.getMonitoringConfig();
    }
}
