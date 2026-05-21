package com.gallop.fastddd.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * FastDDD Monitor 自动配置入口
 *
 * 负责导入各监控子模块配置。当前仅包含 SQL 监控。
 *
 * @author gallop
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
public class FastDddMonitorAutoConfiguration {

    public FastDddMonitorAutoConfiguration() {
        log.info("FastDDD Monitor auto-configuration initialized");
    }
}
