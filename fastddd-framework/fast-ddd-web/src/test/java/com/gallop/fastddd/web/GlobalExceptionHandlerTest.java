package com.gallop.fastddd.web;

import com.gallop.fastddd.common.exception.BusinessException;
import com.gallop.fastddd.common.exception.SystemException;
import com.gallop.fastddd.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.hamcrest.Matchers.*;

/**
 * GlobalExceptionHandler 测试类
 * 验证全局异常处理器是否正确处理各种异常并返回统一的ApiResponse格式
 *
 * @author gallop
 * @since 2025-09-01
 * @version 1.1.0 - 添加 CommandValidationException 测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("这是一个业务异常测试")))
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void shouldHandleSystemException() throws Exception {
        mockMvc.perform(get("/test/system-error"))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is(500)))
                .andExpect(jsonPath("$.message", containsString("系统运行时异常")))
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void shouldHandleParameterTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/param-error?id=invalid"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("参数 'id' 类型不匹配")))
                .andExpect(jsonPath("$.data", nullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void shouldHandleCommandValidationException() throws Exception {
        mockMvc.perform(get("/test/command-validation-error"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("Command 验证失败")))
                .andExpect(jsonPath("$.message", containsString("orderNo")))
                .andExpect(jsonPath("$.message", containsString("amount")))
                .andExpect(jsonPath("$.data", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}