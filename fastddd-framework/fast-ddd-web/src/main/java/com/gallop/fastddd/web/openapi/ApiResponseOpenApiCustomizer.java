package com.gallop.fastddd.web.openapi;

import com.gallop.fastddd.web.advice.ResponseWrapPredicate;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * OpenAPI 响应信封 customizer（WEB-002 Phase C / 验收第 2 条）。
 *
 * <p>问题：springdoc 读方法签名，看不见 {@code ResponseBodyAdvice} 的运行时包装。当响应包装 opt-in
 * 开启时，运行时返回 {@link com.gallop.fastddd.web.response.ApiResponse} 信封，而 OpenAPI 文档仍声明
 * 原始 POJO 类型——契约不一致。
 *
 * <p>本 customizer 在 springdoc 构建每个 operation 时介入：对「运行时会被 {@code SmartResponseAdvice}
 * 包装」的端点（判定复用 {@link ResponseWrapPredicate#shouldWrap}，与运行时同源防漂移），将其 2xx
 * 响应的 JSON schema 包成 {@code ApiResponse} 信封 schema（code/message/data/timestamp）。
 *
 * <p>不应包装的端点（{@code BaseReadOnlyController} 已声明 {@code ResponseEntity<ApiResponse<T>>}、
 * 返回 {@code Result}、void、Flux/Mono、@ExceptionHandler）原样保留——天然自洽，避免二次包装。
 *
 * <p>门控：{@code @ConditionalOnProperty("fastddd.web.response-wrapper.enabled", havingValue="true")}——
 * 与响应包装同门控。包装关闭 → customizer 不注册 → OpenAPI schema = 原始类型（与运行时一致：都不包装）。
 *
 * @author gallop
 * @since 2026-08-12
 * @version 1.0.0-SNAPSHOT
 */
@Component
@ConditionalOnProperty(prefix = "fastddd.web.response-wrapper", name = "enabled", havingValue = "true")
public class ApiResponseOpenApiCustomizer implements OperationCustomizer {

    /** 成功响应状态码——仅包装这些 2xx 响应的信封。 */
    private static final String[] SUCCESS_CODES = {"200", "201"};

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation == null || handlerMethod == null) {
            return operation;
        }

        MethodParameter returnType = handlerMethod.getReturnType();
        if (!ResponseWrapPredicate.shouldWrap(returnType)) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return operation;
        }

        for (String code : SUCCESS_CODES) {
            ApiResponse response = responses.get(code);
            if (response != null) {
                wrapContent(response);
            }
        }

        return operation;
    }

    /**
     * 将响应体 JSON content schema 包成 ApiResponse 信封。
     */
    private void wrapContent(ApiResponse response) {
        Content content = response.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }

        for (Map.Entry<String, MediaType> entry : content.entrySet()) {
            MediaType mediaType = entry.getValue();
            if (mediaType == null) {
                continue;
            }
            Schema<?> originalSchema = mediaType.getSchema();
            if (originalSchema == null) {
                continue;
            }

            ObjectSchema envelope = new ObjectSchema();
            envelope.addProperties("code", new IntegerSchema().example(200));
            envelope.addProperties("message", new StringSchema().example("操作成功"));
            envelope.addProperties("data", originalSchema);
            envelope.addProperties("timestamp", new IntegerSchema().format("int64"));

            mediaType.setSchema(envelope);
        }
    }
}
