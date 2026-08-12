package com.gallop.fastddd.web.openapi;

import com.gallop.fastddd.web.FastDddWebAutoConfiguration;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WEB-002 Phase C / 验收第 2 条：{@link ApiResponseOpenApiCustomizer} 契约与门控测试。
 *
 * <p>本类直接构造 Operation + HandlerMethod 调用 {@code customize()}，对「应包装」「不应二次包装」
 * 「无 responses 容错」精确断言——customizer 契约的确定性证明，不依赖 springdoc 全栈。
 * springdoc 端到端集成见 {@link ApiResponseOpenApiCustomizerSpringdocTest}。
 *
 * @author gallop
 * @since 2026-08-12
 */
class ApiResponseOpenApiCustomizerTest {

    private static final String DEMO_POJO_REF = "#/components/schemas/DemoPojo";
    private static final String APIRESPONSE_STRING_REF = "#/components/schemas/ApiResponseString";

    private final ApiResponseOpenApiCustomizer customizer = new ApiResponseOpenApiCustomizer();

    /** 构造一个模拟 springdoc 输出的 operation：200 响应 + $ref schema。 */
    private static Operation operationWithSchema(String ref) {
        Schema<?> schema = new Schema<Object>().$ref(ref);
        Content content = new Content().addMediaType("*/*", new MediaType().schema(schema));
        return new Operation().responses(
                new ApiResponses().addApiResponse("200", new ApiResponse().content(content)));
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = OpenApiDemoController.class.getMethod(methodName);
        return new HandlerMethod(new OpenApiDemoController(), method);
    }

    /** 验收第 2 条：POJO 端点 schema 被包成 ApiResponse 信封，data 指向原 POJO。 */
    @Test
    void shouldWrapPojoEndpointIntoEnvelope() throws Exception {
        Operation op = operationWithSchema(DEMO_POJO_REF);
        Operation result = customizer.customize(op, handler("pojo"));

        Schema<?> schema = result.getResponses().get("200").getContent().get("*/*").getSchema();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getProperties()).containsKeys("code", "message", "data", "timestamp");
        Schema<?> dataSchema = (Schema<?>) schema.getProperties().get("data");
        assertThat(dataSchema.get$ref()).isEqualTo(DEMO_POJO_REF);
    }

    /** ResponseEntity<ApiResponse<T>> 端点不被二次包装：predicate 排除 ResponseEntity。 */
    @Test
    void shouldNotDoubleWrapResponseEntityEndpoint() throws Exception {
        Operation op = operationWithSchema(APIRESPONSE_STRING_REF);
        Operation result = customizer.customize(op, handler("entity"));

        Schema<?> schema = result.getResponses().get("200").getContent().get("*/*").getSchema();
        assertThat(schema.get$ref()).as("应保留原 $ref，未被替换为内联信封").isEqualTo(APIRESPONSE_STRING_REF);
        assertThat(schema.getProperties()).isNull();
    }

    /** 无 responses 不抛异常，原样返回。 */
    @Test
    void shouldSkipWhenOperationHasNoResponses() throws Exception {
        Operation op = new Operation();
        Operation result = customizer.customize(op, handler("pojo"));
        assertThat(result.getResponses()).isNull();
    }

    /** 门控机械性：customizer 与响应包装同 @ConditionalOnProperty（FastDddWebAutoConfiguration 扫描注册）。 */
    @Test
    void shouldRegisterCustomizerWhenWrapperEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FastDddWebAutoConfiguration.class))
                .withPropertyValues("fastddd.web.response-wrapper.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ApiResponseOpenApiCustomizer.class));
    }

    @Test
    void shouldNotRegisterCustomizerByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FastDddWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ApiResponseOpenApiCustomizer.class));
    }
}
