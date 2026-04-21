package com.gallop.fastddd.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * i18n 自动配置：注册 MessageSource 并初始化 Messages 静态工具类。
 * Web 相关的 LocaleResolver / LocaleChangeInterceptor 在 fast-ddd-web 的 I18nWebConfiguration 中配置。
 */
@AutoConfiguration
public class I18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames("messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        return ms;
    }

    @Bean
    public MessageSourceInitializer messageSourceInitializer(MessageSource messageSource) {
        Messages.setMessageSource(messageSource);
        return new MessageSourceInitializer();
    }

    static class MessageSourceInitializer {
    }
}
