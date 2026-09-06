package com.gallop.fastddd.consumer.webcruddruid.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消费者自有 MP 插件配置（真实项目形态：分页拦截器由消费方装配；
 * 框架仅在 outbox 模式下经 OutboxEventConfiguration 提供 @ConditionalOnMissingBean 兜底）。
 */
@Configuration
public class ConsumerMybatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}
