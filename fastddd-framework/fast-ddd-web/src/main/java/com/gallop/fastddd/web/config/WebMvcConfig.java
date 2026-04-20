package com.gallop.fastddd.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC配置类
 * 配置消息转换器和其他Web相关设置
 * 
 * @author FastDDD Framework Team
 * @version 1.0.0-SNAPSHOT
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 扩展消息转换器
     * 确保MappingJackson2HttpMessageConverter正确配置并支持application/json
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 创建Jackson消息转换器
        MappingJackson2HttpMessageConverter jackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
        jackson2HttpMessageConverter.setObjectMapper(objectMapper);
        
        // 设置支持的媒体类型
        // 注意：不包含 TEXT_PLAIN — Spring MVC 的 SSE 支持（Flux<ServerSentEvent> / SseEmitter）
        // 内部以 TEXT_PLAIN 发送 SSE 帧文本，如果 Jackson converter 拦截了 TEXT_PLAIN，
        // 会将整个 SSE 帧 JSON 序列化（加引号、转义换行符），导致前端无法解析。
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.APPLICATION_JSON);
        mediaTypes.add(MediaType.APPLICATION_JSON_UTF8);
        jackson2HttpMessageConverter.setSupportedMediaTypes(mediaTypes);
        
        // 添加到转换器列表的开头，确保优先使用
        converters.add(0, jackson2HttpMessageConverter);
    }
}