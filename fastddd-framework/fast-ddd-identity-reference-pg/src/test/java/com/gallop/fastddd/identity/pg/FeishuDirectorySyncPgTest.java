package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.feishu.FeishuDirectoryProperties;
import com.gallop.fastddd.identity.feishu.FeishuDirectorySource;
import com.gallop.fastddd.identity.feishu.FeishuLoginProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M1-TAG-01/02 补修全链回归（打标签评审 2026-10-01 两项 P2；评审关闭证据 = "不得
 * 仅在替身 source 上抛异常"）：真实 {@link FeishuDirectorySource}（本地三端点 HTTP
 * 桩，可注入原始故障响应——与评审探针同输入形态）× 真实
 * {@link PgDirectoryMirrorStore}/{@link DirectorySyncUseCase}（zonky 一次性 PG）。
 *
 * <p>异常轮（TAG-01 畸形分页标记 / TAG-02 过载 × 40008）断言：整代 ABANDONED、
 * abandon_reason=source-unavailable、旧活动代/水位（committed_seq=applied_seq=1）/
 * 事实数/pending_confirm 零置位全部不变（评审 bug 形态即 "people 1-&gt;0 pending=1"
 * 的错误发布）；正对照（正常多页 + 合法空终页 / 合法 40008 部门不存在）继续发布。
 * 替身源矩阵 3/4 见 {@link DirectorySyncOrchestrationPgTest}（保留）。</p>
 */
class FeishuDirectorySyncPgTest {

    private static final String TENANT = "tenant-pg";
    private static final String APP = "cli_pg_app";
    private static final DirectoryScope SCOPE = new DirectoryScope(TENANT, APP);

