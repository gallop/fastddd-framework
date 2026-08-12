package com.gallop.fastddd.web;

import com.gallop.fastddd.web.config.WebMvcConfig;
import com.gallop.fastddd.web.config.OpenApiConfiguration;
import com.gallop.fastddd.web.config.Knife4jConfiguration;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * FastDDD Web模块自动配置类
 * 自动配置Web相关的组件，包括全局异常处理器、OpenAPI和Knife4j文档
 *
 * <p>SPLIT-002：FastDoc 控制器与自动配置已上移至 Tooling 层
 * fastddd-fastdoc-spring-boot-starter，本类不再扫描/装配 fastdoc 包，也不再持有
 * fastddd-fastdoc-core 反向依赖。原 excludeFilters 与嵌套 FastDocConfiguration 已移除。
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "com.gallop.fastddd.web")
@Import({WebMvcConfig.class,
         OpenApiConfiguration.class, Knife4jConfiguration.class})
public class FastDddWebAutoConfiguration {

    public FastDddWebAutoConfiguration() {
        log.info("=== FastDddWebAutoConfiguration initialized ===");
    }

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        log.info("=== Creating GlobalExceptionHandler ===");
        return new GlobalExceptionHandler();
    }
}
