package com.gallop.fastddd.crud;

import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.gallop.fastddd.crud.handler.AutoFillMetaObjectHandler;
import com.gallop.fastddd.crud.injector.FastDddSqlInjector;
import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.crud.provider.DefaultCurrentUserProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * FastDDD CRUD模块的自动配置。
 * 为MetaObjectHandler和CurrentUserProvider提供自动配置。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.gallop.fastddd.crud")
public class FastDddCrudAutoConfiguration {
    
    /**
     * Provides a default CurrentUserProvider implementation if none is defined.
     * Applications can override this by providing their own CurrentUserProvider bean.
     * 
     * @return DefaultCurrentUserProvider instance
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider defaultCurrentUserProvider() {
        return new DefaultCurrentUserProvider();
    }
    
    /**
     * Provides AutoFillMetaObjectHandler for automatic field population.
     * This handler automatically fills createTime, updateTime, createUser, and updateUser
     * fields during MyBatis Plus insert and update operations.
     * 
     * @param currentUserProvider the current user provider
     * @return AutoFillMetaObjectHandler instance
     */
    @Bean
    @ConditionalOnMissingBean(AutoFillMetaObjectHandler.class)
    public AutoFillMetaObjectHandler autoFillMetaObjectHandler(CurrentUserProvider currentUserProvider) {
        return new AutoFillMetaObjectHandler(currentUserProvider);
    }
    
    /**
     * Provides custom SQL injector for FastDDD CRUD operations.
     * This injector adds batch insert and batch update methods to MyBatis Plus mappers.
     * 
     * @return FastDddSqlInjector instance
     */
    @Bean
    @ConditionalOnMissingBean(ISqlInjector.class)
    public ISqlInjector fastDddSqlInjector() {
        return new FastDddSqlInjector();
    }

}