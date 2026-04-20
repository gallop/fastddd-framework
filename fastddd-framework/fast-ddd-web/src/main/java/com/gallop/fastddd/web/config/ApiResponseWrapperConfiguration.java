package com.gallop.fastddd.web.config;

import com.gallop.fastddd.web.advice.SmartResponseAdvice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API响应包装配置类
 */
@Configuration
public class ApiResponseWrapperConfiguration {
    
    /**
     * 手动注册SmartResponseAdvice Bean
     */
    @Bean
    public SmartResponseAdvice smartResponseAdvice() {
        return new SmartResponseAdvice();
    }
}