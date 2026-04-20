package com.gallop.fastddd.web.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.response.ApiResponse;
import com.gallop.fastddd.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 响应包装过滤器
 * 使用Filter来拦截和包装API响应，提供更稳定的响应处理
 * 
 * @author gallop
 * @since 1.0.0
 */
@Slf4j
// @Component - 暂时禁用Filter，使用SmartResponseAdvice代替
public class ResponseWrapperFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper;
    
    public ResponseWrapperFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // 排除Swagger相关的端点
        String path = request.getRequestURI();
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") || 
            path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 使用ContentCachingResponseWrapper来缓存响应内容
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        
        try {
            // 继续执行过滤器链
            filterChain.doFilter(request, responseWrapper);
            
            // 获取原始响应内容
            byte[] responseContent = responseWrapper.getContentAsByteArray();
            
            if (responseContent.length > 0) {
                String originalContent = new String(responseContent, StandardCharsets.UTF_8);
                
                // 检查是否已经是ApiResponse格式
                if (isAlreadyWrapped(originalContent)) {
                    // 已经包装过，直接返回
                    response.getOutputStream().write(responseContent);
                } else {
                    // 需要包装
                    Object wrappedResponse = wrapResponse(originalContent, responseWrapper.getStatus());
                    String wrappedJson = objectMapper.writeValueAsString(wrappedResponse);
                    
                    // 设置响应头
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    
                    // 写入包装后的响应
                    response.getOutputStream().write(wrappedJson.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                // 空响应，包装为成功响应
                ApiResponse<Object> apiResponse = ApiResponse.success(null);
                String wrappedJson = objectMapper.writeValueAsString(apiResponse);
                
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getOutputStream().write(wrappedJson.getBytes(StandardCharsets.UTF_8));
            }
            
        } catch (Exception e) {
            log.error("响应包装失败", e);
            // 发生异常时，返回原始响应
            responseWrapper.copyBodyToResponse();
        }
    }
    
    /**
     * 检查响应内容是否已经被包装
     */
    private boolean isAlreadyWrapped(String content) {
        try {
            // 尝试解析为ApiResponse
            objectMapper.readValue(content, ApiResponse.class);
            return true;
        } catch (Exception e) {
            // 不是ApiResponse格式
            return false;
        }
    }
    
    /**
     * 包装响应内容
     */
    private Object wrapResponse(String originalContent, int statusCode) {
        try {
            // 尝试解析原始内容
            Object parsedContent = objectMapper.readValue(originalContent, Object.class);
            
            // 检查是否是Result类型
            if (parsedContent instanceof Result) {
                return parsedContent;
            }
            
            // 根据状态码决定如何包装
            if (statusCode >= 200 && statusCode < 300) {
                return ApiResponse.success(parsedContent);
            } else {
                return ApiResponse.<Object>error(500, "请求失败", parsedContent);
            }
            
        } catch (Exception e) {
            // 解析失败，直接包装原始字符串
            if (statusCode >= 200 && statusCode < 300) {
                return ApiResponse.success(originalContent);
            } else {
                return ApiResponse.<String>error(500, "请求失败", originalContent);
            }
        }
    }
}