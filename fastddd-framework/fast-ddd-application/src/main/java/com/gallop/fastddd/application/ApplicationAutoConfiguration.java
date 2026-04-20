package com.gallop.fastddd.application;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Application 层自动配置
 *
 * 自动配置应用层相关组件，包括：
 * <ul>
 *   <li>@ApplicationService 注解扫描</li>
 *   <li>Command/Query 处理器注册</li>
 *   <li>CQRS 相关组件</li>
 * </ul>
 *
 * <p>通过在 classpath 中存在此模块，自动配置会自动启用。
 *
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@AutoConfiguration
@ComponentScan(basePackages = {
        "com.gallop.fastddd.application.annotation",
        "com.gallop.fastddd.application.command",
        "com.gallop.fastddd.application.query"
})
@EnableConfigurationProperties
public class ApplicationAutoConfiguration {

    public ApplicationAutoConfiguration() {
        // 自动配置构造器
        // 实际配置通过 @ComponentScan 自动发现组件
    }
}
