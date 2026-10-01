package com.gallop.fastddd.identity.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 飞书目录 HTTP 客户端（W3-b；W0-4 §1.3/1.4 2026-09-28 编码前复核；JDK
 * HttpClient，无 SDK）。三端点：tenant_access_token/internal（POST）、
 * departments/:id/children（GET，{@code fetch_child=false} 逐层）、
 * users/find_by_department（GET）。
 *
 * <p><b>成功判定（沿用 W22-C1 严格口径；M1-TAG-01 收紧分页字段）</b>：HTTP 200 +
 * 信封业务码为整数值且严格等于 0 + 必需字段类型正确，三者同时成立才算成功；
 * 目录分页响应的 {@code items} 必须为在场的数组、{@code has_more} 必须为在场的
 * 布尔（缺失/类型错误一律 {@code malformed-response}，不得默认空集/末页——分页
 * 完整性不确定即整代废弃）。信封非法 → {@code malformed-response} 不重试；
 * 业务码 0 而 HTTP 非 200 → {@code http-business-conflict} 不重试。</p>
 *
 * <p><b>错误分类（W0-4 §3 目录族；M1-TAG-02 过载优先）</b>：<b>HTTP 429/5xx 先于
 * 一切业务码解释</b>——传输层失败时信封不可信（与 tenant token 获取同序），一律
 * {@code upstream-system} 可重试，耗尽后 Unavailable 令编排整代废弃；仅非过载
 * 状态下的业务码才进入语义解释：230002/40004/40014/41050/42008 →
 * {@link DirectorySourceForbiddenException}（fail-fast，零重试——禁止退避风暴，
 * 禁止当离职/删除）；99991400 → {@code rate-limited} 可重试；传输异常（含读超时/
 * 中断）→ {@code transport} 可重试；40008（部门不存在）→ 内部信号
 * {@link DepartmentGoneSignal}（源侧按空页/跳过处置，不上抛、不解释为删除）；
 * 其余合法非零码 → {@code feishu-error} 不重试（确定失败，本次同步废弃，下轮
 * 调度重来）。</p>
 *
 * <p><b>重试（W3 设计 §2：源侧有界、责任不叠加）</b>：仅幂等 GET（children/
 * find_by_department）与 tenant token 获取（官方语义：有效期内重复获取幂等返回）
 * 按 {@code directory.retry.max-attempts}/{@code backoff} 固定退避有限重试
 * （复用登录客户端退避口径，配额独立）；Forbidden/畸形/矛盾结果一律不重试。
 * 耗尽后抛 {@link DirectorySourceUnavailableException}——编排不再重试。</p>
 *
 * <p><b>tenant_access_token 缓存</b>：官方最大有效期 2 小时，剩余 &lt; 30 分钟
 * 时重新获取会得到新 token（旧 token 并存可用）；本客户端在剩余 ≤ 30 分钟时
 * 主动换新。{@code expire} 字段按"有效期秒数"解读（官方/第三方示例一致）；
 * 防御性兼容绝对时间戳形态（&gt; 10^9 视为 epoch 秒）。凭据/token 不入日志、
 * 不入异常消息（本类不记日志，异常只含原因码/错误码/HTTP 状态）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
final class FeishuDirectoryClient {

    /** 飞书列表接口 page_size 上限（官方：缺省 10、最大 50）。 */
    static final int MAX_PAGE_SIZE = 50;

    /** 目录族权限/配置类错误码（W0-4 §3 + children 官方 40014/42008）。 */
    private static final Set<Long> FORBIDDEN_CODES = Set.of(
            230002L, 40004L, 40014L, 41050L, 42008L);

    /** 部门不存在（上游并发删除形态；不解释为删除，源侧转空页/跳过）。 */
    private static final long CODE_DEPARTMENT_NOT_EXIST = 40008L;

    /** 官方限流码（HTTP 200 信封内）。 */
    private static final long CODE_TOO_MANY_REQUESTS = 99991400L;

    /** token 剩余有效期低于此值即换新（官方：<30 分钟重取发新 token）。 */
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(30);

    /** expire 绝对时间戳形态判别阈值（秒；有效期上限 7200，epoch 均 > 10^9）。 */
    private static final long EPOCH_LIKE_THRESHOLD = 1_000_000_000L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final FeishuLoginProperties loginProperties;
    private final FeishuDirectoryProperties properties;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final HttpClient httpClient;

    private volatile CachedToken cachedToken;

