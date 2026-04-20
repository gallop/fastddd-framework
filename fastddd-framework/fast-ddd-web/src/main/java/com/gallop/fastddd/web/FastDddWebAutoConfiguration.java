package com.gallop.fastddd.web;

import com.gallop.fastddd.fastdoc.FastDocExportService;
import com.gallop.fastddd.fastdoc.FastDocService;
import com.gallop.fastddd.fastdoc.FastDocTemplateService;
import com.gallop.fastddd.web.config.ApiResponseWrapperConfiguration;
import com.gallop.fastddd.web.config.WebMvcConfig;
import com.gallop.fastddd.web.config.OpenApiConfiguration;
import com.gallop.fastddd.web.config.Knife4jConfiguration;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * FastDDD Web模块自动配置类
 * 自动配置Web相关的组件，包括全局异常处理器、OpenAPI和Knife4j文档
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@AutoConfiguration
@ComponentScan("com.gallop.fastddd.web")
@Import({ApiResponseWrapperConfiguration.class, WebMvcConfig.class,
         OpenApiConfiguration.class, Knife4jConfiguration.class})
public class FastDddWebAutoConfiguration {

    public FastDddWebAutoConfiguration() {
        log.info("=== FastDddWebAutoConfiguration initialized ===");
    }

    /**
     * 全局异常处理器
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        log.info("=== Creating GlobalExceptionHandler ===");
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public FastDocService fastDocService(DataSource dataSource) {
        return new FastDocService(dataSource);
    }

    @Bean
    @ConditionalOnBean(FastDocService.class)
    public FastDocTemplateService fastDocTemplateService() {
        return new FastDocTemplateService();
    }

    @Bean
    @ConditionalOnBean(FastDocService.class)
    public FastDocExportService fastDocExportService(FastDocService fastDocService, FastDocTemplateService fastDocTemplateService) {
        return new FastDocExportService(fastDocService, fastDocTemplateService);
    }
}
