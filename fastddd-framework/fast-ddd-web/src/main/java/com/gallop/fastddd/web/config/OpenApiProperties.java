package com.gallop.fastddd.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI配置属性类
 * 支持通过配置文件自定义API文档信息
 * 
 * @author gallop
 * @since 2025-01-15
 * @version 1.0.0-SNAPSHOT
 */
@ConfigurationProperties(prefix = "fastddd.web.openapi")
public class OpenApiProperties {

    /**
     * 是否启用OpenAPI文档
     */
    private boolean enabled = true;

    /**
     * API文档标题
     */
    private String title = "FastDDD API Documentation";

    /**
     * API文档描述
     */
    private String description = "FastDDD框架API文档，提供完整的接口说明和示例";

    /**
     * API版本
     */
    private String version = "1.0.0";

    /**
     * 联系人信息
     */
    private Contact contact = new Contact();

    /**
     * 许可证信息
     */
    private License license = new License();

    /**
     * 服务器信息
     */
    private Server server = new Server();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    /**
     * 联系人信息
     */
    public static class Contact {
        private String name = "FastDDD Team";
        private String email = "support@fastddd.com";
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 许可证信息
     */
    public static class License {
        private String name = "Apache 2.0";
        private String url = "https://www.apache.org/licenses/LICENSE-2.0";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 服务器信息
     */
    public static class Server {
        private String url = "http://localhost:8080";
        private String description = "开发环境";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