    private record CachedToken(String token, Instant expiresAt) {
        boolean needsRefresh(Instant now) {
            return !now.plus(TOKEN_REFRESH_MARGIN).isBefore(expiresAt);
        }
    }

    /** 部门子级/人员分页的内部形态（游标原样携带飞书 page_token）。 */
    record FeishuPage<T>(List<T> items, String pageToken, boolean hasMore) {
    }

    record DepartmentItem(String openDepartmentId, String parentDepartmentId, String name) {
    }

    record PersonItem(String openId, String name, String enName, List<String> departmentIds) {
    }

    /** 可重试信号（传输/限流/上游过载）——仅在重试循环内部流转。 */
    private static final class RetryableSignal extends RuntimeException {

        private final String reasonCode;

        RetryableSignal(String reasonCode) {
            super(reasonCode, null, false, false);
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }

    /** 40008 部门不存在信号（源侧翻译为空终页/跳过，不是错误）。 */
    static final class DepartmentGoneSignal extends RuntimeException {
        DepartmentGoneSignal() {
            super("department-gone", null, false, false);
        }
    }

    FeishuDirectoryClient(FeishuLoginProperties loginProperties,
                          FeishuDirectoryProperties properties,
                          int maxRetries, Duration retryBackoff) {
        this.loginProperties = loginProperties;
        this.properties = properties;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoff = retryBackoff == null || retryBackoff.isNegative()
                ? Duration.ZERO : retryBackoff;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** tenant_access_token（带缓存；剩余 ≤ 30 分钟换新；获取失败按分类上抛）。 */
    synchronized String tenantAccessToken() {
        Instant now = Instant.now();
        CachedToken current = cachedToken;
        if (current != null && !current.needsRefresh(now)) {
            return current.token();
        }
        String token = withRetry("tenant-token", this::fetchTenantToken);
        return token;
    }

    private String fetchTenantToken() {
        String body;
        try {
            body = MAPPER.writeValueAsString(java.util.Map.of(
                    "app_id", String.valueOf(loginProperties.getAppId()),
                    "app_secret", String.valueOf(loginProperties.getAppSecret())));
        } catch (IOException e) {
            throw new IllegalStateException("飞书 tenant token 请求体序列化失败（实现缺陷）", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tenantTokenEndpoint()))
                .timeout(properties.getReadTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        ParsedResponse parsed = sendForJson(request);
        int httpStatus = parsed.httpStatus();
        Long bizCode = strictEnvelopeCode(parsed.json());
        if (bizCode == null) {
            throw unavailable("malformed-response",
                    "飞书 tenant_token 响应缺少合法整型业务码（httpStatus=" + httpStatus + "）");
        }
        if (bizCode == 0) {
            if (httpStatus != 200) {
                throw unavailable("http-business-conflict",
                        "飞书 tenant_token 业务码为 0 但 HTTP 状态非成功（httpStatus=" + httpStatus + "）");
            }
            String token = requiredText(parsed.json(), "tenant_access_token", "tenant_token");
            JsonNode expire = parsed.json().path("expire");
            if (!expire.isIntegralNumber() || !expire.canConvertToLong() || expire.asLong() <= 0) {
                throw unavailable("malformed-response",
                        "飞书 tenant_token 响应 expire 缺失或非正整数（code=0）");
            }
            cachedToken = new CachedToken(token, expiryFrom(expire.asLong()));
            return token;
        }
        // 凭据/应用形态类配置错误：fail-fast（无重试价值，重试会放大噪声）
        if (isUpstreamOverload(httpStatus)) {
            throw new RetryableSignal("upstream-system");
        }
        throw forbidden("tenant-token",
                "飞书 tenant_token 获取被拒（code=" + bizCode + "）——请核对 app-id/app-secret"
                        + " 与应用可用状态（配置类错误，fail-fast）");
    }

    /** 部门子级分页（{@code fetch_child=false}；pageToken=null 表示该父部门首页）。 */
    FeishuPage<DepartmentItem> children(String parentDepartmentId, String pageToken, int pageSize) {
        StringBuilder uri = new StringBuilder(properties.childrenEndpoint(parentDepartmentId))
                .append("?department_id_type=open_department_id")
                .append("&fetch_child=false")
                .append("&page_size=").append(clampPageSize(pageSize));
        if (notBlank(pageToken)) {
            uri.append("&page_token=").append(encode(pageToken));
        }
        return withRetry("departments-children", () ->
                contactGet(uri.toString(), "departments-children", FeishuDirectoryClient::mapDepartments));
    }

    /** 按部门查人员分页（user_id_type=open_id；pageToken=null 表示首页）。 */
    FeishuPage<PersonItem> persons(String departmentId, String pageToken, int pageSize) {
        StringBuilder uri = new StringBuilder(properties.personsEndpoint())
                .append("?department_id=").append(encode(departmentId))
                .append("&user_id_type=open_id")
                .append("&department_id_type=open_department_id")
                .append("&page_size=").append(clampPageSize(pageSize));
        if (notBlank(pageToken)) {
            uri.append("&page_token=").append(encode(pageToken));
        }
        return withRetry("find-by-department", () ->
                contactGet(uri.toString(), "find-by-department", FeishuDirectoryClient::mapPersons));
    }

    private interface BodyMapper<T> {
        T apply(JsonNode data);
    }

    private <T> T contactGet(String uri, String label, BodyMapper<T> mapper) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(properties.getReadTimeout())
                .header("Authorization", "Bearer " + tenantAccessToken())
                .GET()
                .build();
        ParsedResponse parsed = sendForJson(request);
        return interpret(parsed, label, mapper);
    }

    private <T> T interpret(ParsedResponse parsed, String label, BodyMapper<T> mapper) {
        int httpStatus = parsed.httpStatus();
        Long bizCode = strictEnvelopeCode(parsed.json());
        if (bizCode == null) {
            throw unavailable("malformed-response",
                    "飞书" + label + "响应缺少合法整型业务码（httpStatus=" + httpStatus + "）");
        }
        if (bizCode != 0) {
            // M1-TAG-02：HTTP 429/5xx 属传输层失败，此时响应体（含业务码信封）不可信，
            // 过载判定先于一切业务码解释——与 fetchTenantToken 过载优先同序。否则
            // 40008 会把过载响应转成"部门不存在"空页/跳过，权限码会 fail-fast 误报，
            // 未完成扫描被当正常结果放行（打标签评审反例：503/429 × 40008 → 空终页发布）。
            if (isUpstreamOverload(httpStatus)) {
                throw new RetryableSignal("upstream-system");
            }
            if (bizCode == CODE_DEPARTMENT_NOT_EXIST) {
                throw new DepartmentGoneSignal();
            }
            if (FORBIDDEN_CODES.contains(bizCode)) {
                throw forbidden("upstream-forbidden",
                        "飞书" + label + "无权限（code=" + bizCode + "，httpStatus=" + httpStatus
                                + "）——配置类错误，fail-fast；不解释为删除/离职");
            }
            if (bizCode == CODE_TOO_MANY_REQUESTS) {
                throw new RetryableSignal("rate-limited");
            }
            throw unavailable("feishu-error",
                    "飞书" + label + "返回未核对错误码（code=" + bizCode
                            + "，httpStatus=" + httpStatus + "），确定失败不重试");
        }
        if (httpStatus != 200) {
            throw unavailable("http-business-conflict",
                    "飞书" + label + "业务码为 0 但 HTTP 状态非成功（httpStatus=" + httpStatus + "）");
        }
        JsonNode data = parsed.json().path("data");
        if (!data.isObject()) {
            throw unavailable("malformed-response",
                    "飞书" + label + "响应缺少 data 对象（code=0）");
        }
        return mapper.apply(data);
    }

    private static FeishuPage<DepartmentItem> mapDepartments(JsonNode data) {
        return pageOf(data, item -> new DepartmentItem(
                requiredText(item, "open_department_id", "departments-children"),
                textOrNull(item, "parent_department_id"),
                textOrNull(item, "name")));
    }

    private static FeishuPage<PersonItem> mapPersons(JsonNode data) {
        return pageOf(data, item -> new PersonItem(
                requiredText(item, "open_id", "find-by-department"),
                textOrNull(item, "name"),
                textOrNull(item, "en_name"),
                textListOrNull(item, "department_ids")));
    }

    private static <T> FeishuPage<T> pageOf(JsonNode data, java.util.function.Function<JsonNode, T> mapper) {
        JsonNode items = data.path("items");
        if (!items.isArray()) {
            // M1-TAG-01：items 必为数组且必须在场（官方两分页端点契约均为数组字段）。
            // 缺失/null/非数组形态下分页完整性不确定，不得当"完整空目录"消费——
            // 否则未完成扫描会被当末页接受，不完整观察进入发布链。
            throw unavailable("malformed-response",
                    "飞书目录响应 items 缺失或非数组（code=0）——分页完整性不确定，不得当完整空目录");
        }
        List<T> mapped = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            mapped.add(mapper.apply(item));
        }
        JsonNode hasMoreNode = data.path("has_more");
        if (!hasMoreNode.isBoolean()) {
            // M1-TAG-01：has_more 必须为布尔且必须在场。存在但类型错误（字符串/数字）
            // 或缺失/null 一律拒绝，不得默认 false——默认末页会静默丢弃 page_token
            // 提前终止扫描（打标签评审反例：has_more:"true"/1/null 被吞成空末页发布）。
            throw unavailable("malformed-response",
                    "飞书目录响应 has_more 缺失或非布尔（code=0）——分页完整性不确定，不得默认末页");
        }
        boolean hasMore = hasMoreNode.asBoolean();
        String pageToken = textOrNull(data, "page_token");
        if (hasMore && !notBlank(pageToken)) {
            throw unavailable("malformed-response",
                    "飞书目录响应 has_more=true 但缺少 page_token（分页形态矛盾）");
        }
        return new FeishuPage<>(List.copyOf(mapped), hasMore ? pageToken : null, hasMore);
    }

