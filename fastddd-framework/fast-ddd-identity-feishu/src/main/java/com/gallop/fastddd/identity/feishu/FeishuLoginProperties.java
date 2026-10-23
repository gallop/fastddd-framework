package com.gallop.fastddd.identity.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code fastddd.identity.feishu.*} 飞书自建应用登录配置（W2-2，首版单企业自建应用）。
 *
 * <p>必填项（login.mode=feishu-web 且本模块在 classpath 时）：{@code app-id}、
 * {@code app-secret}、{@code redirect-uri}（须与开放平台安全设置登记一致）、
 * {@code expected-tenant-key}（企业租户校验——token/user_info 返回的 tenant_key
 * 不匹配即拒绝登录，防跨企业 code 混用）。端点/超时/重试为可调项，缺省按
 * W0-4（CW-01，2026-09-26 复核）冻结的官方地址。</p>
 *
 * <p>凭据安全：app-secret 只进配置绑定与本模块内存，不写入任何日志/响应/异常消息。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "fastddd.identity.feishu")
public class FeishuLoginProperties {

    /** 开放平台应用 client_id。 */
    private String appId;

    /** 应用 client_secret（敏感：不入日志）。 */
    private String appSecret;

    /** 授权回调地址（须与开放平台登记一致，绝对 http/https URL）。 */
    private String redirectUri;

    /** 预期企业 tenant_key：user_info 返回值不匹配即拒绝（防跨企业 code 混用）。 */
    private String expectedTenantKey;

    /** 授权页端点（浏览器 302 目标，无服务端调用）。 */
    private String authorizeEndpoint = "https://accounts.feishu.cn/open-apis/authen/v1/authorize";

    /**
     * token v2 端点（JSON 请求体）。2026-09-27 真实联调实证修订：v1 授权页 + PKCE
     * 官方当前仅支持搭配 v2 token 端点（authorize 文档明示「后续将尽快支持最新的
     * Token 端点」）；原冻结的 v3 端点（accounts.feishu.cn/oauth/v3/token）对此类
     * code 一律 20049。v2 响应为平铺信封，与客户端解析兼容（W2-2 联调 §6 实证）。
     */
    private String tokenEndpoint = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";

    /** user_info v1 端点（Bearer user_access_token，幂等 GET）。 */
    private String userInfoEndpoint = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private final Http http = new Http();

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getExpectedTenantKey() {
        return expectedTenantKey;
    }

    public void setExpectedTenantKey(String expectedTenantKey) {
        this.expectedTenantKey = expectedTenantKey;
    }

    public String getAuthorizeEndpoint() {
        return authorizeEndpoint;
    }

    public void setAuthorizeEndpoint(String authorizeEndpoint) {
        this.authorizeEndpoint = authorizeEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    public void setUserInfoEndpoint(String userInfoEndpoint) {
        this.userInfoEndpoint = userInfoEndpoint;
    }

    public Http getHttp() {
        return http;
    }

    /** HTTP 行为：超时与有限重试（仅幂等 user_info GET；授权码兑换不重试）。 */
    public static class Http {

        private Duration connectTimeout = Duration.ofSeconds(5);

        private Duration readTimeout = Duration.ofSeconds(10);

        /** user_info 幂等 GET 的附加重试次数（传输异常/系统类错误后；0 = 不重试）。 */
        private int userInfoMaxRetries = 2;

        /** 固定退避（不指数放大，避免登录路径长阻塞）。 */
        private Duration retryBackoff = Duration.ofMillis(300);

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getUserInfoMaxRetries() {
            return userInfoMaxRetries;
        }

        public void setUserInfoMaxRetries(int userInfoMaxRetries) {
            this.userInfoMaxRetries = userInfoMaxRetries;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }

    /** 必填项缺失清单（装配期拒绝用）。 */
    List<String> missingRequiredKeys() {
        List<String> missing = new ArrayList<>();
        if (appId == null || appId.isBlank()) {
            missing.add("fastddd.identity.feishu.app-id");
        }
        if (appSecret == null || appSecret.isBlank()) {
            missing.add("fastddd.identity.feishu.app-secret");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            missing.add("fastddd.identity.feishu.redirect-uri");
        }
        if (expectedTenantKey == null || expectedTenantKey.isBlank()) {
            missing.add("fastddd.identity.feishu.expected-tenant-key");
        }
        return missing;
    }
}
