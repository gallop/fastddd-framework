package com.gallop.fastddd.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knife4j配置属性类
 * 支持通过配置文件自定义Knife4j功能和界面设置
 * 
 * @author gallop
 * @since 2025-01-15
 * @version 1.0.0-SNAPSHOT
 */
@ConfigurationProperties(prefix = "fastddd.web.knife4j")
public class Knife4jProperties {

    /**
     * 是否启用Knife4j
     */
    private boolean enabled = true;

    /**
     * 是否启用增强功能
     */
    private boolean enhance = true;

    /**
     * 是否在生产环境中屏蔽
     */
    private boolean production = false;

    /**
     * 基础认证配置
     */
    private Basic basic = new Basic();

    /**
     * 设置配置
     */
    private Setting setting = new Setting();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnhance() {
        return enhance;
    }

    public void setEnhance(boolean enhance) {
        this.enhance = enhance;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    public Basic getBasic() {
        return basic;
    }

    public void setBasic(Basic basic) {
        this.basic = basic;
    }

    public Setting getSetting() {
        return setting;
    }

    public void setSetting(Setting setting) {
        this.setting = setting;
    }

    /**
     * 基础认证配置
     */
    public static class Basic {
        /**
         * 是否启用基础认证
         */
        private boolean enable = false;

        /**
         * 用户名
         */
        private String username = "admin";

        /**
         * 密码
         */
        private String password = "123456";

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * 界面设置配置
     */
    public static class Setting {
        /**
         * 是否启用界面设置
         */
        private boolean enable = true;

        /**
         * 语言设置
         */
        private String language = "zh-CN";

        /**
         * 是否启用搜索
         */
        private boolean enableSearch = true;

        /**
         * 是否启用Footer
         */
        private boolean enableFooter = false;

        /**
         * 是否启用Footer自定义
         */
        private boolean enableFooterCustom = true;

        /**
         * Footer自定义文本
         */
        private String footerCustomContent = "Copyright © 2025 FastDDD Framework";

        /**
         * 是否启用动态参数调试
         */
        private boolean enableDynamicParameter = true;

        /**
         * 是否启用调试
         */
        private boolean enableDebug = true;

        /**
         * 是否启用OpenApi规范
         */
        private boolean enableOpenApi = true;

        /**
         * 是否启用分组
         */
        private boolean enableGroup = true;

        public boolean isEnable() {
            return enable;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public boolean isEnableSearch() {
            return enableSearch;
        }

        public void setEnableSearch(boolean enableSearch) {
            this.enableSearch = enableSearch;
        }

        public boolean isEnableFooter() {
            return enableFooter;
        }

        public void setEnableFooter(boolean enableFooter) {
            this.enableFooter = enableFooter;
        }

        public boolean isEnableFooterCustom() {
            return enableFooterCustom;
        }

        public void setEnableFooterCustom(boolean enableFooterCustom) {
            this.enableFooterCustom = enableFooterCustom;
        }

        public String getFooterCustomContent() {
            return footerCustomContent;
        }

        public void setFooterCustomContent(String footerCustomContent) {
            this.footerCustomContent = footerCustomContent;
        }

        public boolean isEnableDynamicParameter() {
            return enableDynamicParameter;
        }

        public void setEnableDynamicParameter(boolean enableDynamicParameter) {
            this.enableDynamicParameter = enableDynamicParameter;
        }

        public boolean isEnableDebug() {
            return enableDebug;
        }

        public void setEnableDebug(boolean enableDebug) {
            this.enableDebug = enableDebug;
        }

        public boolean isEnableOpenApi() {
            return enableOpenApi;
        }

        public void setEnableOpenApi(boolean enableOpenApi) {
            this.enableOpenApi = enableOpenApi;
        }

        public boolean isEnableGroup() {
            return enableGroup;
        }

        public void setEnableGroup(boolean enableGroup) {
            this.enableGroup = enableGroup;
        }
    }
}