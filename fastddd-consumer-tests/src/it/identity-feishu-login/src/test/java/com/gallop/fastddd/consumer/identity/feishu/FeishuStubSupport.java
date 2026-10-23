package com.gallop.fastddd.consumer.identity.feishu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 飞书端点本地 HTTP 桩（消费者级端到端夹具；§五 桩边界——仅存在于测试，
 * 不进生产装配；桩通过 ≠ 真实飞书联调通过）。token v3 走 JSON 请求体（W0-4
 * 2026-09-26 复核），授权码按 code → 用户映射（deny-code → 准入拒绝主体）。
 */
final class FeishuStubSupport implements AutoCloseable {

    /** 桩行为开关：OK / REJECTED（token 确定拒绝 20008）/ TRANSPORT（连接中断）。 */
    enum Mode {
        OK, REJECTED, TRANSPORT
    }

    private final HttpServer server;

    final AtomicInteger tokenHits = new AtomicInteger();

    final AtomicInteger userInfoHits = new AtomicInteger();

    private volatile Mode mode = Mode.OK;

    private volatile String lastTokenRequestBody = "";

    private volatile String lastTokenContentType = "";

    private volatile String lastUserInfoAuthorization = "";

    /** 当前登录流的外部主体（token 处理器按 code 决定，user_info 回读）。 */
    private volatile String unionId = "ou_consumer_1";

    private FeishuStubSupport() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/v3/token", exchange -> {
            tokenHits.incrementAndGet();
            lastTokenRequestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            lastTokenContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            handleToken(exchange);
        });
        server.createContext("/authen/v1/user_info", exchange -> {
            userInfoHits.incrementAndGet();
            lastUserInfoAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            respondJson(exchange, 200, userInfoJson(unionId));
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        String body = lastTokenRequestBody;
        String code = extractJsonString(body, "code");
        switch (mode) {
            case TRANSPORT -> throw new IOException("stub: connection dropped");
            case REJECTED -> respondJson(exchange, 200,
                    "{\"code\":20008,\"error\":\"invalid_request\",\"error_description\":\"stub detail\"}");
            default -> {
                unionId = "deny-code".equals(code) ? "ou_denied" : "ou_consumer_1";
                respondJson(exchange, 200, "{\"code\":0,\"access_token\":\"u-access-token\","
                        + "\"expires_in\":7200,\"token_type\":\"Bearer\","
                        + "\"scope\":\"contact:user.base:readonly\"}");
            }
        }
    }

    private static String userInfoJson(String unionId) {
        return "{\"code\":0,\"msg\":\"success\",\"data\":{"
                + "\"name\":\"李四\",\"en_name\":\"Si Li\","
                + "\"open_id\":\"ou_open_" + unionId + "\",\"union_id\":\"" + unionId + "\","
                + "\"tenant_key\":\"consumer-tenant\",\"avatar_url\":\"\"}}";
    }

    /** 最小 JSON 字符串字段提取（测试桩专用，不引 JSON 依赖）。 */
    private static String extractJsonString(String json, String field) {
        int idx = json.indexOf("\"" + field + "\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + field.length() + 4;
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    static FeishuStubSupport start() {
        try {
            return new FeishuStubSupport();
        } catch (IOException e) {
            throw new IllegalStateException("飞书测试桩启动失败", e);
        }
    }

    String tokenUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/v3/token";
    }

    String userInfoUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/authen/v1/user_info";
    }

    void setMode(Mode mode) {
        this.mode = mode;
    }

    /** 计数器归零（用例间隔离；命中次数断言均为用例内相对值）。 */
    void resetCounters() {
        tokenHits.set(0);
        userInfoHits.set(0);
    }

    String lastTokenRequestBody() {
        return lastTokenRequestBody;
    }

    String lastTokenContentType() {
        return lastTokenContentType;
    }

    String lastUserInfoAuthorization() {
        return lastUserInfoAuthorization;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respondJson(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
