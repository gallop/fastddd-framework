package com.gallop.fastddd.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import com.gallop.fastddd.common.FastDddCommonAutoConfiguration;
import com.gallop.fastddd.config.FastDddConfigAutoConfiguration;
import com.gallop.fastddd.model.FastDddModelAutoConfiguration;
import com.gallop.fastddd.crud.FastDddCrudAutoConfiguration;
import com.gallop.fastddd.web.FastDddWebAutoConfiguration;

/**
 * FastDDD Starter模块的主要自动配置类
 * 
 * 该配置类导入所有其他FastDDD模块配置，
 * 通过单一依赖提供完整的框架设置。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@AutoConfiguration
@Import({
    FastDddCommonAutoConfiguration.class,
    FastDddConfigAutoConfiguration.class,
    FastDddModelAutoConfiguration.class,
    FastDddCrudAutoConfiguration.class,
    FastDddWebAutoConfiguration.class
})
public class FastDddStarterAutoConfiguration {
    
    // Starter orchestration will be implemented in subsequent tasks
    
}