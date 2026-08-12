package com.gallop.fastddd.web.annotation;

import java.lang.annotation.*;

/**
 * 启用API响应包装器注解（已废弃）。
 *
 * <p>响应包装的唯一开关是属性 {@code fastddd.web.response-wrapper.enabled=true}
 * （门控施加在 {@code SmartResponseAdvice} 类级别的 {@code @ConditionalOnProperty}）。
 * 本注解保留为<b>纯源兼容标记</b>，不再具备任何装配语义——不 {@code @Import} 任何配置。
 *
 * <p>WEB-002：响应包装 opt-in 化。原 {@code @Import(ApiResponseWrapperConfiguration.class)}
 * 装配语义已移除（该配置类已删除）；属性成为唯一 opt-in，避免注解与属性双模式 registrar 的条件竞态。
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 * @deprecated 改用属性 {@code fastddd.web.response-wrapper.enabled=true} 作为唯一 opt-in。
 */
@Deprecated(since = "1.0.0-jdk21")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableApiResponseWrapper {
}
