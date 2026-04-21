package com.gallop.fastddd.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * i18n 消息工具类，提供静态方法获取国际化消息。
 * 由 I18nAutoConfiguration 自动注入 MessageSource。
 */
public class Messages {

    private static MessageSource messageSource;

    public static void setMessageSource(MessageSource ms) {
        messageSource = ms;
    }

    /**
     * 获取国际化消息
     *
     * @param code 消息码
     * @param args 消息参数
     * @return 国际化消息字符串，未找到时返回 code 本身
     */
    public static String get(String code, Object... args) {
        if (messageSource == null) {
            return code;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, locale);
    }
}
