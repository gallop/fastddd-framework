package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类别 A：飞书目录源 HTTP 协议契约（本地 HttpServer 桩）——请求形态/BFS 分页/
 * 错误分类/有界重试/边界语义。协议依据 W0-4 §1.3/1.4（2026-09-28 编码前官方复核：
 * tenant_access_token/internal、departments/:id/children、users/find_by_department）。
 * 真实飞书环境未接入（W3-b 外部未验证项，见执行记录）。
 */
class FeishuDirectorySourceContractTest {

    private FeishuStubServer stub;

    private FeishuLoginProperties properties;

    private FeishuDirectoryProperties directoryProperties;

    /** 重试配额取框架缺省（2 次附加重试）；退避缩短以控制用例时长。 */
    private FeishuDirectorySource source() {
        return new FeishuDirectorySource(properties, directoryProperties, 2, Duration.ofMillis(20));
    }

    @BeforeEach
    void setUp() throws IOException {
        stub = new FeishuStubServer();
        properties = FeishuStubServer.propertiesAgainst(stub);
        directoryProperties = FeishuStubServer.directoryPropertiesAgainst(stub);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static DirectoryPageQuery firstPage(int pageSize) {
        return DirectoryPageQuery.firstPage(pageSize);
    }

    /** 逐页拉全部门流（首页合成根 → BFS 游标推进至终页）。 */
    private List<DirectoryDepartment> walkDepartments(FeishuDirectorySource source, int pageSize) {
        List<DirectoryDepartment> all = new ArrayList<>();
        DirectoryPage<DirectoryDepartment> page = source.departments(firstPage(pageSize));
        all.addAll(page.items());
        while (page.hasMore()) {
            page = source.departments(new DirectoryPageQuery(page.nextCursor(), pageSize));
            all.addAll(page.items());
        }
        return all;
    }

    /** 标准三层树：root(0) → od-a / od-b；od-a → od-a1。全部单页返回。 */
    private void stubStandardTree() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                        FeishuStubServer.childDepartment("od-a", "0", "部门A"),
                        FeishuStubServer.childDepartment("od-b", "0", "部门B")));
            } else if (path.endsWith("/departments/od-a/children")) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                        FeishuStubServer.childDepartment("od-a1", "od-a", "子部门A1")));
            } else {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false));
            }
        });
    }

    private String childrenCursor(FeishuDirectorySource source) {
        return source.departments(firstPage(50)).nextCursor();
    }

    // ------------------ 正常分页与映射 ------------------

    /** 首页 = 合成根部门（不外呼；children 接口不含根本身，人员流需要根条目覆盖根直属人员）。 */
    @Test
    void firstPageIsSyntheticRootWithoutHttpCall() {
        DirectoryPage<DirectoryDepartment> first = source().departments(firstPage(100));

        assertThat(first.items()).containsExactly(new DirectoryDepartment("0", null, null));
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(stub.childrenHits.get()).isZero();
        assertThat(stub.tenantTokenHits.get()).isZero();
    }

    /** BFS 多层遍历 + page_size 收缩（100 → 官方上限 50，不得放大）+ 父引用映射 + 单 token 复用。 */
    @Test
    void departmentsWalkBfsMultiLevelWithClampAndParentMapping() {
        stubStandardTree();

        List<DirectoryDepartment> all = walkDepartments(source(), 100);

        assertThat(all).hasSize(4);
        assertThat(all.get(0).departmentId()).isEqualTo("0");
        assertThat(all.get(0).parentDepartmentId()).isNull();
        // 根的子部门保留 parent="0"（合成根在流中，引用可解析——不虚构归一化）
        assertThat(all.get(1).departmentId()).isEqualTo("od-a");
        assertThat(all.get(1).parentDepartmentId()).isEqualTo("0");
        assertThat(all.get(1).name()).isEqualTo("部门A");
        assertThat(all.get(2).departmentId()).isEqualTo("od-b");
        assertThat(all.get(3).departmentId()).isEqualTo("od-a1");
        assertThat(all.get(3).parentDepartmentId()).isEqualTo("od-a");

        assertThat(stub.lastChildrenPath()).isEqualTo("/open-apis/contact/v3/departments/od-a1/children");
        assertThat(stub.lastChildrenQuery())
                .contains("page_size=50")
                .contains("fetch_child=false")
                .contains("department_id_type=open_department_id");
        // 遍历全程复用一个 tenant token（expire=7200 > 30 分钟换新余量）
        assertThat(stub.tenantTokenHits.get()).isEqualTo(1);
        assertThat(stub.lastContactAuthorization()).isEqualTo("Bearer t-tenant-token");
    }

    /** tenant token 请求形态：POST JSON，app_id/app_secret 齐备（官方契约）。 */
    @Test
    void tenantTokenRequestIsJsonBodyWithCredentials() {
        stubStandardTree();
        walkDepartments(source(), 50);

        assertThat(stub.lastTenantTokenContentType()).contains("application/json");
        assertThat(stub.lastTenantTokenRequestBody()).contains("\"app_id\":\"cli_test_app\"");
        assertThat(stub.lastTenantTokenRequestBody()).contains("\"app_secret\":\"secret-test\"");
    }

    /** 同一父部门多页：page_token 透传、父不变；父枚举完毕后队列推进至终页（nextCursor=null）。 */
    @Test
    void departmentsMidParentPaginationKeepsParentAndTerminates() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                        FeishuStubServer.childDepartment("od-a", "0", "部门A")));
            } else if (path.endsWith("/departments/od-a/children")) {
                if (FeishuStubServer.queryParam(exchange.getRequestURI().getRawQuery(), "page_token") == null) {
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage("ct-a2", true,
                            FeishuStubServer.childDepartment("od-a1", "od-a", "A1")));
                } else {
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                            FeishuStubServer.childDepartment("od-a2", "od-a", "A2")));
                }
            } else {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false));
            }
        });
        FeishuDirectorySource source = source();

        DirectoryPage<DirectoryDepartment> root = source.departments(firstPage(50));
        DirectoryPage<DirectoryDepartment> page2 = source.departments(new DirectoryPageQuery(root.nextCursor(), 50));
        assertThat(page2.items()).extracting(DirectoryDepartment::departmentId).containsExactly("od-a");

        DirectoryPage<DirectoryDepartment> page3 = source.departments(new DirectoryPageQuery(page2.nextCursor(), 50));
        assertThat(page3.items()).extracting(DirectoryDepartment::departmentId).containsExactly("od-a1");
        assertThat(page3.hasMore()).isTrue();
        assertThat(page3.nextCursor()).isNotBlank();

        DirectoryPage<DirectoryDepartment> page4 = source.departments(new DirectoryPageQuery(page3.nextCursor(), 50));
        // 上一页响应的 page_token 经游标透传到本页请求
        assertThat(FeishuStubServer.queryParam(stub.lastChildrenQuery(), "page_token")).isEqualTo("ct-a2");
        assertThat(page4.items()).extracting(DirectoryDepartment::departmentId).containsExactly("od-a2");
        // od-a 枚举完毕，发现的 od-a2 入队 → 仍继续
        assertThat(page4.hasMore()).isTrue();

        DirectoryPage<DirectoryDepartment> page5 = source.departments(new DirectoryPageQuery(page4.nextCursor(), 50));
        // od-a1 子级为空，但队列仍有 od-a2 → 继续
        assertThat(page5.items()).isEmpty();
        assertThat(page5.hasMore()).isTrue();

        DirectoryPage<DirectoryDepartment> page6 = source.departments(new DirectoryPageQuery(page5.nextCursor(), 50));
        assertThat(page6.items()).isEmpty();
        assertThat(page6.hasMore()).isFalse();
        assertThat(page6.nextCursor()).isNull();
    }

    /** M1 范围：tenant_key = expected-tenant-key，app_scope = app-id（与登录链 ExternalIdentity 同维）。 */
    @Test
    void scopeMatchesLoginChainDimensions() {
        assertThat(source().scope()).isEqualTo(new DirectoryScope("tenant-1", "cli_test_app"));
    }

    // ------------------ 人员流与多部门 ------------------

    /** 人员分页：飞书 page_token 原样透传为 nextCursor；department_ids 多部门关系随人员携带。 */
    @Test
    void personsPagesWithRawPageTokenPassthroughAndMultiDeptIds() {
        stub.setPersonsHandler(exchange -> {
            String dept = FeishuStubServer.queryParam(exchange.getRequestURI().getRawQuery(), "department_id");
            if ("0".equals(dept)) {
                if (FeishuStubServer.queryParam(exchange.getRequestURI().getRawQuery(), "page_token") == null) {
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage("pt-1", true,
                            FeishuStubServer.personUser("ou_1", "张三", "San Zhang", "0", "od-a")));
                } else {
                    FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage(null, false,
                            FeishuStubServer.personUser("ou_2", "李四", "Si Li", "od-b")));
                }
            } else {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage(null, false));
            }
        });
        FeishuDirectorySource source = source();

        DirectoryPage<DirectoryPerson> page1 = source.persons("0", firstPage(100));
        assertThat(page1.items()).hasSize(1);
        DirectoryPerson user = page1.items().get(0);
        assertThat(user.openId()).isEqualTo("ou_1");
        assertThat(user.name()).isEqualTo("张三");
        assertThat(user.enName()).isEqualTo("San Zhang");
        assertThat(user.departmentIds()).containsExactly("0", "od-a");
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isEqualTo("pt-1");

        DirectoryPage<DirectoryPerson> page2 = source.persons("0", new DirectoryPageQuery("pt-1", 100));
        assertThat(page2.items()).extracting(DirectoryPerson::openId).containsExactly("ou_2");
        assertThat(page2.hasMore()).isFalse();
        assertThat(page2.nextCursor()).isNull();

        assertThat(stub.lastPersonsQuery())
                .contains("department_id=0")
                .contains("user_id_type=open_id")
                .contains("page_size=50");
        assertThat(stub.lastContactAuthorization()).isEqualTo("Bearer t-tenant-token");
    }

    /** 同一人员随其每个任职部门重复出现（源不去重——去重属 W3-c 存储责任）。 */
    @Test
    void samePersonAppearsUnderEachDepartmentWithoutDedup() {
        stub.setPersonsHandler(exchange -> {
            String dept = FeishuStubServer.queryParam(exchange.getRequestURI().getRawQuery(), "department_id");
            if ("0".equals(dept) || "od-a".equals(dept)) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage(null, false,
                        FeishuStubServer.personUser("ou_1", "张三", "San Zhang", "0", "od-a")));
            } else {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.personsPage(null, false));
            }
        });

        DirectoryPage<DirectoryPerson> fromRoot = source().persons("0", firstPage(50));
        DirectoryPage<DirectoryPerson> fromA = source().persons("od-a", firstPage(50));

        assertThat(fromRoot.items()).extracting(DirectoryPerson::openId).containsExactly("ou_1");
        assertThat(fromA.items()).extracting(DirectoryPerson::openId).containsExactly("ou_1");
        assertThat(fromA.items().get(0).departmentIds()).containsExactly("0", "od-a");
    }

    // ------------------ 权限族：fail-fast 零重试 ------------------

    /** 权限族错误码 → Forbidden（配置类，fail-fast），单次命中零重试——禁止退避风暴、禁止当删除/离职。 */
    @ParameterizedTest
    @ValueSource(ints = {230002, 40004, 40014, 41050, 42008})
    void forbiddenFamilyOnChildrenFailsFastWithoutRetry(int code) {
        stub.setChildrenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 403, FeishuStubServer.contactError(code)));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceForbiddenException.class)
                .hasMessageContaining(String.valueOf(code))
                .extracting(e -> ((DirectorySourceForbiddenException) e).reasonCode())
                .isEqualTo("upstream-forbidden");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
        assertThat(stub.tenantTokenHits.get()).isEqualTo(1);
    }

    /** 人员流权限错误（41050 无用户权限）同样 fail-fast。 */
    @Test
    void forbiddenOnPersonsFailsFastWithoutRetry() {
        stub.setPersonsHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.contactError(41050)));

        assertThatThrownBy(() -> source().persons("0", firstPage(50)))
                .isInstanceOf(DirectorySourceForbiddenException.class)
                .extracting(e -> ((DirectorySourceForbiddenException) e).reasonCode())
                .isEqualTo("upstream-forbidden");
        assertThat(stub.personsHits.get()).isEqualTo(1);
    }

    /** tenant token 获取被拒（凭据/应用形态错误）→ Forbidden 配置类 fail-fast，不触碰目录端点。 */
    @Test
    void tenantTokenRejectionIsForbiddenConfigError() {
        stub.setTenantTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(10003)));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceForbiddenException.class)
                .extracting(e -> ((DirectorySourceForbiddenException) e).reasonCode())
                .isEqualTo("tenant-token");
        assertThat(stub.tenantTokenHits.get()).isEqualTo(1);
        assertThat(stub.childrenHits.get()).isZero();
    }

    // ------------------ 瞬时族：有界重试后 Unavailable ------------------

    /**
     * 限流/上游过载：幂等 GET 有界重试（1 + maxAttempts=2 次共 3 击），耗尽 → Unavailable。
     * 〔M1-TAG-02 relabel〕首行 429+99991400 原期望 {@code rate-limited}——过载优先
     * 重排后 HTTP 429 先于业务码解释，改判 {@code upstream-system}（重试次数与终点不变）。
     */
    @ParameterizedTest
    @CsvSource({
            "429, 99991400, upstream-system",
            "200, 99991400, rate-limited",
            "503, 20051, upstream-system",
            "500, 20051, upstream-system"})
    void retryableFamilyBoundedThenUnavailable(int status, int code, String reasonCode) {
        stub.setChildrenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, status, FeishuStubServer.contactError(code)));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo(reasonCode);
        assertThat(stub.childrenHits.get()).isEqualTo(3);
    }

    /** 读超时（传输异常）：同样有界重试，耗尽 → transport/Unavailable。 */
    @Test
    void readTimeoutRetriesThenUnavailableTransport() {
        directoryProperties.setReadTimeout(Duration.ofMillis(150));
        stub.setChildrenHandler(exchange -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false));
        });
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("transport");
        assertThat(stub.childrenHits.get()).isEqualTo(3);
    }

    /** 未核对的合法非零业务码：确定失败不重试（不当权限、不风暴）。 */
    @Test
    void unknownBusinessCodeIsDeterminateUnavailableNoRetry() {
        stub.setChildrenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.contactError(30001)));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("feishu-error");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    // ------------------ M1-TAG-02：HTTP 过载优先于业务码解释 ------------------

    /**
     * M1-TAG-02 反例（打标签评审 2026-10-01）：429/5xx × 40008 不得按"部门不存在"
     * 解释——传输层失败时信封不可信，先按 upstream-system 有界重试（1 + 2 次共 3 击），
     * 耗尽 → Unavailable（整代废弃信号），不得转成空页/跳过父部门。
     */
    @ParameterizedTest
    @CsvSource({"429", "500", "503"})
    void overloadWithDepartmentGoneCodeIsUpstreamSystemNeverEmptyResult(int status) {
        stub.setChildrenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, status, FeishuStubServer.contactError(40008)));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.childrenHits.get()).isEqualTo(3);
    }

    /** M1-TAG-02 人员流同样覆盖（评审反例即在 persons 端点把 503/429+40008 吞成空终页）。 */
    @ParameterizedTest
    @CsvSource({"429", "500", "503"})
    void overloadWithDepartmentGoneCodeOnPersonsIsUpstreamSystemNeverEmptyPage(int status) {
        stub.setPersonsHandler(exchange ->
                FeishuStubServer.respondJson(exchange, status, FeishuStubServer.contactError(40008)));

        assertThatThrownBy(() -> source().persons("od-gone", firstPage(50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("upstream-system");
        assertThat(stub.personsHits.get()).isEqualTo(3);
    }

    // ------------------ 畸形/矛盾响应：一律不重试 ------------------

    /** 非法信封（非 JSON/码非整型/缺码，随 5xx 网关页到达）→ malformed-response，不重试。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "not-json-at-all",
            "{\"code\":\"9x\",\"msg\":\"x\"}",
            "{\"msg\":\"no-code\"}"})
    void malformedEnvelopesNeverRetry(String body) {
        stub.setChildrenHandler(exchange -> FeishuStubServer.respondJson(exchange, 502, body));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    /** 信封合法但 data 形态矛盾/必需字段缺失（HTTP 200）→ malformed-response，不重试。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":true,\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false,\"items\":{}}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false,\"items\":[{\"name\":\"缺部门id\"}]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":\"not-an-object\"}"})
    void malformedDataShapesNeverRetry(String body) {
        stub.setChildrenHandler(exchange -> FeishuStubServer.respondJson(exchange, 200, body));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    /**
     * M1-TAG-01 反例（打标签评审 2026-10-01）：has_more 非布尔（字符串/数字/null）与
     * items 缺失不得默认"完整空末页"——分页完整性不确定一律 malformed-response 拒绝、
     * 不重试。前四形态携带 page_token + 空 items，证明旧实现会静默丢弃续页游标提前终止
     * 扫描；后两形态证明 data 缺分页字段同样不可当完整空目录。
     * 正对照（合法空终页/合法多页）见 {@link #departmentWithoutUsersIsLegalEmptyLastPage}、
     * {@link #emptyTreeYieldsOnlySyntheticRoot} 与
     * {@link #departmentsMidParentPaginationKeepsParentAndTerminates}。
     * （"has_more=false 携 token"形态在官方契约之外——契约明示 has_more=false 不返回
     * page_token——全测试面未构造该形态，保留宽松忽略属防御性兼容；补修执行记录 §4.1
     * 〔复评补记 P3-N2〕。）
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":\"true\",\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":1,\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":null,\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{}}"})
    void nonBooleanHasMoreOrMissingItemsIsMalformedNeverEmptyLastPage(String body) {
        stub.setChildrenHandler(exchange -> FeishuStubServer.respondJson(exchange, 200, body));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    /** M1-TAG-01 人员流同样覆盖（children/persons 两接口共用分页契约）。 */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":\"true\",\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":1,\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":null,\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"page_token\":\"next-1\",\"items\":[]}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false}}",
            "{\"code\":0,\"msg\":\"ok\",\"data\":{}}"})
    void nonBooleanHasMoreOrMissingItemsOnPersonsIsMalformedNeverEmptyLastPage(String body) {
        stub.setPersonsHandler(exchange -> FeishuStubServer.respondJson(exchange, 200, body));

        assertThatThrownBy(() -> source().persons("od-a", firstPage(50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("malformed-response");
        assertThat(stub.personsHits.get()).isEqualTo(1);
    }

    /** 业务码 0 但 HTTP 5xx：矛盾结果不重试（http-business-conflict）。 */
    @Test
    void successShapeOnHttp5xxIsConflictNotAccepted() {
        stub.setChildrenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 500,
                        FeishuStubServer.departmentsPage(null, false,
                                FeishuStubServer.childDepartment("od-a", "0", "部门A"))));
        FeishuDirectorySource source = source();
        String cursor = childrenCursor(source);

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(cursor, 50)))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .extracting(e -> ((DirectorySourceUnavailableException) e).reasonCode())
                .isEqualTo("http-business-conflict");
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    // ------------------ 空页/空目录/部门消失：合法，不解释为删除 ------------------

    /** 空目录：仅合成根，无异常。 */
    @Test
    void emptyTreeYieldsOnlySyntheticRoot() {
        List<DirectoryDepartment> all = walkDepartments(source(), 50);

        assertThat(all).containsExactly(new DirectoryDepartment("0", null, null));
        assertThat(stub.childrenHits.get()).isEqualTo(1);
    }

    /** 部门无人员：合法空终页。 */
    @Test
    void departmentWithoutUsersIsLegalEmptyLastPage() {
        DirectoryPage<DirectoryPerson> page = source().persons("od-b", firstPage(50));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(stub.personsHits.get()).isEqualTo(1);
    }

    /** persons 遇 40008（上游并发删除）→ 空终页，不抛异常、不解释为删除/离职、不重试。 */
    @Test
    void departmentGoneOnPersonsReturnsEmptyLastPage() {
        stub.setPersonsHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.contactError(40008)));

        DirectoryPage<DirectoryPerson> page = source().persons("od-gone", firstPage(50));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(stub.personsHits.get()).isEqualTo(1);
    }

    /** children 遇 40008：跳过该父部门继续 BFS（同层其余部门不受影响）。 */
    @Test
    void departmentGoneOnChildrenSkipsParentAndContinuesBfs() {
        stub.setChildrenHandler(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/departments/0/children")) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                        FeishuStubServer.childDepartment("od-a", "0", "部门A"),
                        FeishuStubServer.childDepartment("od-b", "0", "部门B")));
            } else if (path.endsWith("/departments/od-b/children")) {
                FeishuStubServer.respondJson(exchange, 400, FeishuStubServer.contactError(40008));
            } else if (path.endsWith("/departments/od-a/children")) {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false,
                        FeishuStubServer.childDepartment("od-a1", "od-a", "子部门A1")));
            } else {
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.departmentsPage(null, false));
            }
        });

        List<DirectoryDepartment> all = walkDepartments(source(), 50);

        // od-b 子级枚举被跳过，遍历整体完成、od-a1 照常到达
        assertThat(all).extracting(DirectoryDepartment::departmentId)
                .containsExactly("0", "od-a", "od-b", "od-a1");
        assertThat(stub.childrenHits.get()).isEqualTo(4);
    }

    // ------------------ 游标与构造边界 ------------------

    /** 非本源产出的游标（无法解码/形态错误）→ ContractViolation（整代废弃信号）。 */
    @Test
    void garbageCursorIsContractViolation() {
        FeishuDirectorySource source = source();

        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery("%%%not-a-cursor", 50)))
                .isInstanceOf(DirectoryContractViolationException.class)
                .extracting(e -> ((DirectoryContractViolationException) e).reasonCode())
                .isEqualTo("invalid-cursor");

        // 合法 Base64 但形态错误（p 非文本）同样拒绝
        String wrongShape = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"p\":1,\"q\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> source.departments(new DirectoryPageQuery(wrongShape, 50)))
                .isInstanceOf(DirectoryContractViolationException.class)
                .extracting(e -> ((DirectoryContractViolationException) e).reasonCode())
                .isEqualTo("invalid-cursor");
    }

    /** 目录必填键缺失 → 构造期 fail-closed，逐项列出并给出退让指引。 */
    @Test
    void missingCredentialsFailClosedWithActionableMessage() {
        FeishuLoginProperties empty = new FeishuLoginProperties();

        assertThatThrownBy(() -> new FeishuDirectorySource(empty, directoryProperties, 2,
                Duration.ofMillis(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fastddd.identity.feishu.app-id")
                .hasMessageContaining("fastddd.identity.feishu.app-secret")
                .hasMessageContaining("fastddd.identity.feishu.expected-tenant-key")
                .hasMessageContaining("宿主使用其他目录源");
    }

    // ------------------ tenant token 缓存 ------------------

    /** expire 解读为"有效期秒"且余量充足 → 全程单次获取。 */
    @Test
    void tokenCachedAcrossCallsWhenExpireComfortable() {
        stubStandardTree();
        FeishuDirectorySource source = source();
        walkDepartments(source, 50);
        source.persons("0", firstPage(50));

        assertThat(stub.tenantTokenHits.get()).isEqualTo(1);
    }

    /** 剩余有效期进入 30 分钟换新余量 → 下次调用重新获取。 */
    @Test
    void tokenRefreshedWhenWithinMargin() {
        stub.setTenantTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200, FeishuStubServer.tenantTokenBody("t-short", 1)));
        FeishuDirectorySource source = source();

        source.persons("0", firstPage(50));
        source.persons("od-a", firstPage(50));

        assertThat(stub.tenantTokenHits.get()).isEqualTo(2);
    }

    /** 防御性双解读：expire 呈绝对时间戳形态（> 10^9 epoch 秒，已过期）→ 每次换新。 */
    @Test
    void tokenEpochLikeExpireInterpretedAsEpochSeconds() {
        stub.setTenantTokenHandler(exchange ->
                FeishuStubServer.respondJson(exchange, 200,
                        FeishuStubServer.tenantTokenBody("t-old", 1500000000)));
        FeishuDirectorySource source = source();

        source.persons("0", firstPage(50));
        source.persons("od-a", firstPage(50));

        assertThat(stub.tenantTokenHits.get()).isEqualTo(2);
    }
}
