package com.gallop.fastddd.consumer.webrespwrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-002 验收第 1 条（场景②）：opt-in 后响应 = ApiResponse 信封。
 *
 * <p>断言：code=200、data.id 为字符串（Long→String）、data.time 为框架格式 "yyyy-MM-dd HH:mm:ss"、
 * data.name 原样。证明 opt-in 后行为与既有 JSON 契约的改写是「显式同意」的。
 *
 * @author gallop
 * @since 2026-08-12
 */
@SpringBootTest(classes = WebRespWrapApp.class)
@AutoConfigureMockMvc
class ResponseWrappedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnEnvelopeWithWrappedData() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/demo"))
                .andExpect(status().isOk())
                // 信封字段存在
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                // data 体内为原始字段
                .andExpect(jsonPath("$.data.name").value("gallop"))
                // Long → 字符串
                .andExpect(jsonPath("$.data.id").value("9007199254740992"))
                // 框架日期格式
                .andExpect(jsonPath("$.data.time").value("2026-08-12 10:30:00"))
                .andReturn().getResponse().getContentAsString();

        // 明确锁定 Long 为字符串形态（带引号）
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("\"id\":\"9007199254740992\"")
                .doesNotContain("\"id\":9007199254740992");
    }
}
