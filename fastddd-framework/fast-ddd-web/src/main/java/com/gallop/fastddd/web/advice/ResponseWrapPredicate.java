package com.gallop.fastddd.web.advice;

import com.gallop.fastddd.common.result.Result;
import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.Map;

/**
 * 响应包装判定谓词（WEB-002 Phase C 抽取）。
 *
 * <p>统一 {@link SmartResponseAdvice#supports}（运行时）与 {@code ApiResponseOpenApiCustomizer}
 * （OpenAPI 契约）两处的「是否应包装」判定，防止运行时与文档契约漂移。两处必须对同一端点给出
 * 相同结论，否则 OpenAPI 文档与实际响应体不一致。
 *
 * <p>排除规则（不包装）：
 * <ul>
 *   <li>void / Void 返回</li>
 *   <li>@ExceptionHandler / GlobalExceptionHandler 所在类</li>
 *   <li>{@link Result} / {@link ApiResponse}（已包装）</li>
 *   <li>{@link ResponseEntity}（含 {@code BaseReadOnlyController} 声明的 {@code ResponseEntity<ApiResponse<T>>}）</li>
 *   <li>Reactor Flux / Mono（SSE 流式）</li>
 * </ul>
 *
 * @author gallop
 * @since 2026-08-12
 * @version 1.0.0-SNAPSHOT
 */
public final class ResponseWrapPredicate {

    private ResponseWrapPredicate() {
    }

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

    /**
     * 判断给定返回类型是否应被 ApiResponse 信封包装。
     *
     * @param returnType 控制器方法返回类型（来自 {@code ResponseBodyAdvice.supports} 或 {@code HandlerMethod}）
     * @return true 表示运行时会被 {@link SmartResponseAdvice} 包装
     */
    public static boolean shouldWrap(MethodParameter returnType) {
        if (returnType == null) {
            return false;
        }

        Class<?> parameterType = returnType.getParameterType();
        if (parameterType == void.class || parameterType == Void.class) {
            return false;
        }

        String className = returnType.getContainingClass().getName();
        if (className.contains("ExceptionHandler") || className.contains("GlobalExceptionHandler")) {
            return false;
        }

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

    /**
     * 判断具体类型是否属于「需要包装」的类别（基本类型/集合/数组/自定义 POJO）。
     */
    private static boolean isNeedWrapping(Class<?> type) {
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

        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }

        if (type.isArray()) {
            return true;
        }

        // 自定义 POJO 类型（不在 java / javax / org.springframework 包下）
        if (!type.getName().startsWith("java.") &&
                !type.getName().startsWith("javax.") &&
                !type.getName().startsWith("org.springframework.")) {
            return true;
        }

        return false;
    }
}
