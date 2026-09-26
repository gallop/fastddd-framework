package com.gallop.fastddd.identity.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.identity.core.error.ExternalLoginUnavailableException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 飞书 OAuth HTTP 客户端（W0-4 CW-01，2026-09-26 编码前复核；JDK HttpClient，无 SDK）。
 *
 * <p><b>协议事实</b>：token v3 = {@code POST /oauth/v3/token}，请求体为
 * <b>application/json</b>（grant_type=authorization_code + client_id + client_secret +
 * code + redirect_uri + code_verifier）；user_info v1 = {@code GET
 * /open-apis/authen/v1/user_info}，Bearer user_access_token，响应信封
 * {@code {code,msg,data}}。错误信封官方现行 {@code {code,msg}}（W22-R2-N3 同步
 * W0-4 §7 勘误：首版误记 {@code {code,error,error_description}}；实现只读
 * {@code code} 分类，无行为影响），按端点分类表归类（{@link FeishuApiException}），
 * 上游错误消息不透传。</p>
 *
 * <p><b>重试契约（W2-2）</b>：授权码兑换<b>不重试</b>——code 一次性（5 分钟），
 * 网络超时/系统错误下首次兑换结果不确定，重试同一 code 有"已使用（20065）"错配
 * 风险，统一归"结果不确定，重新发起登录"；user_info 为幂等 GET，传输异常/系统类
 * 错误（20050/20072）按 {@code http.user-info-max-retries} 有限重试（固定退避）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
final class FeishuOAuthClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final FeishuLoginProperties properties;

    private final HttpClient httpClient;

    FeishuOAuthClient(FeishuLoginProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getHttp().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** token v3 兑换结果（仅保留登录链路所需字段；refresh_token 首版不用，不外泄）。 */
    record TokenResult(String accessToken, long expiresIn, String scope) {
    }

    /** user_info v1 经验证的用户字段（无 status 字段——停用判定走错误码分类）。 */
    record UserInfo(String openId, String unionId, String tenantKey, String name, String enName) {
    }

    /** 授权码兑换（不重试）。任何网络/系统/不可解析失败 → 结果不确定。 */
    TokenResult exchange(String code, String codeVerifier) {
        String body;
        try {
            body = MAPPER.writeValueAsString(java.util.Map.of(
                    "grant_type", "authorization_code",
                    "client_id", properties.getAppId(),
                    "client_secret", properties.getAppSecret(),
                    "code", code,
                    "redirect_uri", properties.getRedirectUri(),
                    "code_verifier", codeVerifier));
        } catch (IOException e) {
            throw new IllegalStateException("飞书 token 请求体序列化失败（实现缺陷）", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getTokenEndpoint()))
                .timeout(properties.getHttp().getReadTimeout())
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonNode json;
        int httpStatus;
        try {
            ParsedResponse parsed = sendForJson(request, FeishuApiException.Endpoint.TOKEN);
            json = parsed.json();
            httpStatus = parsed.httpStatus();
        } catch (IOException transport) {
            // 兑换不重试：网络层失败即结果不确定（首次兑换可能已被服务端受理）
            throw unavailable("transport", "飞书授权码兑换网络异常，结果不确定，请重新发起登录", transport);
        }
        int bizCode = json.path("code").asInt(0);
        if (bizCode != 0) {
            // W22-RV-05：异常携带真实 HTTP 状态（排障区分"传输正常业务拒绝"与"上游 5xx"）
            throw new FeishuApiException(FeishuApiException.Endpoint.TOKEN,
                    FeishuApiException.classify(FeishuApiException.Endpoint.TOKEN, bizCode),
                    bizCode, httpStatus);
        }
        String accessToken = json.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw unavailable("malformed-response", "飞书 token 响应缺少 access_token（code=0），结果不确定");
        }
        return new TokenResult(accessToken, json.path("expires_in").asLong(0), json.path("scope").asText(null));
    }

    /** user_info 拉取（幂等 GET，有限重试：仅传输异常与系统类错误重试）。 */
    UserInfo fetchUserInfo(String accessToken) {
        FeishuApiException lastApiError = null;
        IOException lastTransportError = null;
        int attempts = 1 + Math.max(0, properties.getHttp().getUserInfoMaxRetries());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUserInfoEndpoint()))
                    .timeout(properties.getHttp().getReadTimeout())
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            try {
                ParsedResponse parsed = sendForJson(request, FeishuApiException.Endpoint.USER_INFO);
                JsonNode json = parsed.json();
                int bizCode = json.path("code").asInt(0);
                if (bizCode != 0) {
                    // W22-RV-05：真实 HTTP 状态随异常携带（非字面量 200）
                    FeishuApiException apiError = new FeishuApiException(FeishuApiException.Endpoint.USER_INFO,
                            FeishuApiException.classify(FeishuApiException.Endpoint.USER_INFO, bizCode),
                            bizCode, parsed.httpStatus());
                    if (apiError.category() == FeishuApiException.Category.SYSTEM && attempt < attempts) {
                        lastApiError = apiError;
                        backoff();
                        continue;
                    }
                    throw apiError;
                }
                JsonNode data = json.path("data");
                String openId = data.path("open_id").asText(null);
                String unionId = data.path("union_id").asText(null);
                String tenantKey = data.path("tenant_key").asText(null);
                if (openId == null || unionId == null || tenantKey == null
                        || openId.isBlank() || unionId.isBlank() || tenantKey.isBlank()) {
                    throw unavailable("malformed-response",
                            "飞书 user_info 响应缺少 open_id/union_id/tenant_key，结果不确定");
                }
                return new UserInfo(openId, unionId, tenantKey,
                        data.path("name").asText(null), data.path("en_name").asText(null));
            } catch (IOException transport) {
                // 传输异常（含读超时）：幂等 GET 可安全重试
                lastTransportError = transport;
                if (attempt < attempts) {
                    backoff();
                    continue;
                }
            }
        }
        if (lastApiError != null) {
            throw unavailable("upstream-system",
                    "飞书 user_info 系统错误重试耗尽，登录结果不确定（code=" + lastApiError.code() + "）");
        }
        throw unavailable("transport", "飞书 user_info 网络异常重试耗尽，登录结果不确定", lastTransportError);
    }

    /** sendForJson 产物（W22-RV-05）：真实 HTTP 状态随 JSON 一起回传，供异常与排障使用。 */
    private record ParsedResponse(int httpStatus, JsonNode json) {
    }

    private ParsedResponse sendForJson(HttpRequest request, FeishuApiException.Endpoint endpoint)
            throws IOException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("transport", "飞书" + endpoint.label() + "调用被中断，结果不确定", e);
        }
        String body = response.body();
        JsonNode json;
        try {
            json = body == null || body.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (IOException malformed) {
            // 非法 JSON：结果不确定（不猜测错误语义）
            throw unavailable("malformed-response",
                    "飞书" + endpoint.label() + "响应非 JSON（httpStatus=" + response.statusCode() + "），结果不确定");
        }
        return new ParsedResponse(response.statusCode(), json);
    }

    private void backoff() {
        try {
            Thread.sleep(properties.getHttp().getRetryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalLoginUnavailableException("transport", "登录重试等待被中断", e);
        }
    }

    private static ExternalLoginUnavailableException unavailable(String reasonCode, String message) {
        return new ExternalLoginUnavailableException(reasonCode, message);
    }

    private static ExternalLoginUnavailableException unavailable(String reasonCode, String message, Throwable cause) {
        return new ExternalLoginUnavailableException(reasonCode, message, cause);
    }
}
