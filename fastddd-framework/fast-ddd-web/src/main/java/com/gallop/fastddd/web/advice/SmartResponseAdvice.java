package com.gallop.fastddd.web.advice;

import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 智能响应包装器
 * 只包装原始数据类型，让已包装的响应类型（Result、ApiResponse、ResponseEntity）直接返回
 *
 * <p>WEB-002 opt-in 门控：本类带 {@code @ControllerAdvice}（Spring 仅探测标注此注解的
 * {@code ResponseBodyAdvice} bean），故会被任意组件扫描直接注册。为使其「仅引入 fast-ddd-web
 * 时不改写消费者 JSON」，门控施加在<b>类级别</b>——所有组件扫描均求值 {@link ConditionalOnProperty}，
 * 仅当 {@code fastddd.web.response-wrapper.enabled=true} 时才注册本 bean（默认关）。
 */
@ConditionalOnProperty(prefix = "fastddd.web.response-wrapper", name = "enabled", havingValue = "true")
@org.springframework.web.bind.annotation.ControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class SmartResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // WEB-002 Phase C：判定逻辑抽取到 ResponseWrapPredicate，与 OpenAPI customizer 共享，防契约漂移。
        return ResponseWrapPredicate.shouldWrap(returnType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // 如果body已经是包装类型，直接返回
        if (body instanceof Result || body instanceof ApiResponse || body instanceof ResponseEntity) {
            return body;
        }

        // 包装原始数据
        return ApiResponse.success(body);
    }
}