package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.tck.DirectorySourceHarness;
import com.gallop.fastddd.identity.core.tck.DirectoryUpstreamScript;
import com.gallop.fastddd.identity.core.tck.UpstreamResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * W7-d TCK 宿主实现：把 {@link UpstreamResponse} 语义词表渲染为 feishu 真实线协议形态
 * （HTTP 状态 × 信封字段 × 响应时延），经 {@link FeishuStubServer} 驱动参考实现
 * {@link FeishuDirectorySource} 跑全套端口契约断言（dogfood）。
 *
 * <p>渲染映射（M1 冻结错误分类，W3-b/W5 系）：权限 = 230002；限流 = 99991400；
 * 过载 = 5xx × 20051（TAG-02 过载优先）；未知业务码兜底 = 30001；部门并发删除 = 40008；
 * 读超时 = 慢响应（600ms）× 客户端读超时（150ms）后断连。畸形成功信封按
 * {@link UpstreamResponse.MalformedShape} 八形态手工渲染（200 × 反面信封）。
 */
final class TckFeishuHarness implements DirectorySourceHarness, AutoCloseable {

    private final FeishuStubServer stub;
    private final RecordingScript script = new RecordingScript();
    private final FeishuLoginProperties loginProperties;
    private final FeishuDirectoryProperties directoryProperties;

    TckFeishuHarness() {
        try {
            this.stub = new FeishuStubServer();
        } catch (IOException e) {
            // 桩启动失败 = 测试基础设施故障，非被测行为；fail-loud 包装
            throw new IllegalStateException("TCK 桩服务启动失败", e);
        }
        this.loginProperties = FeishuStubServer.propertiesAgainst(stub);
        this.directoryProperties = FeishuStubServer.directoryPropertiesAgainst(stub);
        // 读超时收窄：ReadTimeout 形态 = 响应慢于客户端读超时（600ms 慢响应 × 150ms 读超时）
        this.directoryProperties.setReadTimeout(Duration.ofMillis(150));
        installHandlers();
    }

    @Override
    public DirectoryUpstreamScript newScript() {
        return script;
    }

    @Override
    public DirectorySource newSource(DirectoryUpstreamScript script) {
        return new FeishuDirectorySource(loginProperties, directoryProperties, 2, Duration.ofMillis(20));
    }

    @Override
    public DirectorySource newSourceWithMissingCredentials(DirectoryUpstreamScript script) {
        // 三键（app-id/app-secret/expected-tenant-key）全留空 → 构造期 fail-closed（零外呼）
        FeishuLoginProperties missing = new FeishuLoginProperties();
        return new FeishuDirectorySource(missing, directoryProperties, 2, Duration.ofMillis(20));
    }

    @Override
    public int transientRetryBudget() {
        // 与 newSource 的 maxRetries=2（附加重试）一致；单次外呼总尝试上限 = 3
        return 2;
    }

    @Override
    public DirectoryScope expectedScope() {
        return new DirectoryScope(loginProperties.getExpectedTenantKey(), loginProperties.getAppId());
    }

    @Override
    public void close() throws IOException {
        stub.close();
    }

    // ---- 桩端点安装：上游外呼 → script 消费 → 线协议渲染 ----

    private void installHandlers() {
        stub.setTenantTokenHandler(exchange -> {
            script.recordAuthEstablishment();
            UpstreamResponse sticky = script.pollAuthEstablishmentFailure();
            if (sticky != null) {
                render(exchange, StreamKind.AUTH, sticky);
            } else {
                FeishuStubServer.respondJson(exchange, 200,
                        FeishuStubServer.tenantTokenBody("tck-token", 3600));
            }
        });
        stub.setChildrenHandler(exchange -> {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            int pageSize = parseIntOrDefault(FeishuStubServer.queryParam(rawQuery, "page_size"));
            String pageToken = FeishuStubServer.queryParam(rawQuery, "page_token");
            render(exchange, StreamKind.DEPARTMENTS, script.pollDepartment(pageSize, pageToken));
        });
        stub.setPersonsHandler(exchange -> {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            String departmentId = FeishuStubServer.queryParam(rawQuery, "department_id");
            int pageSize = parseIntOrDefault(FeishuStubServer.queryParam(rawQuery, "page_size"));
            String pageToken = FeishuStubServer.queryParam(rawQuery, "page_token");
            render(exchange, StreamKind.PERSONS, script.pollPerson(departmentId, pageSize, pageToken));
        });
    }

