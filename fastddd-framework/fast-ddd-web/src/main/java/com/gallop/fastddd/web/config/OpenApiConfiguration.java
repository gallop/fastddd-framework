package com.gallop.fastddd.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI和Knife4j自动配置类
 * 提供默认的API文档配置，支持通过配置文件自定义
 * 
 * @author gallop
 * @since 2025-01-15
 * @version 1.0.0-SNAPSHOT
 */
@Configuration
@ConditionalOnClass({OpenAPI.class})
@ConditionalOnProperty(name = "fastddd.web.openapi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfiguration {

    private final OpenApiProperties properties;

    public OpenApiConfiguration(OpenApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 默认OpenAPI配置
     */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI defaultOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .description(properties.getDescription())
                        .version(properties.getVersion())
                        .contact(new Contact()
                                .name(properties.getContact().getName())
                                .email(properties.getContact().getEmail())
                                .url(properties.getContact().getUrl()))
                        .license(new License()
                                .name(properties.getLicense().getName())
                                .url(properties.getLicense().getUrl())))
                .servers(List.of(
                        new Server().url(properties.getServer().getUrl())
                                .description(properties.getServer().getDescription())
                ));
    }

    /**
     * 默认API分组配置
     */
    @Bean
    @ConditionalOnMissingBean(GroupedOpenApi.class)
    public GroupedOpenApi defaultGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch("/**")
                .packagesToScan("com.gallop.fastddd")
                .build();
    }

    /**
     * FastDDD框架API分组
     */
    @Bean
    public GroupedOpenApi fastDddGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("fastddd-framework")
                .pathsToMatch("/api/**")
                .packagesToScan("com.gallop.fastddd")
                .build();
    }

    /**
     * 业务API分组
     */
    @Bean
    public GroupedOpenApi businessGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("business-api")
                .pathsToMatch("/business/**", "/app/**")
                .build();
    }
}