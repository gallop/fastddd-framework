package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Outbox 事件自动配置类。
 *
 * <p>EVT-002：由 {@code @Configuration}（@Import 拾取）改为独立的 {@link AutoConfiguration @AutoConfiguration}，
 * 经 {@code AutoConfiguration.imports} 注册并在 {@link DataSourceAutoConfiguration} 之后求值——
 * 修复类级 {@code @ConditionalOnBean(DataSource)} 在 @Import 普通配置上求值时机不可靠
 * （Spring Boot 文档：@ConditionalOnBean 仅应用于自动配置类）导致 outbox 正向装配（DataSource 已在场）
 * 仍整体退让的缺陷。EVT-001 的装配边界门禁语义（local 默认退让、outbox 无 DataSource 退让）保持不变。
 *
 * <p>装配条件：仅当 <b>显式 outbox 模式</b>（{@code fastddd.event.mode=outbox}）且依赖满足
 * （{@link DataSource} + MyBatis-Plus 在场）时装配。local 模式（默认）整体退让——
 * 无 OutboxEventConfiguration、无 @MapperScan、无 OutboxEventRepository/Service、
 * 无 TransactionalEventPublisher。
 *
 * <p>注意：不要在此处定义 ObjectMapper Bean，应该使用 Spring Boot 自动配置的 ObjectMapper，
 * 这样才能保证 FastDDD 的 Jackson 自定义配置生效。
 *
 * <p><b>@MapperScan 边界</b>（EVT-002）：{@code markerInterface = BaseMapper.class} 限定仅注册
 * MyBatis-Plus {@code BaseMapper} 子接口（如 {@link OutboxEventRepository}）——否则
 * {@code com.gallop.fastddd.event.outbox} 包内的非 Mapper 接口 {@link OutboxEventService}
 * 会被 {@code @MapperScan} 当作 Mapper 注册为 bean {@code outboxEventService}，与下方
 * {@code @Bean outboxEventService()} 方法同名冲突（ConflictingBeanDefinitionException）。
 *
 * @author gallop
 * @since 2025-01-09
 */
@Slf4j
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@MapperScan(basePackages = "com.gallop.fastddd.event.outbox", markerInterface = BaseMapper.class)
@ConditionalOnProperty(prefix = "fastddd.event", name = "mode", havingValue = "outbox")
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnBean(DataSource.class)
public class OutboxEventConfiguration {

    /**
     * 配置MyBatis-Plus插件链：乐观锁 + 分页。
     *
     * <p>EVT-002：补充 {@link OptimisticLockerInnerInterceptor}——{@link OutboxEvent#version}
     * 带 {@code @Version}，{@code updateById} 走乐观锁改写 SQL，缺此拦截器会以
     * {@code BindingException: Parameter 'MP_OPTLOCK_VERSION_ORIGINAL' not found} 失败。
     * 必须先于分页拦截器添加（MyBatis-Plus 要求 OptimisticLocker 在链首）。
     *
     * @param dataSource 数据源，用于推断 DbType
     * @return MybatisPlusInterceptor实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataSource dataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁拦截器须位于链首
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
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
