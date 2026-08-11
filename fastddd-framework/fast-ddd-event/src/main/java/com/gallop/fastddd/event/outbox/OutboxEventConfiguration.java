package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Outbox 事件配置类。
 *
 * <p>EVT-001 装配边界门禁：仅当 <b>显式 outbox 模式</b>（{@code fastddd.event.mode=outbox}）
 * 且依赖满足（{@link DataSource} + MyBatis-Plus 在场）时装配。local 模式（默认）整体退让——
 * 无 OutboxEventConfiguration、无 @MapperScan、无 OutboxEventRepository/Service、
 * 无 TransactionalEventPublisher。
 *
 * <p>此前此类由 EventAutoConfiguration 的宽 {@code @ComponentScan} 无条件拾取，
 * 导致 local 场景（无 DataSource）下 Outbox 链路被强制装配而失败。
 *
 * <p>注意：不要在此处定义 ObjectMapper Bean，应该使用 Spring Boot 自动配置的 ObjectMapper，
 * 这样才能保证 FastDDD 的 Jackson 自定义配置生效。
 *
 * @author gallop
 * @since 2025-01-09
 */
@Slf4j
@Configuration
@MapperScan("com.gallop.fastddd.event.outbox")
@ConditionalOnProperty(prefix = "fastddd.event", name = "mode", havingValue = "outbox")
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnBean(DataSource.class)
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

    /**
     * Outbox 事件服务（outbox 模式装配）。
     *
     * <p>EVT-001：显式 @Bean 声明，替代 OutboxEventServiceImpl 上原 @Service 刻板注解
     * （原注解依赖宽 ComponentScan 兜底，已被移除）。
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxEventService outboxEventService(OutboxEventRepository outboxEventRepository,
                                                 ObjectMapper objectMapper) {
        return new OutboxEventServiceImpl(outboxEventRepository, objectMapper);
    }

    /**
     * 事务性事件发布器（outbox 模式装配）。
     *
     * <p>仅 outbox 模式下作为 {@link EventPublisher} 提供；local 模式由
     * EventAutoConfiguration 的 springEventPublisher 兜底。
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher transactionalEventPublisher(OutboxEventRepository outboxEventRepository,
                                                     ObjectMapper objectMapper) {
        return new TransactionalEventPublisher(outboxEventRepository, objectMapper);
    }
}
