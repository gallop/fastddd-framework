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
 * <p><b>协议事实</b>：token v2 = {@code POST /open-apis/authen/v2/oauth/token}
 * （2026-09-27 联调修订：v1 授权页 + PKCE 仅配 v2，原 v3 冻结对此类 code 一律
 * 20049），请求体为<b>application/json</b>（grant_type=authorization_code +
 * client_id + client_secret + code + redirect_uri + code_verifier），响应<b>平铺</b>
 * {@code {code, access_token, expires_in, scope, …}}；user_info v1 = {@code GET
 * /open-apis/authen/v1/user_info}，Bearer user_access_token，响应信封
 * {@code {code,msg,data}}。错误信封官方现行 {@code {code,msg}}（W22-R2-N3 同步
 * W0-4 §7 勘误：首版误记 {@code {code,error,error_description}}；实现只读
 * {@code code} 分类，无行为影响），按端点分类表归类（{@link FeishuApiException}），
 * 上游错误消息不透传。</p>
 *
 * <p><b>成功判定契约（W22-C1，2026-09-27）</b>：以下三条<b>同时成立</b>才算成功，
 * 任一不成立按失败类别处置、拒绝本次登录且不据其修改任何本地状态——
 * （1）HTTP 状态恰为 200（两端点唯一允许的成功状态）；（2）信封业务码 {@code code}
 * 节点存在且为整数值（int/long/BigInteger，可无损承载为 long）且严格等于 0——
 * 缺失、JSON null、字符串数字、浮点、布尔、超出 long 一律视为非法信封，绝不使用
 * Jackson 默认值或有损转换；（3）必需字段按协议类型校验通过：token 的
 * {@code access_token} 非空文本、{@code expires_in} 正整数（scope 宽松可选）；
 * user_info 的 {@code data} 为对象且 {@code open_id/union_id/tenant_key} 均为
 * 非空文本（name/en_name 宽松可选）。失败优先级：信封非法 →
 * {@code malformed-response}；业务码=0 而 HTTP 非 200（成功形状矛盾）→
 * {@code http-business-conflict}；HTTP 429/5xx 且信封为合法非零码（上游过载，
 * 业务结果不可信）→ token 不重试直接 {@code upstream-system}、user_info 有界
 * 重试后 {@code upstream-system}；其余合法非零码按端点分类表归类
 * （{@link FeishuApiException}，code 以 long 承载、诊断不截断）。</p>
 *
 * <p><b>重试契约（W2-2）</b>：授权码兑换<b>不重试</b>——code 一次性（5 分钟），
 * 网络超时/系统错误下首次兑换结果不确定，重试同一 code 有"已使用（20065）"错配
 * 风险，统一归"结果不确定，重新发起登录"；user_info 为幂等 GET，传输异常、系统类
 * 错误（20050/20072）及 HTTP 429/5xx 按 {@code http.user-info-max-retries} 有限
 * 重试（固定退避）；畸形信封与成功形状矛盾不重试（响应内容已确定，重试无意义）。</p>
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

    /** token v2 兑换结果（仅保留登录链路所需字段；refresh_token 首版不用，不外泄）。 */
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
        ParsedResponse parsed;
        try {
            parsed = sendForJson(request, FeishuApiException.Endpoint.TOKEN);
        } catch (IOException transport) {
            // 兑换不重试：网络层失败即结果不确定（首次兑换可能已被服务端受理）
            throw unavailable("transport", "飞书授权码兑换网络异常，结果不确定，请重新发起登录", transport);
        }
        int httpStatus = parsed.httpStatus();
        Long bizCode = strictEnvelopeCode(parsed.json());
        if (bizCode == null) {
            throw unavailable("malformed-response",
                    "飞书 token 响应缺少合法整型业务码（httpStatus=" + httpStatus + "），结果不确定");
        }
        if (bizCode == 0) {
            if (httpStatus != 200) {
                throw unavailable("http-business-conflict",
                        "飞书 token 响应业务码为 0 但 HTTP 状态非成功（httpStatus=" + httpStatus + "），矛盾结果不确定");
            }
            String accessToken = requiredText(parsed.json(), "access_token", "token");
            JsonNode expiresIn = parsed.json().path("expires_in");
            if (!expiresIn.isIntegralNumber() || !expiresIn.canConvertToLong() || expiresIn.asLong() <= 0) {
                throw unavailable("malformed-response",
                        "飞书 token 响应 expires_in 缺失或非正整数（code=0），结果不确定");
            }
            return new TokenResult(accessToken, expiresIn.asLong(), textOrNull(parsed.json(), "scope"));
        }
        if (isUpstreamOverload(httpStatus)) {
            // 429/5xx：传输层已报异常，业务结果不可信；兑换不重试 → 结果不确定
            throw unavailable("upstream-system",
                    "飞书 token 上游过载（httpStatus=" + httpStatus + "，业务码=" + bizCode + "），结果不确定");
        }
        // W22-RV-05：异常携带真实 HTTP 状态（排障区分"传输正常业务拒绝"与"上游 5xx"）
        throw new FeishuApiException(FeishuApiException.Endpoint.TOKEN,
                FeishuApiException.classify(FeishuApiException.Endpoint.TOKEN, bizCode),
                bizCode, httpStatus);
    }

    /** user_info 拉取（幂等 GET，有限重试：传输异常/系统类错误/HTTP 429 或 5xx）。 */
    UserInfo fetchUserInfo(String accessToken) {
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
                int httpStatus = parsed.httpStatus();
                Long bizCode = strictEnvelopeCode(parsed.json());
                if (bizCode == null) {
                    throw unavailable("malformed-response",
                            "飞书 user_info 响应缺少合法整型业务码（httpStatus=" + httpStatus + "），结果不确定");
                }
                if (bizCode != 0) {
                    FeishuApiException apiError = new FeishuApiException(FeishuApiException.Endpoint.USER_INFO,
                            FeishuApiException.classify(FeishuApiException.Endpoint.USER_INFO, bizCode),
                            bizCode, httpStatus);
                    boolean retryable = apiError.category() == FeishuApiException.Category.SYSTEM
                            || isUpstreamOverload(httpStatus);
                    if (retryable && attempt < attempts) {
                        backoff();
                        continue;
                    }
                    if (retryable) {
                        throw unavailable("upstream-system",
                                "飞书 user_info 上游过载/系统错误重试耗尽（httpStatus=" + httpStatus
                                        + "，业务码=" + bizCode + "），登录结果不确定");
                    }
                    throw apiError;
                }
                if (httpStatus != 200) {
                    throw unavailable("http-business-conflict",
                            "飞书 user_info 响应业务码为 0 但 HTTP 状态非成功（httpStatus=" + httpStatus + "），矛盾结果不确定");
                }
                JsonNode data = parsed.json().path("data");
                if (!data.isObject()) {
                    throw unavailable("malformed-response",
                            "飞书 user_info 响应缺少 data 对象（code=0），结果不确定");
                }
                return new UserInfo(
                        requiredText(data, "open_id", "user_info"),
                        requiredText(data, "union_id", "user_info"),
                        requiredText(data, "tenant_key", "user_info"),
                        textOrNull(data, "name"), textOrNull(data, "en_name"));
            } catch (IOException transport) {
                // 传输异常（含读超时）：幂等 GET 可安全重试
                lastTransportError = transport;
                if (attempt < attempts) {
                    backoff();
                    continue;
                }
            }
        }
        throw unavailable("transport", "飞书 user_info 网络异常重试耗尽，登录结果不确定", lastTransportError);
    }

    /**
     * W22-C1：信封业务码严格解析——{@code code} 节点必须存在、非 JSON null、为整数值
     * 类型（int/long/BigInteger）且可无损承载为 long；字符串数字/浮点/布尔一律非法
     * （返回 {@code null}），不走默认值或有损转换；合法值原样返回（long 不截断）。
     */
    private static Long strictEnvelopeCode(JsonNode json) {
        JsonNode code = json.path("code");
        if (!code.isIntegralNumber() || !code.canConvertToLong()) {
            return null;
        }
        return code.longValue();
    }

    /** HTTP 429/5xx：传输层已报过载/故障，业务语义不可信（与业务码无关）。 */
    private static boolean isUpstreamOverload(int httpStatus) {
        return httpStatus == 429 || httpStatus >= 500;
    }

    /** 必需字段：必须为非空文本——拒绝数字/布尔等其他 JSON 类型的宽松转换。 */
    private static String requiredText(JsonNode node, String field, String endpointLabel) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw unavailable("malformed-response",
                    "飞书" + endpointLabel + "响应字段 " + field + " 缺失或非文本/为空，结果不确定");
        }
        return value.asText();
    }

    /** 可选字段：仅接受文本（类型不对返回 null，不做宽松转换）。 */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
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
