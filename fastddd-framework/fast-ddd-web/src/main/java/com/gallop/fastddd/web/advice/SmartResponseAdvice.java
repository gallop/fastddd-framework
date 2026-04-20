package com.gallop.fastddd.web.advice;

import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 智能响应包装器
 * 只包装原始数据类型，让已包装的响应类型（Result、ApiResponse、ResponseEntity）直接返回
 */
@org.springframework.web.bind.annotation.ControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class SmartResponseAdvice implements ResponseBodyAdvice<Object> {

    // 缓存 Reactor 类型检查结果，避免每个请求重复 Class.forName
    private static final Class<?> FLUX_CLASS;
    private static final Class<?> MONO_CLASS;

    static {
        Class<?> flux = null, mono = null;
        try {
            flux = Class.forName("reactor.core.publisher.Flux");
            mono = Class.forName("reactor.core.publisher.Mono");
        } catch (ClassNotFoundException ignored) {
        }
        FLUX_CLASS = flux;
        MONO_CLASS = mono;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.getParameterType() == void.class || returnType.getParameterType() == Void.class) {
            return false;
        }

        String className = returnType.getContainingClass().getName();
        if (className.contains("ExceptionHandler") || className.contains("GlobalExceptionHandler")) {
            return false;
        }

        Class<?> parameterType = returnType.getParameterType();
        String typeName = parameterType.getName();

        if (Result.class.isAssignableFrom(parameterType) || typeName.startsWith("com.gallop.fastddd.common.result.Result")) {
            return false;
        }
        if (ApiResponse.class.isAssignableFrom(parameterType) || typeName.startsWith("com.gallop.fastddd.web.response.ApiResponse")) {
            return false;
        }
        if (ResponseEntity.class.isAssignableFrom(parameterType)) {
            return false;
        }

        // 排除 Reactive 类型（Flux/Mono）— 用于 SSE 流式端点
        if (FLUX_CLASS != null && FLUX_CLASS.isAssignableFrom(parameterType)) {
            return false;
        }
        if (MONO_CLASS != null && MONO_CLASS.isAssignableFrom(parameterType)) {
            return false;
        }

        return isNeedWrapping(parameterType);
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
    
    /**
     * 判断是否需要包装
     */
    private boolean isNeedWrapping(Class<?> type) {
        // 基本类型和包装类型
        if (type.isPrimitive() || 
            type == String.class ||
            type == Boolean.class ||
            type == Byte.class ||
            type == Short.class ||
            type == Integer.class ||
            type == Long.class ||
            type == Float.class ||
            type == Double.class ||
            type == Character.class) {
            return true;
        }
        
        // 集合类型
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        
        // 数组类型
        if (type.isArray()) {
            return true;
        }
        
        // 自定义POJO类型（不在java包下的类）
        if (!type.getName().startsWith("java.") && 
            !type.getName().startsWith("javax.") &&
            !type.getName().startsWith("org.springframework.")) {
            return true;
        }
        
        return false;
    }
}