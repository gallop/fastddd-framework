package com.gallop.fastddd.identity.feishu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 飞书端点本地 HTTP 测试桩（类别 A：协议契约验证；仅测试夹具，不进生产装配）。
 * 以 JDK HttpServer 提供 /oauth/v3/token 与 /authen/v1/user_info 两个端点，
 * 处理器可按用例编程；计数器供重试/不重试断言。
 */
final class FeishuStubServer implements AutoCloseable {

    interface EndpointHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;

    final AtomicInteger tokenHits = new AtomicInteger();

    final AtomicInteger userInfoHits = new AtomicInteger();

    private volatile EndpointHandler tokenHandler = exchange ->
            respondJson(exchange, 200, successToken());

    private volatile EndpointHandler userInfoHandler = exchange ->
            respondJson(exchange, 200, successUserInfo());

    private volatile String lastTokenRequestBody = "";

    private volatile String lastTokenContentType = "";

    private volatile String lastUserInfoAuthorization = "";

    FeishuStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/v3/token", exchange -> {
            tokenHits.incrementAndGet();
            lastTokenRequestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            lastTokenContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            tokenHandler.handle(exchange);
        });
        server.createContext("/authen/v1/user_info", exchange -> {
            userInfoHits.incrementAndGet();
            lastUserInfoAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            userInfoHandler.handle(exchange);
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void setTokenHandler(EndpointHandler handler) {
        this.tokenHandler = handler;
    }

    void setUserInfoHandler(EndpointHandler handler) {
        this.userInfoHandler = handler;
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

    static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 处理器内抛出 → 连接中止 → 客户端收到 IOException（传输异常形态）。 */
    static void dropConnection() throws IOException {
        throw new IOException("stub: connection dropped");
    }

    static String successToken() {
        return "{\"code\":0,\"access_token\":\"u-access-token\",\"expires_in\":7200,"
                + "\"token_type\":\"Bearer\",\"scope\":\"contact:user.base:readonly\"}";
    }

    static String successUserInfo() {
        return "{\"code\":0,\"msg\":\"success\",\"data\":{"
                + "\"name\":\"张三\",\"en_name\":\"San Zhang\","
                + "\"open_id\":\"ou_open_1\",\"union_id\":\"ou_union_1\","
                + "\"tenant_key\":\"tenant-1\",\"avatar_url\":\"\"}}";
    }

    static String tokenError(int code) {
        return "{\"code\":" + code + ",\"error\":\"invalid_request\","
                + "\"error_description\":\"stub described detail " + code + " (sensitive)\"}";
    }

    static String userInfoError(int code) {
        return "{\"code\":" + code + ",\"msg\":\"stub failure " + code + "\"}";
    }

    /** 典型配置（端点指向桩；重试退避缩短）。 */
    static FeishuLoginProperties propertiesAgainst(FeishuStubServer stub) {
        FeishuLoginProperties properties = new FeishuLoginProperties();
        properties.setAppId("cli_test_app");
        properties.setAppSecret("secret-test");
        properties.setRedirectUri("https://app.example.com/api/identity/login/callback");
        properties.setExpectedTenantKey("tenant-1");
        properties.setAuthorizeEndpoint("https://accounts.feishu.example/open-apis/authen/v1/authorize");
        properties.setTokenEndpoint(stub.baseUrl() + "/oauth/v3/token");
        properties.setUserInfoEndpoint(stub.baseUrl() + "/authen/v1/user_info");
        properties.getHttp().setRetryBackoff(java.time.Duration.ofMillis(20));
        return properties;
    }
}
