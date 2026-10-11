package com.gallop.fastddd.identity.core.tck;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * IdP 适配器 {@link DirectorySource} 端口契约套件（TCK 第一期，W7-d / D-7 = (A) test-jar classifier）。
 *
 * <p>宿主（IdP 适配器自实现方）实现 {@link DirectorySourceHarness} + 继承本基类即可运行全套断言；
 * 本仓参考实现（feishu）经 dogfood 子类自跑验证（判据之一）。
 *
 * <p><b>断言域</b>（端口级语义，宿主线协议无关）：
 * <ul>
 *   <li>P 组——分页走查终止、内容超集、游标状态机推进、page_size 不放大；</li>
 *   <li>S 组——严格成功三要素（W22-C1）与分页严格校验反面形态：malformed-response，
 *       Unavailable 不重试（击数 == 队列位）；</li>
 *   <li>F 组——权限族 fail-fast 零重试（击数 == 队列位）；凭据建立失败 Forbidden 且数据端点零外呼；</li>
 *   <li>R 组——瞬态族有界重试：恢复（≥ 首击+重试 2 击，容树形宿主结构性子级展开）、
 *       耗尽（Unavailable，击数 ∈ [2, 1+预算]）、确定失败不重试（1 击）；</li>
 *   <li>G 组——规则 B：部门并发删除（DepartmentGone）不判删除，合法空终页；空页合法；</li>
 *   <li>C 组——凭据缺失 fail-closed：构造期抛 RuntimeException 且零上游外呼；</li>
 *   <li>X 组——同步范围组件级断言。</li>
 * </ul>
 *
 * <p><b>击数不变量</b>：上游脚本队列第 i 位（1-based）为终态失败（malformed / denied /
 * determinate）时，该流外呼总数 == i；瞬态失败重试消费下一位（FIFO）。不断言 reasonCode
 * 字符串与具体线协议字段。
 */
public abstract class DirectorySourceContractTck {

    /** 契约走查使用的页大小上限（源实现不得放大上送）。 */
    protected static final int PAGE_SIZE = 20;

    /** 走查护栏：防上游桩/实现死循环（合成根 hasMore 恒 true 等病态形态在此暴露）。 */
    protected static final int MAX_PAGES = 32;

    private static final String TCK_BASE = "com.gallop.fastddd.identity.core.tck.DirectorySourceContractTck";

    private DirectorySourceHarness harnessInstance;

    /** 宿主 SPI 装配点：每测试惰性创建一个 harness 实例。 */
    protected abstract DirectorySourceHarness newHarness();

    protected final DirectorySourceHarness harness() {
        if (harnessInstance == null) {
            harnessInstance = newHarness();
        }
        return harnessInstance;
    }

    @AfterEach
    void closeTckHarness() throws Exception {
        if (harnessInstance instanceof AutoCloseable closeable) {
            closeable.close();
        }
        harnessInstance = null;
    }

    // ---- P 组：分页走查 / 游标 / pageSize ----

