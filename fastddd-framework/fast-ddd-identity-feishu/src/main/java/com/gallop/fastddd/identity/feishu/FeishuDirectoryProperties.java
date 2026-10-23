package com.gallop.fastddd.identity.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fastddd.identity.feishu.directory.*} 飞书目录源端点配置（W3-b）。
 *
 * <p>目录三端点（tenant token / 部门子级 / 按部门查人员）共享单一
 * {@code api-base-url}（同域开放平台；部门子级端点含路径参数，不适合逐端点
 * 全 URL 配置）。缺省按 W0-4（2026-09-28 编码前复核）冻结的官方地址。
 * 应用凭据（{@code app-id}/{@code app-secret}）与范围键
 * （{@code expected-tenant-key}）复用 {@link FeishuLoginProperties} 同键。</p>
 *
 * <p>HTTP 超时与登录链路同口径（connect 5s / read 10s），可独立调整；
 * 目录重试配额不复用本类——见 {@code FeishuDirectoryRetryProperties}
 * （{@code fastddd.identity.directory.retry.*}，W3 设计 §7 框架键）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "fastddd.identity.feishu.directory")
public class FeishuDirectoryProperties {

    /** 开放平台 API 基地址（目录端点全部由它派生；测试指向本地桩）。 */
    private String apiBaseUrl = "https://open.feishu.cn";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(10);

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

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

    /** tenant_access_token 端点（自建应用内部获取，JSON 请求体）。 */
    String tenantTokenEndpoint() {
        return trimSlash(apiBaseUrl) + "/open-apis/auth/v3/tenant_access_token/internal";
    }

    /** 部门子级端点（{@code fetch_child=false} 显式逐层遍历）。 */
    String childrenEndpoint(String parentDepartmentId) {
        return trimSlash(apiBaseUrl) + "/open-apis/contact/v3/departments/"
                + urlEncode(parentDepartmentId) + "/children";
    }

    /** 按部门查人员端点（department_id 为 open_department_id）。 */
    String personsEndpoint() {
        return trimSlash(apiBaseUrl) + "/open-apis/contact/v3/users/find_by_department";
    }

    /** 用户单查端点（W5-d 回查面；user_id_type=open_id 由调用方附加）。 */
    String userDetailEndpoint(String openId) {
        return trimSlash(apiBaseUrl) + "/open-apis/contact/v3/users/" + urlEncode(openId);
    }

    /** 部门单查端点（W5-d 回查面；department_id_type=open_department_id 由调用方附加）。 */
    String departmentDetailEndpoint(String openDepartmentId) {
        return trimSlash(apiBaseUrl) + "/open-apis/contact/v3/departments/"
                + urlEncode(openDepartmentId);
    }

    private static String trimSlash(String value) {
        String trimmed = value == null ? "" : value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String urlEncode(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8);
    }
}