    private static int parseIntOrDefault(String value) {
        return value == null ? Integer.MAX_VALUE : Integer.parseInt(value);
    }

    // ---- 渲染：语义形态 → feishu 线协议 ----

    /** 三条上游流（AUTH 渲染域窄：凭据拒绝 + 瞬态族）。 */
    private enum StreamKind {AUTH, DEPARTMENTS, PERSONS}

    private void render(HttpExchange exchange, StreamKind kind, UpstreamResponse response) throws IOException {
        if (kind == StreamKind.AUTH) {
            switch (response) {
                case UpstreamResponse.AuthEstablishmentDenied d ->
                        FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.tokenError(230002));
                case UpstreamResponse.RateLimited r ->
                        FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.tokenError(99991400));
                case UpstreamResponse.UpstreamOverloaded o ->
                        FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.tokenError(20051));
                case UpstreamResponse.ReadTimeout t -> sleepThenDrop();
                default -> throw new IllegalArgumentException(
                        "AUTH 流仅支持凭据拒绝与瞬态族渲染，收到: " + response);
            }
            return;
        }
        switch (response) {
            case UpstreamResponse.DepartmentsPage p -> {
                String[] items = p.items().stream()
                        .map(d -> FeishuStubServer.childDepartment(d.id(), d.parentId(), d.name()))
                        .toArray(String[]::new);
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(
                        p.nextToken() == null ? "" : p.nextToken(), p.hasMore(), items));
            }
            case UpstreamResponse.PersonsPage p -> {
                String[] items = p.items().stream()
                        .map(u -> FeishuStubServer.personUser(u.openId(), u.name(), u.enName(),
                                u.departmentIds().toArray(String[]::new)))
                        .toArray(String[]::new);
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage(
                        p.nextToken() == null ? "" : p.nextToken(), p.hasMore(), items));
            }
            case UpstreamResponse.PermissionDenied d ->
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(230002));
            case UpstreamResponse.AuthEstablishmentDenied d ->
                    // 数据端点不应出现凭据形态（f3 已断言零数据外呼）；防御渲染为权限拒绝
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(230002));
            case UpstreamResponse.RateLimited r ->
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(99991400));
            case UpstreamResponse.UpstreamOverloaded o ->
                    FeishuStubServer.respondJson(exchange, 503, FeishuStubServer.contactError(20051));
            case UpstreamResponse.ReadTimeout t -> sleepThenDrop();
            case UpstreamResponse.DeterminateFailure f ->
                    // 未核对业务码（30001 非冻结表成员）→ 兜底确定失败不重试
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(30001));
            case UpstreamResponse.DepartmentGone g ->
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(40008));
            case UpstreamResponse.MalformedSuccess m ->
                    renderMalformed(exchange, m.shape(), kind);
        }
    }

    /** 畸形成功信封八形态（全部 HTTP 200 × 反面信封——W22-C1 严格三要素反面）。 */
    private static void renderMalformed(HttpExchange exchange, UpstreamResponse.MalformedShape shape,
                                        StreamKind kind) throws IOException {
        String body = switch (shape) {
            case NON_JSON_BODY -> "not-json-at-all";
            case SUCCESS_FLAG_MISSING -> "{\"data\":{\"items\":[],\"has_more\":false}}";
            case SUCCESS_FLAG_WRONG_TYPE -> "{\"code\":\"0\",\"data\":{\"items\":[],\"has_more\":false}}";
            case PAGER_FLAG_MISSING -> "{\"code\":0,\"data\":{\"items\":[]}}";
            case PAGER_FLAG_WRONG_TYPE -> "{\"code\":0,\"data\":{\"items\":[],\"has_more\":\"no\"}}";
            case ITEMS_MISSING -> "{\"code\":0,\"data\":{\"has_more\":false}}";
            case ITEMS_WRONG_TYPE -> "{\"code\":0,\"data\":{\"items\":{},\"has_more\":false}}";
            case ITEM_MISSING_REQUIRED_FIELD -> kind == StreamKind.PERSONS
                    ? "{\"code\":0,\"data\":{\"items\":[{\"name\":\"缺键人员\"}],\"has_more\":false}}"
                    : "{\"code\":0,\"data\":{\"items\":[{\"name\":\"缺键部门\"}],\"has_more\":false}}";
        };
        FeishuStubServer.respondJson(exchange, 200, body);
    }

    private static void sleepThenDrop() throws IOException {
        try {
            Thread.sleep(600);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        FeishuStubServer.dropConnection();
    }

    // ---- 上游脚本实现：FIFO 队列 + 粘性失败 + 观测（线程安全：桩 handler 与断言线程共用） ----

    private static final class RecordingScript implements DirectoryUpstreamScript {

        private final ConcurrentLinkedQueue<UpstreamResponse> departmentQueue = new ConcurrentLinkedQueue<>();
        private final AtomicReference<UpstreamResponse> departmentSticky = new AtomicReference<>();
        private final ConcurrentHashMap<String, ConcurrentLinkedQueue<UpstreamResponse>> personQueues =
                new ConcurrentHashMap<>();
        private final AtomicReference<UpstreamResponse> authSticky = new AtomicReference<>();
        private final CopyOnWriteArrayList<Integer> departmentPageSizes = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> departmentCursors = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>> personPageSizes =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> personCursors =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicInteger> personCallCounters = new ConcurrentHashMap<>();
        private final AtomicInteger departmentCallCount = new AtomicInteger();
        private final AtomicInteger authCallCount = new AtomicInteger();

        @Override
        public void playDepartments(UpstreamResponse... responses) {
            departmentQueue.addAll(List.of(responses));
        }

        @Override
        public void failDepartmentsWith(UpstreamResponse stickyFailure) {
            departmentSticky.set(stickyFailure);
        }

        @Override
        public void playPersons(String departmentId, UpstreamResponse... responses) {
            personQueues.computeIfAbsent(departmentId, k -> new ConcurrentLinkedQueue<>())
                    .addAll(List.of(responses));
        }

        @Override
        public void failAuthEstablishmentWith(UpstreamResponse stickyFailure) {
            authSticky.set(stickyFailure);
        }

        @Override
        public UpstreamResponse pollDepartment(int pageSize, String cursor) {
            departmentCallCount.incrementAndGet();
            departmentPageSizes.add(pageSize);
            departmentCursors.add(cursor);
            UpstreamResponse sticky = departmentSticky.get();
            if (sticky != null) {
                return sticky;
            }
            UpstreamResponse next = departmentQueue.poll();
            return next != null ? next : new UpstreamResponse.DepartmentsPage(List.of(), null, false);
        }

        @Override
        public UpstreamResponse pollPerson(String departmentId, int pageSize, String cursor) {
            personCallCounters.computeIfAbsent(departmentId, k -> new AtomicInteger()).incrementAndGet();
            personPageSizes.computeIfAbsent(departmentId, k -> new CopyOnWriteArrayList<>()).add(pageSize);
            personCursors.computeIfAbsent(departmentId, k -> new CopyOnWriteArrayList<>()).add(cursor);
            ConcurrentLinkedQueue<UpstreamResponse> queue = personQueues.get(departmentId);
            UpstreamResponse next = queue == null ? null : queue.poll();
            return next != null ? next : new UpstreamResponse.PersonsPage(List.of(), null, false);
        }

        @Override
        public UpstreamResponse pollAuthEstablishmentFailure() {
            return authSticky.get();
        }

        @Override
        public void recordAuthEstablishment() {
            authCallCount.incrementAndGet();
        }

        @Override
        public int departmentCallCount() {
            return departmentCallCount.get();
        }

        @Override
        public List<Integer> observedDepartmentPageSizes() {
            return List.copyOf(departmentPageSizes);
        }

        @Override
        public List<String> observedDepartmentCursors() {
            // 首頁外呼可不帶游標（null 合法形態）——null 容忍拷貝（List.copyOf 拒 null）
            return new ArrayList<>(departmentCursors);
        }

        @Override
        public int personCallCount(String departmentId) {
            AtomicInteger counter = personCallCounters.get(departmentId);
            return counter == null ? 0 : counter.get();
        }

        @Override
        public List<Integer> observedPersonPageSizes(String departmentId) {
            CopyOnWriteArrayList<Integer> sizes = personPageSizes.get(departmentId);
            return sizes == null ? List.of() : List.copyOf(sizes);
        }

        @Override
        public List<String> observedPersonCursors(String departmentId) {
            CopyOnWriteArrayList<String> cursors = personCursors.get(departmentId);
            return cursors == null ? List.of() : new ArrayList<>(cursors);
        }

        @Override
        public int authEstablishmentCallCount() {
            return authCallCount.get();
        }

        @Override
        public int totalUpstreamCallCount() {
            int personTotal = personCallCounters.values().stream()
                    .mapToInt(AtomicInteger::get).sum();
            return authCallCount.get() + departmentCallCount.get() + personTotal;
        }
    }
}
