package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

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
@Slf4j
@Configuration
@MapperScan("com.gallop.fastddd.event.outbox")
public class OutboxEventConfiguration {

    /**
     * 配置MyBatis-Plus分页插件，自动检测数据库类型
     *
     * @param dataSource 数据源，用于推断 DbType
     * @return MybatisPlusInterceptor实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = null;
        try (var conn = dataSource.getConnection()) {
            dbType = JdbcUtils.getDbType(conn.getMetaData().getURL());
        } catch (Exception e) {
            log.warn("[Outbox] Failed to detect database type, falling back to default: {}", e.getMessage());
        }
        interceptor.addInnerInterceptor(dbType != null
                ? new PaginationInnerInterceptor(dbType)
                : new PaginationInnerInterceptor());
        return interceptor;
    }
}