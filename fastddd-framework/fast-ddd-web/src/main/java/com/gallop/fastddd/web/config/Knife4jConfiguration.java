package com.gallop.fastddd.web.config;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Knife4j增强配置类
 * 提供更丰富的API文档界面功能和自定义配置
 * 
 * @author gallop
 * @since 2025-01-15
 * @version 1.0.0-SNAPSHOT
 */
@Configuration
@EnableKnife4j
@ConditionalOnClass(name = "com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j")
@ConditionalOnProperty(name = "fastddd.web.knife4j.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Knife4jProperties.class)
public class Knife4jConfiguration implements WebMvcConfigurer {

    private final Knife4jProperties properties;

    public Knife4jConfiguration(Knife4jProperties properties) {
        this.properties = properties;
    }

    /**
     * 配置静态资源处理
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (properties.isEnabled()) {
            registry.addResourceHandler("/doc.html**")
                    .addResourceLocations("classpath:/META-INF/resources/");
            registry.addResourceHandler("/webjars/**")
                    .addResourceLocations("classpath:/META-INF/resources/webjars/");
        }
    }

    /**
     * Knife4j增强配置Bean
     */
    @Bean
    public Knife4jEnhanceConfiguration knife4jEnhanceConfiguration() {
        return new Knife4jEnhanceConfiguration(properties);
    }

    /**
     * Knife4j增强配置内部类
     */
    public static class Knife4jEnhanceConfiguration {
        private final Knife4jProperties properties;

        public Knife4jEnhanceConfiguration(Knife4jProperties properties) {
            this.properties = properties;
        }

        /**
         * 获取基础配置
         */
        public Knife4jProperties getProperties() {
            return properties;
        }

        /**
         * 是否启用增强功能
         */
        public boolean isEnhanceEnabled() {
            return properties.isEnhance();
        }

        /**
         * 是否启用生产环境屏蔽
         */
        public boolean isProductionEnabled() {
            return properties.isProduction();
        }

        /**
         * 获取基础认证配置
         */
        public Knife4jProperties.Basic getBasicAuth() {
            return properties.getBasic();
        }
    }
}