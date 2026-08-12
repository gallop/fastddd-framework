package com.gallop.fastddd.consumer.webrespoff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-002 验收第 1 条（场景①）：仅引入 fast-ddd-starter、不设 opt-in 属性 →
 * 响应 = 原始 JSON：Long 保持数值、无 ApiResponse 信封、LocalDateTime 为 Spring Boot 默认表示。
 *
 * <p>机械锁定「仅 import web 不改写既有 JSON 契约」。
 *
 * @author gallop
 * @since 2026-08-12
 */
@SpringBootTest(classes = WebRespOffApp.class)
@AutoConfigureMockMvc
class ResponseNotWrappedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnRawJsonWithoutEnvelope() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/demo"))
                .andExpect(status().isOk())
                // 无 ApiResponse 信封字段
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                // 原始字段直出
                .andExpect(jsonPath("$.name").value("gallop"))
                .andReturn().getResponse().getContentAsString();

        // Long 保持数值（非字符串）：响应文本中 id 无引号
        // 使用字符串匹配明确锁定「数值」形态，避免 JSON 数值→Double 精度坑
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("\"id\":9007199254740992")
                .doesNotContain("\"id\":\"");
    }

    @Test
    void timeShouldBeSpringBootDefaultNotFrameworkFormatted() throws Exception {
        // 默认关时框架不施加 "yyyy-MM-dd HH:mm:ss" 格式；断言框架格式未生效（不含该字面量）
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/demo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("\"time\":\"2026-08-12 10:30:00\"");
        // Spring Boot 默认（WRITE_DATES_AS_TIMESTAMPS=off + JavaTimeModule）输出 ISO-8601 数组解构或 ISO 字符串
        org.assertj.core.api.Assertions.assertThat(body).contains("\"time\"");
    }
}
