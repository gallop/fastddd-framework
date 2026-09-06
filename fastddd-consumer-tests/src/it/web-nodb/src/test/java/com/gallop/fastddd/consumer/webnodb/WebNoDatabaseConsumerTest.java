package com.gallop.fastddd.consumer.webnodb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ECO-006C4 验收 1：纯 Web 无库消费者（无 H2、无 datasource 配置、无自动配置排除）。
 *
 * <p>① 上下文零 DataSource / 零 JdbcTemplate Bean（不靠排除配置达成，而是依赖边界整改后
 * 池实现类不在 classpath、Boot 条件装配自然退让）；② 默认响应包装关 → 原始 JSON 直出；
 * ③ BusinessException → 真实全局异常处理信封（400 + errCode 透传）；④ @Valid 失败 → 400 信封。
 * 全部走 MockMvc 真实链。
 */
@SpringBootTest(classes = WebNodbApp.class)
@AutoConfigureMockMvc
class WebNoDatabaseConsumerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextHasNoDataSourceOrJdbcBeans() {
        // 无库契约本体：不配数据源、不排除任何自动配置，上下文仍装配成功且零数据访问 Bean
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JdbcTemplate.class)).isEmpty();
    }

    @Test
    void shouldReturnRawJsonWithoutEnvelope() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/probe/item"))
                .andExpect(status().isOk())
                // 无 ApiResponse 信封字段（包装默认关）
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                // 原始字段直出
                .andExpect(jsonPath("$.name").value("gallop"))
                .andReturn().getResponse().getContentAsString();
        // Long 保持数值形态（未启用包装时框架不改写 JSON 契约）
        assertThat(body).contains("\"id\":9007199254740992").doesNotContain("\"id\":\"");
    }

    @Test
    void businessExceptionShouldProduceErrorEnvelope() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/probe/boom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errCode").value("WEB_NO_DB_PROBE"))
                .andExpect(jsonPath("$.message").value("boom from web-nodb consumer"));
    }

    @Test
    void validationFailureShouldProduceBadRequestEnvelope() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/probe/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("name must not be blank")));
    }
}
