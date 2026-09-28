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
 * 以 JDK HttpServer 提供登录两端点（/oauth/v3/token、/authen/v1/user_info）与
 * 目录三端点（/auth/v3/tenant_access_token/internal、/contact/v3/departments/:id/children、
 * /contact/v3/users/find_by_department），处理器可按用例编程；计数器供重试/不重试断言。
 */
final class FeishuStubServer implements AutoCloseable {

    interface EndpointHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;

    final AtomicInteger tokenHits = new AtomicInteger();

    final AtomicInteger userInfoHits = new AtomicInteger();

    final AtomicInteger tenantTokenHits = new AtomicInteger();

    final AtomicInteger childrenHits = new AtomicInteger();

    final AtomicInteger personsHits = new AtomicInteger();

    private volatile EndpointHandler tokenHandler = exchange ->
            respondJson(exchange, 200, successToken());

    private volatile EndpointHandler userInfoHandler = exchange ->
            respondJson(exchange, 200, successUserInfo());

    private volatile EndpointHandler tenantTokenHandler = exchange ->
            respondJson(exchange, 200, tenantTokenBody("t-tenant-token", 7200));

    private volatile EndpointHandler childrenHandler = exchange ->
            respondJson(exchange, 200, departmentsPage(null, false));

    private volatile EndpointHandler personsHandler = exchange ->
            respondJson(exchange, 200, personsPage(null, false));

    private volatile String lastTokenRequestBody = "";

    private volatile String lastTokenContentType = "";

    private volatile String lastUserInfoAuthorization = "";

    private volatile String lastTenantTokenRequestBody = "";

    private volatile String lastTenantTokenContentType = "";

    private volatile String lastChildrenPath = "";

    private volatile String lastChildrenQuery = "";

    private volatile String lastContactAuthorization = "";

    private volatile String lastPersonsQuery = "";

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
        server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> {
            tenantTokenHits.incrementAndGet();
            lastTenantTokenRequestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            lastTenantTokenContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            tenantTokenHandler.handle(exchange);
        });
        server.createContext("/open-apis/contact/v3/departments", exchange -> {
            childrenHits.incrementAndGet();
            lastChildrenPath = exchange.getRequestURI().getPath();
            lastChildrenQuery = exchange.getRequestURI().getRawQuery();
            lastContactAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            childrenHandler.handle(exchange);
        });
        server.createContext("/open-apis/contact/v3/users/find_by_department", exchange -> {
            personsHits.incrementAndGet();
            lastPersonsQuery = exchange.getRequestURI().getRawQuery();
            lastContactAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            personsHandler.handle(exchange);
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

    void setTenantTokenHandler(EndpointHandler handler) {
        this.tenantTokenHandler = handler;
    }

    void setChildrenHandler(EndpointHandler handler) {
        this.childrenHandler = handler;
    }

    void setPersonsHandler(EndpointHandler handler) {
        this.personsHandler = handler;
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

    String lastTenantTokenRequestBody() {
        return lastTenantTokenRequestBody;
    }

    String lastTenantTokenContentType() {
        return lastTenantTokenContentType;
    }

    /** 最近一次部门子级请求的完整路径（如 /contact/v3/departments/0/children）。 */
    String lastChildrenPath() {
        return lastChildrenPath;
    }

    String lastChildrenQuery() {
        return lastChildrenQuery == null ? "" : lastChildrenQuery;
    }

    /** 最近一次目录请求（children/persons 任一）的 Authorization 头。 */
    String lastContactAuthorization() {
        return lastContactAuthorization;
    }

    String lastPersonsQuery() {
        return lastPersonsQuery == null ? "" : lastPersonsQuery;
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

    // ===== 目录端点（W3-b）响应构造 =====

    /** tenant_access_token/internal 成功体（平铺信封；expire=有效期秒）。 */
    static String tenantTokenBody(String token, int expireSeconds) {
        return "{\"code\":0,\"expire\":" + expireSeconds + ",\"tenant_access_token\":\"" + token + "\"}";
    }

    /** 目录信封业务错误体（HTTP 状态由处理器另行指定）。 */
    static String contactError(int code) {
        return "{\"code\":" + code + ",\"msg\":\"stub failure " + code + "\"}";
    }

    /** 部门子级成功页（has_more=false 时省略 page_token——对齐官方形态）。 */
    static String departmentsPage(String pageToken, boolean hasMore, String... departments) {
        StringBuilder items = new StringBuilder();
        for (String department : departments) {
            if (items.length() > 0) {
                items.append(',');
            }
            items.append(department);
        }
        return "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":" + hasMore
                + (pageToken != null ? ",\"page_token\":\"" + pageToken + "\"" : "")
                + ",\"items\":[" + items + "]}}";
    }

    static String childDepartment(String openDepartmentId, String parentDepartmentId, String name) {
        return "{\"open_department_id\":\"" + openDepartmentId + "\",\"department_id\":\"custom-"
                + openDepartmentId + "\",\"parent_department_id\":\"" + parentDepartmentId
                + "\",\"name\":\"" + name + "\",\"member_count\":1,"
                + "\"status\":{\"is_deleted\":false},\"order\":\"1\"}";
    }

    /** 按部门查人员成功页。 */
    static String personsPage(String pageToken, boolean hasMore, String... users) {
        StringBuilder items = new StringBuilder();
        for (String user : users) {
            if (items.length() > 0) {
                items.append(',');
            }
            items.append(user);
        }
        return "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":" + hasMore
                + (pageToken != null ? ",\"page_token\":\"" + pageToken + "\"" : "")
                + ",\"items\":[" + items + "]}}";
    }

    static String personUser(String openId, String name, String enName, String... departmentIds) {
        StringBuilder departments = new StringBuilder();
        for (String departmentId : departmentIds) {
            if (departments.length() > 0) {
                departments.append(',');
            }
            departments.append('"').append(departmentId).append('"');
        }
        return "{\"open_id\":\"" + openId + "\",\"union_id\":\"un-" + openId + "\","
                + "\"name\":\"" + name + "\",\"en_name\":\"" + enName + "\","
                + "\"department_ids\":[" + departments + "]}";
    }

    /** 查询串取参（无该参数返回 null）。 */
    static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /** 目录源典型配置（端点指向桩；重试退避缩短）。 */
    static FeishuDirectoryProperties directoryPropertiesAgainst(FeishuStubServer stub) {
        FeishuDirectoryProperties properties = new FeishuDirectoryProperties();
        properties.setApiBaseUrl(stub.baseUrl());
        return properties;
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
