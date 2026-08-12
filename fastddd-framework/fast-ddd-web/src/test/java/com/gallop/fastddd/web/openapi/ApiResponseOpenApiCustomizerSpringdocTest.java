package com.gallop.fastddd.web.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-002 Phase C springdoc 端到端集成：经 /v3/api-docs 证明 springdoc 实际调用了
 * {@link ApiResponseOpenApiCustomizer}（customizer 契约的确定性证明见
 * {@link ApiResponseOpenApiCustomizerTest}）。
 *
 * <p>测试上下文响应包装 opt-in，/v3/api-docs 响应本身被 SmartResponseAdvice 包成信封
 * （返回类型 byte[] → data 为 OpenAPI JSON 的 base64），故先解码 data 再校验 paths。
 * 这一副作用本身（包装 opt-in 时 springdoc 端点被包）记录于 §16.33 作为遗留观察项，
 * 不影响 customizer 对业务端点 schema 的正确包装。
 *
 * @author gallop
 * @since 2026-08-12
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class ApiResponseOpenApiCustomizerSpringdocTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode openApiDoc() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode parsed = objectMapper.readTree(body);
        // 包装 opt-in → 响应是信封，data = OpenAPI JSON 的 base64；否则 parsed 本身即文档
        if (parsed.has("paths")) {
            return parsed;
        }
        String base64 = parsed.at("/data").asText("");
        assertThat(base64).as("/v3/api-docs 响应应含可解码的 data").isNotEmpty();
        return objectMapper.readTree(new String(Base64.getDecoder().decode(base64)));
    }

    @Test
    void springdocShouldApplyCustomizerToPojoEndpoint() throws Exception {
        JsonNode schema = openApiDoc()
                .at("/paths/~1openapi-test~1pojo/get/responses/200/content/*~1*/schema");
        assertThat(schema.at("/properties/code").isMissingNode()).isFalse();
        assertThat(schema.at("/properties/message").isMissingNode()).isFalse();
        assertThat(schema.at("/properties/timestamp").isMissingNode()).isFalse();
        assertThat(schema.at("/properties/data/$ref").asText("")).isEqualTo("#/components/schemas/DemoPojo");
    }

    @Test
    void springdocShouldNotDoubleWrapResponseEntityEndpoint() throws Exception {
        JsonNode schema = openApiDoc()
                .at("/paths/~1openapi-test~1entity/get/responses/200/content/*~1*/schema");
        boolean isInlineEnvelope = schema.has("properties")
                && !schema.at("/properties/code").isMissingNode()
                && !schema.at("/properties/data").isMissingNode()
                && !schema.at("/properties/timestamp").isMissingNode();
        assertThat(isInlineEnvelope).as("ResponseEntity 端点不应被二次包装").isFalse();
    }
}
