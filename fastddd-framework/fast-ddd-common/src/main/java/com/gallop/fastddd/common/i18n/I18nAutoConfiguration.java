package com.gallop.fastddd.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

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
    public MessagesInitializer messagesInitializer(MessageSource messageSource) {
        Messages.setMessageSource(messageSource);
        return new MessagesInitializer();
    }

    public static class MessagesInitializer {
        // placeholder — Messages.setMessageSource 已在 Bean 工厂方法中完成
    }
}
