package com.gallop.fastddd.web;

import com.gallop.fastddd.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异常处理集成测试
 * 验证GlobalExceptionHandler能正确处理各种异常并返回统一的ApiResponse格式
 * 
 * @author gallop
 * @since 2025-09-01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ExceptionHandlingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void shouldHandleBusinessExceptionAndReturnApiResponse() {
        // 测试业务异常处理
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/business-error", ApiResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("业务异常");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    public void shouldHandleSystemExceptionAndReturnApiResponse() {
        // 测试系统异常处理
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/system-error", ApiResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).contains("系统运行时异常");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    public void shouldHandleParameterTypeMismatchAndReturnApiResponse() {
        // 测试参数类型不匹配异常处理
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/param-error?id=invalid", ApiResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("参数 'id' 类型不匹配");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    public void shouldHandleValidationExceptionAndReturnApiResponse() {
        // 测试JSR-303验证异常处理
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // 发送无效的请求体（缺少必填字段）
        String invalidRequestBody = "{\"name\":\"\",\"age\":null}";
        HttpEntity<String> request = new HttpEntity<>(invalidRequestBody, headers);
        
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/test/validation", request, ApiResponse.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("参数校验失败");
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    public void shouldReturnConsistentApiResponseFormat() {
        // 验证所有异常都返回一致的ApiResponse格式
        ResponseEntity<ApiResponse> businessErrorResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/business-error", ApiResponse.class);
        
        ResponseEntity<ApiResponse> systemErrorResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/system-error", ApiResponse.class);
        
        ResponseEntity<ApiResponse> paramErrorResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/test/param-error?id=invalid", ApiResponse.class);
        
        // 验证所有响应都有相同的结构
        assertThat(businessErrorResponse.getBody().getCode()).isNotNull();
        assertThat(businessErrorResponse.getBody().getMessage()).isNotNull();
        assertThat(businessErrorResponse.getBody().getTimestamp()).isNotNull();
        
        assertThat(systemErrorResponse.getBody().getCode()).isNotNull();
        assertThat(systemErrorResponse.getBody().getMessage()).isNotNull();
        assertThat(systemErrorResponse.getBody().getTimestamp()).isNotNull();
        
        assertThat(paramErrorResponse.getBody().getCode()).isNotNull();
        assertThat(paramErrorResponse.getBody().getMessage()).isNotNull();
        assertThat(paramErrorResponse.getBody().getTimestamp()).isNotNull();
    }
}