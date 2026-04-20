package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Outbox事件配置类
 *
 * 提供Outbox模式相关的Bean配置。
 *
 * 注意：不要在此处定义 ObjectMapper Bean，应该使用 Spring Boot 自动配置的 ObjectMapper，
 * 这样才能保证 FastDDD 的 Jackson 自定义配置生效。
 *
 * @author gallop
 * @since 2025-01-09
 */
@Configuration
@MapperScan("com.gallop.fastddd.event.outbox")
public class OutboxEventConfiguration {

    /**
     * 配置MyBatis-Plus分页插件
     *
     * @return MybatisPlusInterceptor实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}