    private StubServer stub;
    private DataSource dataSource;
    private PgDirectoryMirrorStore store;
    private IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        store = new PgDirectoryMirrorStore(new JdbcTemplate(dataSource), Instant::now, Duration.ofMinutes(10));
        runner = Support.runner(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    /** 重试配额取框架缺省（2 次附加重试）；退避缩短以控制用例时长。 */
    private FeishuDirectorySource source() {
        FeishuLoginProperties login = new FeishuLoginProperties();
        login.setAppId(APP);
        login.setAppSecret("secret-pg");
        login.setExpectedTenantKey(TENANT);
        FeishuDirectoryProperties directory = new FeishuDirectoryProperties();
        directory.setApiBaseUrl("http://127.0.0.1:" + stub.port());
        return new FeishuDirectorySource(login, directory, 2, Duration.ofMillis(20));
    }

    private DirectorySyncUseCase useCase() {
        return new DirectorySyncUseCase(source(), store, runner, 20, 100);
    }

    /** 健康部门树路由：根(0) 下 od-a/od-b，其余父部门空页（TAG 反例测试共用）。 */
    private void stubHealthyDepartments() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                respondJson(exchange, 200,
                        departmentsPage(null, false, childDept("od-a", "0"), childDept("od-b", "0")));
            } else {
                respondJson(exchange, 200, departmentsPage(null, false));
            }
        });
    }

    // ------------------ 异常轮：整代废弃、旧世界原样 ------------------

    /**
     * M1-TAG-01（打标签评审反例形态）：persons 页 HTTP 200/code=0 但
     * has_more 非布尔（"true" 字符串）或分页字段缺失——修订前被默认末页空集
     * 消费、未完成扫描发布；修订后 malformed-response 拒绝（不重试）→ 整代废弃。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":\"true\",\"page_token\":\"pt-next\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{}}"})
    void tag01_malformedPagingMarkerAbandonsGenerationAndOldWorldStays(String anomalyBody) {
        GenerationHandle baseline = publishBaseline();
        stubHealthyDepartments();
        stub.setPersonsHandler(exchange -> {
            String dept = queryParam(exchange, "department_id");
            if ("od-a".equals(dept)) {
                respondJson(exchange, 200, anomalyBody);
            } else {
                respondJson(exchange, 200, personsPage(null, false));
            }
        });

        assertThatThrownBy(() -> useCase().runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");

        // 畸形属确定失败不重试：persons("0") 合法 1 击 + od-a 反例体 1 击
        assertThat(stub.personsHits.get()).isEqualTo(2);
        assertAbandonedAndOldWorldIntact(baseline);
    }

    /**
     * M1-TAG-02（打标签评审反例形态）：persons 页 429/503 × 40008——修订前先按
     * "部门不存在"吞成空终页发布；修订后过载优先（信封不可信）有界重试
     * （1+2 共 3 击）耗尽 → upstream-system Unavailable → 整代废弃。
     */
    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void tag02_overloadWithDepartmentGoneCodeAbandonsGenerationAndOldWorldStays(int status) {
        GenerationHandle baseline = publishBaseline();
        stubHealthyDepartments();
        stub.setPersonsHandler(exchange -> {
            String dept = queryParam(exchange, "department_id");
            if ("od-a".equals(dept)) {
                respondJson(exchange, status, "{\"code\":40008,\"msg\":\"stub overload\"}");
            } else {
                respondJson(exchange, 200, personsPage(null, false));
            }
        });

        assertThatThrownBy(() -> useCase().runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");

        // persons("0") 合法 1 击 + od-a 过载 3 击（有界重试上限验证）
        assertThat(stub.personsHits.get()).isEqualTo(4);
        assertAbandonedAndOldWorldIntact(baseline);
    }

    // ------------------ 正对照：合法行为继续发布 ------------------

    /** 正常多页分页（部门/人员双流）+ 合法空终页（部门无人员/末页空）→ 发布成功。 */
    @Test
    void normalPaginationAndLegalEmptyLastPagesStillPublish() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                if (queryParam(exchange, "page_token") == null) {
                    respondJson(exchange, 200,
                            departmentsPage("ct-2", true, childDept("od-a", "0")));
                } else {
                    respondJson(exchange, 200,
                            departmentsPage(null, false, childDept("od-b", "0")));
                }
            } else {
                respondJson(exchange, 200, departmentsPage(null, false));
            }
        });
        stub.setPersonsHandler(exchange -> {
            String dept = queryParam(exchange, "department_id");
            if ("0".equals(dept)) {
                if (queryParam(exchange, "page_token") == null) {
                    respondJson(exchange, 200,
                            personsPage("pt-1", true, person("ou_0", "根直属", "0")));
                } else {
                    // 合法空终页（has_more=false + 空数组在场的完整形态）
                    respondJson(exchange, 200, personsPage(null, false));
                }
            } else if ("od-a".equals(dept)) {
                respondJson(exchange, 200, personsPage(null, false, person("ou_1", "张三", "od-a")));
            } else {
                respondJson(exchange, 200, personsPage(null, false));
            }
        });

        DirectoryPublication publication = useCase().runFullSync(SCOPE);

        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);
        assertThat(publication.factsInserted()).isEqualTo(5);
        assertThat(publication.generationId()).isEqualTo(activeGeneration());
        assertThat(Support.count(dataSource, "directory_fact")).isEqualTo(5);
        assertThat(pendingCount()).isZero();
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.departmentCount()).isEqualTo(3);
        assertThat(observation.personCount()).isEqualTo(2);
    }

    /**
     * 合法"部门不存在"正对照（TAG-02 修复不得收窄既有契约）：非过载形态
     * （HTTP 400）× 40008 —— children 跳过父部门继续 BFS、persons 返回空终页，
     * 同步整体完成并发布。
     */
    @Test
    void legalDepartmentGoneStillSkipsAndPublishes() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                respondJson(exchange, 200,
                        departmentsPage(null, false, childDept("od-a", "0"), childDept("od-b", "0")));
            } else if (path.endsWith("/departments/od-b/children")) {
                respondJson(exchange, 400, "{\"code\":40008,\"msg\":\"department not exist\"}");
            } else {
                respondJson(exchange, 200, departmentsPage(null, false));
            }
        });
        stub.setPersonsHandler(exchange -> {
            String dept = queryParam(exchange, "department_id");
            if ("od-a".equals(dept)) {
                respondJson(exchange, 200, personsPage(null, false, person("ou_1", "张三", "od-a")));
            } else if ("od-b".equals(dept)) {
                respondJson(exchange, 400, "{\"code\":40008,\"msg\":\"department not exist\"}");
            } else {
                respondJson(exchange, 200, personsPage(null, false));
            }
        });

        DirectoryPublication publication = useCase().runFullSync(SCOPE);

        // od-b 本体已观察（子级枚举被跳过）；部门 = 根+od-a+od-b，人员 = OU1
        assertThat(publication.factsInserted()).isEqualTo(4);
        assertThat(store.readObservation(SCOPE).personCount()).isEqualTo(1);
        assertThat(pendingCount()).isZero();
        // 合法 gone 属确定语义不重试：children 3 击（0/od-a/od-b）、persons 3 击（0/od-a/od-b）
        assertThat(stub.childrenHits.get()).isEqualTo(3);
        assertThat(stub.personsHits.get()).isEqualTo(3);
    }

    // ---------------------------------------------------------- 断言与基线助手

    /** 异常轮统一断言：整代 ABANDONED + 旧活动代/水位/事实/pending 全不变 + 租约释放。 */
    private void assertAbandonedAndOldWorldIntact(GenerationHandle baseline) {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_generation WHERE state='ABANDONED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT abandon_reason FROM directory_generation WHERE state='ABANDONED'", String.class))
                .isEqualTo("source-unavailable");
        assertThat(activeGeneration()).isEqualTo(baseline.generationId());
        assertThat(Support.count(dataSource, "directory_fact")).isEqualTo(4);
        // 评审 bug 形态 = 失败轮把旧人员置待核对（pending=1）——修复后零置位
        assertThat(pendingCount()).isZero();
        assertThat(committedSeq()).isEqualTo(1);
        assertThat(appliedSeq()).isEqualTo(1);
        assertThat(leaseToken()).isNull();

        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.activeGenerationId()).contains(baseline.generationId());
        assertThat(observation.departmentCount()).isEqualTo(2);
        assertThat(observation.personCount()).isEqualTo(2);
        assertThat(observation.committedSeq()).isEqualTo(1);
        assertThat(observation.appliedSeq()).isEqualTo(1);
    }

    /** 直接经存储建代并发布基线（D1/D2 + OU1/OU2，H=1、facts=4）——失败轮的"旧世界"。 */
    private GenerationHandle publishBaseline() {
        GenerationHandle handle = runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> {
            store.writeBatch(handle, com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofDepartments(
                    List.of(new DirectoryDepartment("D1", null, "总部"),
                            new DirectoryDepartment("D2", null, "研发"))));
            return null;
        });
        runner.inTransaction(() -> {
            store.writeBatch(handle, com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofPersons(
                    List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                            new DirectoryPerson("OU2", "李四", null, List.of("D2")))));
            return null;
        });
        runner.inTransaction(() -> {
            store.completePaging(handle);
            return null;
        });
        runner.inTransaction(() -> {
            store.validate(handle);
            return null;
        });
        runner.inTransaction(() -> store.publish(handle));
        return handle;
    }

    private String activeGeneration() {
        return jdbc.queryForObject(
                "SELECT active_generation FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private long committedSeq() {
        return jdbc.queryForObject(
                "SELECT committed_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private long appliedSeq() {
        return jdbc.queryForObject(
                "SELECT applied_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private String leaseToken() {
        return jdbc.queryForObject(
                "SELECT lease_token FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private int pendingCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM directory_fact WHERE pending_confirm", Integer.class);
    }

    // ---------------------------------------------------------- 最小本地桩

    /**
     * 最小三端点本地桩（与评审探针同形态）：tenant token / 部门子级 / 按部门查人员，
     * 每端点处理逻辑可注入（含原始 status/body 故障），携带命中计数。
     */
    static final class StubServer {

        @FunctionalInterface
        interface Handler {
            void handle(HttpExchange exchange) throws IOException;
        }

        private final HttpServer server;

        final AtomicInteger childrenHits = new AtomicInteger();
        final AtomicInteger personsHits = new AtomicInteger();
        final AtomicInteger tenantTokenHits = new AtomicInteger();

        private volatile Handler childrenHandler =
                exchange -> respondJson(exchange, 200, departmentsPage(null, false));
        private volatile Handler personsHandler =
                exchange -> respondJson(exchange, 200, personsPage(null, false));
        private volatile Handler tenantTokenHandler = exchange ->
                respondJson(exchange, 200,
                        "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-pg\",\"expire\":7200}");

        StubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> {
                tenantTokenHits.incrementAndGet();
                tenantTokenHandler.handle(exchange);
            });
            server.createContext("/open-apis/contact/v3/departments/", exchange -> {
                childrenHits.incrementAndGet();
                childrenHandler.handle(exchange);
            });
            server.createContext("/open-apis/contact/v3/users/find_by_department", exchange -> {
                personsHits.incrementAndGet();
                personsHandler.handle(exchange);
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        void setChildrenHandler(Handler handler) {
            this.childrenHandler = handler;
        }

        void setPersonsHandler(Handler handler) {
            this.personsHandler = handler;
        }

        void close() {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------- 桩静态助手

    static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String queryParam(HttpExchange exchange, String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static String childDept(String id, String parent) {
        return "{\"open_department_id\":\"" + id + "\",\"parent_department_id\":\"" + parent
                + "\",\"name\":\"部门" + id + "\"}";
    }

    static String person(String openId, String name, String... departmentIds) {
        StringBuilder sb = new StringBuilder("{\"open_id\":\"").append(openId)
                .append("\",\"name\":\"").append(name).append("\",\"department_ids\":[");
        for (int i = 0; i < departmentIds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(departmentIds[i]).append('"');
        }
        return sb.append("]}").toString();
    }

    /** 完整合法分页形态（has_more 布尔在场 + items 数组在场；pageToken 仅在 hasMore 时携带）。 */
    static String departmentsPage(String pageToken, boolean hasMore, String... departments) {
        StringBuilder sb = new StringBuilder("{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":").append(hasMore);
        if (pageToken != null) {
            sb.append(",\"page_token\":\"").append(pageToken).append('"');
        }
        sb.append(",\"items\":[");
        for (int i = 0; i < departments.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(departments[i]);
        }
        return sb.append("]}}").toString();
    }

    static String personsPage(String pageToken, boolean hasMore, String... persons) {
        StringBuilder sb = new StringBuilder("{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":").append(hasMore);
        if (pageToken != null) {
            sb.append(",\"page_token\":\"").append(pageToken).append('"');
        }
        sb.append(",\"items\":[");
        for (int i = 0; i < persons.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(persons[i]);
        }
        return sb.append("]}}").toString();
    }
}
