package com.gallop.fastddd.web.annotation;

import com.gallop.fastddd.web.config.ApiResponseWrapperConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用API响应包装器注解
 * 使用此注解可以自动启用ApiResponse包装功能
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ApiResponseWrapperConfiguration.class)
public @interface EnableApiResponseWrapper {
}