    @Test
    void p1_multiPageWalkTerminatesAndCarriesContent() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                deptPage(true, "token-1", dept("d1", "0")),
                deptPage(false, null, dept("d2", "d1")));
        script.playPersons("d1", personPage(false, null, person("p1", "d1")));
        script.playPersons("d2", personPage(false, null, person("p2", "d2")));
        DirectorySource source = harness().newSource(script);

        List<String> deptIds = walkDepartmentIds(source);
        // 内容超集：脚本播出的部门全部可见（实现可加合成根，容差 +1）
        assertThat(deptIds).contains("d1", "d2");
        // 无重复（分页状态机不得重访）
        assertThat(deptIds.size()).isEqualTo(new HashSet<>(deptIds).size());
        assertThat(deptIds.size()).isLessThanOrEqualTo(3);
        // persons 走查：未编程部门（含可能存在的合成根）走队列耗尽空终页，安全
        List<String> personIds = walkPersonIds(source, deptIds);
        assertThat(personIds).contains("p1", "p2");
    }

    @Test
    void p2_cursorStateMachineAdvancesAcrossPages() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                deptPage(true, "token-1", dept("d1", "0")),
                deptPage(false, null, dept("d2", "d1")));
        DirectorySource source = harness().newSource(script);

        walkDepartmentIds(source);

        List<String> cursors = script.observedDepartmentCursors();
        // 观测记录数与外呼计数自洽（每次外呼都记录了游标形态）
        assertThat(cursors.size()).isEqualTo(script.departmentCallCount());
        // 多页场景下分页状态机必有推进信息：至少一次外呼携带非空游标
        assertThat(cursors.stream().anyMatch(c -> c != null && !c.isBlank())).isTrue();
    }

    @Test
    void p3_emptyDirectoryIsLegal() {
        DirectoryUpstreamScript script = harness().newScript();
        // 零编程：departments/persons 全走队列耗尽空终页
        DirectorySource source = harness().newSource(script);

        List<String> deptIds = walkDepartmentIds(source);
        // 至多一个合成根，无其他部门
        assertThat(deptIds.size()).isLessThanOrEqualTo(1);
        List<String> personIds = walkPersonIds(source, deptIds);
        assertThat(personIds).isEmpty();
    }

    @Test
    void p4_pageSizeIsNeverAmplified() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                deptPage(true, "token-1", dept("d1", "0")),
                deptPage(false, null, dept("d2", "d1")));
        script.playPersons("d1", personPage(true, "p-token-1", person("p1", "d1")),
                personPage(false, null));
        DirectorySource source = harness().newSource(script);

        List<String> deptIds = walkDepartmentIds(source);
        walkPersonIds(source, deptIds);

        assertThat(script.observedDepartmentPageSizes())
                .allSatisfy(size -> assertThat(size).isLessThanOrEqualTo(PAGE_SIZE));
        script.observedPersonPageSizes("d1")
                .forEach(size -> assertThat(size).isLessThanOrEqualTo(PAGE_SIZE));
    }

    // ---- S 组：严格成功三要素 / 分页严格校验（malformed-response，不重试） ----

    @ParameterizedTest
    @MethodSource(TCK_BASE + "#malformedShapes")
    void s1_malformedSuccessEnvelopeIsUnavailableWithoutRetry(UpstreamResponse.MalformedShape shape) {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                deptPage(true, "token-1", dept("d1", "0")),
                new UpstreamResponse.MalformedSuccess(shape));
        DirectorySource source = harness().newSource(script);

        // 队列第 2 位终态失败 ⇒ 恰 2 次部门外呼后 Unavailable（无重试放大）
        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceUnavailableException.class);
        assertThat(script.departmentCallCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource(TCK_BASE + "#malformedShapes")
    void s2_malformedPersonsEnvelopeIsUnavailableWithoutRetry(UpstreamResponse.MalformedShape shape) {
        DirectoryUpstreamScript script = harness().newScript();
        script.playPersons("d-1", new UpstreamResponse.MalformedSuccess(shape));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> source.persons("d-1", firstQuery()))
                .isInstanceOf(DirectorySourceUnavailableException.class);
        assertThat(script.personCallCount("d-1")).isEqualTo(1);
    }

    // ---- F 组：权限族 fail-fast / 凭据建立 fail-closed ----

    @Test
    void f1_permissionDeniedAfterSuccessFailsFast() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                deptPage(true, "token-1", dept("d1", "0")),
                new UpstreamResponse.PermissionDenied("no-contact-scope"));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceForbiddenException.class);
        // 第 2 位终态失败：恰 2 击，权限族零退避重试
        assertThat(script.departmentCallCount()).isEqualTo(2);
    }

    @Test
    void f2_permissionDeniedOnFirstCallFailsFast() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(new UpstreamResponse.PermissionDenied("no-contact-scope"));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceForbiddenException.class);
        assertThat(script.departmentCallCount()).isEqualTo(1);
    }

    @Test
    void f3_authEstablishmentDeniedFailsFastWithZeroDataCalls() {
        DirectoryUpstreamScript script = harness().newScript();
        script.failAuthEstablishmentWith(new UpstreamResponse.AuthEstablishmentDenied("token-rejected"));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceForbiddenException.class);
        // 凭据失败 ⇒ 数据端点零外呼：总击数 == 凭据建立击数
        assertThat(script.totalUpstreamCallCount())
                .isEqualTo(script.authEstablishmentCallCount());
        // fail-fast（或有界，容 1..1+预算——权限族语义上应恰 1）
        assertThat(script.authEstablishmentCallCount())
                .isBetween(1, 1 + harness().transientRetryBudget());
    }

    // ---- R 组：瞬态族有界重试 ----

    @ParameterizedTest
    @MethodSource(TCK_BASE + "#transientKinds")
    void r1_transientFailureRecoversOnNextQueueSlot(TransientKind kind) {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(
                transientOf(kind, "transient-" + kind),
                deptPage(false, null, dept("d1", "0")));
        DirectorySource source = harness().newSource(script);

        List<String> deptIds = walkDepartmentIds(source);
        assertThat(deptIds).contains("d1");
        // [瞬态, OK]：首击失败后内容仍送达 ⇒ 至少一次重试（≥2）。上界 = 2（首击+重试）
        // + 已见部门数（树形宿主 BFS 对每个已见部门至多一次子级展开外呼——dogfood 实证：
        // feishu 恢复页 has_more=false 仅终止当前父级分页，BFS 仍展开子级，结构性第 3 击合法）
        assertThat(script.departmentCallCount()).isBetween(2, 2 + deptIds.size());
    }

    @ParameterizedTest
    @MethodSource(TCK_BASE + "#transientKinds")
    void r2_stickyTransientFailureIsBoundedRetriedThenUnavailable(TransientKind kind) {
        DirectoryUpstreamScript script = harness().newScript();
        script.failDepartmentsWith(transientOf(kind, "sticky-" + kind));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceUnavailableException.class);
        // ≥2（至少一次重试）、≤ 1+预算（有界，不无限退避）
        assertThat(script.departmentCallCount())
                .isBetween(2, 1 + harness().transientRetryBudget());
    }

    @Test
    void r3_determinateFailureIsUnavailableWithoutRetry() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playDepartments(new UpstreamResponse.DeterminateFailure("unmapped-biz-code"));
        DirectorySource source = harness().newSource(script);

        assertThatThrownBy(() -> walkDepartmentIds(source))
                .isInstanceOf(DirectorySourceUnavailableException.class);
        // 未核对业务码兜底域：确定失败不重试，恰 1 击
        assertThat(script.departmentCallCount()).isEqualTo(1);
    }

    // ---- G 组：规则 B（不判删除）/ 空页合法 ----

    @Test
    void g1_departmentGoneOnPersonsStreamIsLegalEmptyPage() {
        DirectoryUpstreamScript script = harness().newScript();
        script.playPersons("d-gone", new UpstreamResponse.DepartmentGone("dept-deleted-upstream"));
        DirectorySource source = harness().newSource(script);

        // 上游并发删除：不抛、不判删除，返回空页（规则 B）
        DirectoryPage<DirectoryPerson> page = source.persons("d-gone", firstQuery());
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(script.personCallCount("d-gone")).isEqualTo(1);
    }

    @Test
    void g2_emptyPersonsPageIsLegal() {
        DirectoryUpstreamScript script = harness().newScript();
        // 零编程：队列耗尽 → 合法空终页
        DirectorySource source = harness().newSource(script);

        DirectoryPage<DirectoryPerson> page = source.persons("d-empty", firstQuery());
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    // ---- C 组：凭据 fail-closed ----

    @Test
    void c1_missingCredentialsFailsClosedAtConstruction() {
        DirectoryUpstreamScript script = harness().newScript();

        Throwable thrown = catchThrowable(() -> harness().newSourceWithMissingCredentials(script));

        assertThat(thrown).isNotNull();
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).isNotBlank();
        // fail-closed：构造期拒绝，零上游外呼
        assertThat(script.totalUpstreamCallCount()).isZero();
    }

    // ---- X 组：同步范围 ----

    @Test
    void x1_scopeMatchesExpectedComponentValues() {
        DirectoryUpstreamScript script = harness().newScript();
        DirectorySource source = harness().newSource(script);

        DirectoryScope expected = harness().expectedScope();
        DirectoryScope actual = source.scope();
        assertThat(actual.tenantKey()).isEqualTo(expected.tenantKey());
        assertThat(actual.appScope()).isEqualTo(expected.appScope());
    }

    // ---- 参数化源 ----

    /** 畸形形态全集（8 值）。 */
    static Stream<Arguments> malformedShapes() {
        return Stream.of(UpstreamResponse.MalformedShape.values())
                .map(Arguments::of);
    }

    /** 瞬态族全集（3 值）。 */
    static Stream<Arguments> transientKinds() {
        return Stream.of(TransientKind.values())
                .map(Arguments::of);
    }

    /** 瞬态族种类（S/F/R 分域之外的瞬态三形态）。 */
    enum TransientKind {
        RATE_LIMITED, UPSTREAM_OVERLOADED, READ_TIMEOUT
    }

    // ---- 走查 helper ----

    /** 循环拉取 departments 至 hasMore=false（收集部门 id）；超 MAX_PAGES 护栏抛 AssertionError。 */
    protected final List<String> walkDepartmentIds(DirectorySource source) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            DirectoryPage<DirectoryDepartment> page = source.departments(new DirectoryPageQuery(cursor, PAGE_SIZE));
            page.items().forEach(d -> ids.add(d.departmentId()));
            if (!page.hasMore()) {
                return ids;
            }
            cursor = page.nextCursor();
        }
        throw new AssertionError("departments walk exceeded MAX_PAGES=" + MAX_PAGES
                + " — 分页状态机未终止（nextCursor 恒非终页？）");
    }

    /** 对每个部门循环拉取 persons 至 hasMore=false（收集 openId）；护栏同上。 */
    protected final List<String> walkPersonIds(DirectorySource source, List<String> departmentIds) {
        List<String> ids = new ArrayList<>();
        for (String deptId : departmentIds) {
            String cursor = null;
            boolean last = false;
            for (int i = 0; i < MAX_PAGES && !last; i++) {
                DirectoryPage<DirectoryPerson> page = source.persons(deptId, new DirectoryPageQuery(cursor, PAGE_SIZE));
                page.items().forEach(p -> ids.add(p.openId()));
                last = !page.hasMore();
                if (!last) {
                    cursor = Objects.requireNonNull(page.nextCursor(),
                            "hasMore=true 必须携带非空 nextCursor");
                }
            }
            if (!last) {
                throw new AssertionError("persons walk exceeded MAX_PAGES=" + MAX_PAGES
                        + " for department " + deptId);
            }
        }
        return ids;
    }

    // ---- 工厂 helper ----

    /** 首页查询（cursor null = 首页）。 */
    protected final DirectoryPageQuery firstQuery() {
        return new DirectoryPageQuery(null, PAGE_SIZE);
    }

    private static UpstreamResponse.UpstreamDepartment dept(String id, String parentId) {
        return new UpstreamResponse.UpstreamDepartment(id, parentId, "dept-" + id);
    }

    private static UpstreamResponse.DepartmentsPage deptPage(boolean hasMore, String nextToken,
                                                             UpstreamResponse.UpstreamDepartment... depts) {
        return new UpstreamResponse.DepartmentsPage(List.of(depts), nextToken, hasMore);
    }

    private static UpstreamResponse.UpstreamPerson person(String openId, String... deptIds) {
        return new UpstreamResponse.UpstreamPerson(openId, "person-" + openId, "en-" + openId, List.of(deptIds));
    }

    private static UpstreamResponse.PersonsPage personPage(boolean hasMore, String nextToken,
                                                           UpstreamResponse.UpstreamPerson... persons) {
        return new UpstreamResponse.PersonsPage(List.of(persons), nextToken, hasMore);
    }

    private static UpstreamResponse transientOf(TransientKind kind, String detail) {
        return switch (kind) {
            case RATE_LIMITED -> new UpstreamResponse.RateLimited(detail);
            case UPSTREAM_OVERLOADED -> new UpstreamResponse.UpstreamOverloaded(detail);
            case READ_TIMEOUT -> new UpstreamResponse.ReadTimeout(detail);
        };
    }
}