    private static Instant expiryFrom(long expireSeconds) {
        if (expireSeconds > EPOCH_LIKE_THRESHOLD) {
            return Instant.ofEpochSecond(expireSeconds);
        }
        return Instant.now().plusSeconds(expireSeconds);
    }

    /** 有界重试（固定退避；仅 RetryableSignal 触发；耗尽 → Unavailable）。 */
    private <T> T withRetry(String label, Supplier<T> attempt) {
        int attempts = 1 + maxRetries;
        RetryableSignal last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return attempt.get();
            } catch (RetryableSignal retryable) {
                last = retryable;
                if (i < attempts) {
                    backoff();
                }
            }
        }
        throw unavailable(last.reasonCode(),
                "飞书" + label + "调用重试耗尽（reasonCode=" + last.reasonCode()
                        + "，attempts=" + attempts + "）——结果不确定，本次同步整代废弃");
    }

    private void backoff() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("transport", "飞书目录重试等待被中断");
        }
    }

    private static int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** W22-C1 口径：code 必须为可无损承载 long 的整数值，否则视为非法信封。 */
    private static Long strictEnvelopeCode(JsonNode json) {
        JsonNode code = json.path("code");
        if (!code.isIntegralNumber() || !code.canConvertToLong()) {
            return null;
        }
        return code.longValue();
    }

    private static boolean isUpstreamOverload(int httpStatus) {
        return httpStatus == 429 || httpStatus >= 500;
    }

    private static String requiredText(JsonNode node, String field, String label) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw unavailable("malformed-response",
                    "飞书" + label + "响应字段 " + field + " 缺失或非文本/为空（code=0）");
        }
        return value.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static List<String> textListOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw unavailable("malformed-response",
                    "飞书find-by-department响应字段 " + field + " 非数组（code=0）");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode entry : value) {
            if (!entry.isTextual()) {
                throw unavailable("malformed-response",
                        "飞书find-by-department响应字段 " + field + " 含非文本元素（code=0）");
            }
            result.add(entry.asText());
        }
        return List.copyOf(result);
    }

    private record ParsedResponse(int httpStatus, JsonNode json) {
    }

    private ParsedResponse sendForJson(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 中断 = 关停请求：终态退出，不重试（与登录客户端同口径）
            throw unavailable("transport", "飞书目录调用被中断，结果不确定");
        } catch (IOException transport) {
            throw new RetryableSignal("transport");
        }
        String body = response.body();
        JsonNode json;
        try {
            json = body == null || body.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (IOException malformed) {
            // 非法 JSON（含 5xx 网关 HTML 页）：内容已确定不可解析，不重试
            // （与 W22-C1 登录链路同口径；下轮调度在运行层重试）
            throw unavailable("malformed-response",
                    "飞书目录响应非 JSON（httpStatus=" + response.statusCode() + "）");
        }
        return new ParsedResponse(response.statusCode(), json);
    }

    private static DirectorySourceUnavailableException unavailable(String reasonCode, String message) {
        return new DirectorySourceUnavailableException(reasonCode, message);
    }

    private static DirectorySourceForbiddenException forbidden(String reasonCode, String message) {
        return new DirectorySourceForbiddenException(reasonCode, message);
    }
}
