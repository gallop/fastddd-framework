package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.error.DirectoryInvalidGenerationStateException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3-c 目录镜像存储 PG 验收测试（W3 设计 v3 §8 矩阵 PG 层：1/2/4/7–18/26/28/29；
 * 矩阵 3/4 的编排放大见 {@link DirectorySyncOrchestrationPgTest}）。
 *
 * <p><b>测试面</b>：真实 zonky PG + 真实事务 runner（READ_COMMITTED 短事务，
 * {@link Support#runner}）；可拨时钟驱动租约过期；两独立 DataSource 模拟两实例
 * （矩阵 7/8/10/11/13——门禁以 DataSource 实例绑定连接，两实例即两数据源对象）。
 * 矩阵 7 的"同时 begin"以顺序模拟等价承载：metadata 行 FOR UPDATE 串行化两个
 * begin 事务，判据（恰一持租约、另一方 LeaseHeld、零交叉写入）在串行交错下
 * 与真实并发窗口一致（并发只改变谁先拿到锁，不改变不变量）。</p>
 */
class PgDirectoryMirrorStoreTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-mirror", "app");
    private static final DirectoryScope VALIDATING_SCOPE = new DirectoryScope("tenant-mirror", "validating");
    private static final DirectoryScope VALIDATED_SCOPE = new DirectoryScope("tenant-mirror", "validated");
    private static final Duration TTL = Duration.ofMinutes(10);

    private DataSource dataSource;
    private MutableClock clock;
    private PgDirectoryMirrorStore store;
    private IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    /** 可拨时钟：租约过期/接管时序全部由它驱动（墙钟绝不进顺序语义，§5.1）。 */
    static final class MutableClock implements PgLocalSessionStore.Clock {
        private Instant now = Instant.parse("2026-09-28T00:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        clock = new MutableClock();
        store = new PgDirectoryMirrorStore(new JdbcTemplate(dataSource), clock, TTL);
        runner = Support.runner(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    // ---------------------------------------------------------- 矩阵 1 + 26

    @Test
    void matrix1And26_firstPublishInsertsRuleAFactsAndAdvancesWatermarkWithoutOutbox() {
        GenerationHandle handle = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                        new DirectoryPerson("OU2", "李四", "Li Si", List.of("D2"))));
        DirectoryPublication publication = publish(handle);

        // 矩阵 1：新代 PUBLISHED、指针切换、规则 A（rev=1、updated_seq=1）、
        // committed_seq 0→1、applied_seq=1（=H_after）；矩阵 26：零 Outbox 即全链完成
        assertThat(publication.generationId()).isEqualTo(handle.generationId());
        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);
        assertThat(publication.factsInserted()).isEqualTo(4);

        assertThat(genState(handle.generationId())).isEqualTo("PUBLISHED");
        assertThat(pubResultColumns(handle.generationId()))
                .containsExactly(0L, 1L, 1L, 4L);
        assertThat(activeGeneration(SCOPE)).isEqualTo(handle.generationId());
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        assertThat(leaseToken(SCOPE)).isNull();

        // 规则 A 行形态：四键全量 local_revision=1 / tombstone=false / FULL_SCAN /
        // pending=false / source_generation=本代 / updated_seq=1
        List<Map<String, Object>> facts = jdbc.queryForList(
                "SELECT object_type, external_id, local_revision, tombstone, origin, pending_confirm, "
                        + "source_generation, updated_seq FROM directory_fact "
                        + "WHERE tenant_id=? AND app_scope=? ORDER BY object_type, external_id",
                SCOPE.tenantKey(), SCOPE.appScope());
        assertThat(facts).hasSize(4);
        for (Map<String, Object> row : facts) {
            assertThat(row.get("local_revision")).isEqualTo(1L);
            assertThat(row.get("tombstone")).isEqualTo(false);
            assertThat(row.get("origin")).isEqualTo("FULL_SCAN");
            assertThat(row.get("pending_confirm")).isEqualTo(false);
            assertThat(row.get("source_generation")).isEqualTo(handle.generationId());
            assertThat(row.get("updated_seq")).isEqualTo(1L);
        }

        // 读契约：观察快照 = 活动代内容 + 水位
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.activeGenerationId()).contains(handle.generationId());
        assertThat(observation.committedSeq()).isEqualTo(1);
        assertThat(observation.appliedSeq()).isEqualTo(1);
        assertThat(observation.observedScope().rootDepartmentIds()).containsExactly("D1");
        assertThat(observation.observedScope().departmentIds()).containsExactly("D1", "D2");
        assertThat(observation.departmentCount()).isEqualTo(2);
        assertThat(observation.personCount()).isEqualTo(2);
        assertThat(observation.scopeSuspicions()).isEmpty();
    }

    // ---------------------------------------------------------- 矩阵 2

    @Test
    void matrix2_repeatSyncWithUnchangedContentWritesZeroFacts() {
        List<DirectoryDepartment> departments =
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发"));
        List<DirectoryPerson> persons = List.of(
                new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                new DirectoryPerson("OU2", "李四", null, List.of("D2")));
        publish(buildGeneration(SCOPE, departments, persons));

        GenerationHandle second = buildGeneration(SCOPE, departments, persons);
        DirectoryPublication publication = publish(second);

        // 空差异：零事实写入、committed/applied 保持 1（不推事实序号）、仅 generation 前进
        assertThat(publication.committedSeqBefore()).isEqualTo(1);
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);
        assertThat(publication.factsInserted()).isZero();
        assertThat(genState(second.generationId())).isEqualTo("PUBLISHED");
        assertThat(activeGeneration(SCOPE)).isEqualTo(second.generationId());
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        assertThat(factCount(SCOPE)).isEqualTo(4);
        assertThat(fact(SCOPE, "PERSON", "OU1").get("updated_seq")).isEqualTo(1L);
    }

    // ---------------------------------------------------------- 矩阵 7

    @Test
    void matrix7_twoInstancesBeginExactlyOneWinsLease() {
        DataSource dsA = Support.dataSource();
        DataSource dsB = Support.dataSource();
        PgDirectoryMirrorStore instanceA =
                new PgDirectoryMirrorStore(new JdbcTemplate(dsA), clock, TTL);
        PgDirectoryMirrorStore instanceB =
                new PgDirectoryMirrorStore(new JdbcTemplate(dsB), clock, TTL);
        IdentityTransactionRunner runnerA = Support.runner(dsA);
        IdentityTransactionRunner runnerB = Support.runner(dsB);

        GenerationHandle handleA =
                runnerA.inTransaction(() -> instanceA.beginGeneration(SCOPE));

        // 另一实例 begin → LeaseHeld（FOR UPDATE 串行下顺序模拟 = 并发不变量，类 javadoc）
        assertThatThrownBy(() -> runnerB.inTransaction(() -> instanceB.beginGeneration(SCOPE)))
                .isInstanceOf(DirectoryLeaseHeldException.class);

        // 恰一代、恰一持有者、零交叉写入
        assertThat(Support.count(dataSource, "directory_generation")).isEqualTo(1);
        assertThat(genState(handleA.generationId())).isEqualTo("BUILDING");
        assertThat(leaseToken(SCOPE)).isEqualTo(handleA.leaseToken());
        assertThat(leaseHolder(SCOPE)).isEqualTo("directory-sync");
        assertThat(Support.count(dataSource, "directory_person_mirror")).isZero();
        assertThat(Support.count(dataSource, "directory_department_mirror")).isZero();
        assertThat(factCount(SCOPE)).isZero();
        assertThat(committedSeq(SCOPE)).isZero();
    }

    // 矩阵 7 纵深防御层：唯一索引的目录存在性断言（运行期互斥由 metadata 行
    // FOR UPDATE 串行化承载，复评 S1 真两连接并发已验证；本索引防的是未来绕过
    // 锁序的代码路径——不能只存在于 DDL 源文本，消解复评 W3C-R4-4/观察 6）
    @Test
    void matrix7_openGenerationUniqueIndexPresentInCatalog() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_directory_generation_open'");
        assertThat(rows).hasSize(1);
        String indexdef = (String) rows.get(0).get("indexdef");
        // PG 目录将谓词规范化为 = ANY(ARRAY[...])，只断言稳定不变的形态要素
        assertThat(indexdef)
                .contains("CREATE UNIQUE INDEX")
                .contains("ON").contains("directory_generation")
                .contains("tenant_id").contains("app_scope")
                .contains("WHERE");
    }

    // ---------------------------------------------------------- 矩阵 8

    @Test
    void matrix8_buildingTakeoverAbandonsOldGenerationAndRejectsLateWrite() {
        DataSource dsB = Support.dataSource();
        PgDirectoryMirrorStore instanceB = new PgDirectoryMirrorStore(new JdbcTemplate(dsB), clock, TTL);
        IdentityTransactionRunner runnerB = Support.runner(dsB);

        GenerationHandle handleA = begin(SCOPE);
        write(handleA, DirectoryBatch.ofDepartments(List.of(
                new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发"))));
        clock.advance(TTL.plus(Duration.ofMinutes(1)));

        GenerationHandle handleB =
                runnerB.inTransaction(() -> instanceB.beginGeneration(SCOPE));

        // 旧代 ABANDONED（稳定原因码）、B 开新代 + 新 token
        assertThat(genState(handleA.generationId())).isEqualTo("ABANDONED");
        assertThat(abandonReason(handleA.generationId())).isEqualTo("lease-takeover");
        assertThat(handleB.generationId()).isNotEqualTo(handleA.generationId());
        assertThat(handleB.leaseToken()).isNotEqualTo(handleA.leaseToken());
        assertThat(genState(handleB.generationId())).isEqualTo("BUILDING");
        assertThat(leaseToken(SCOPE)).isEqualTo(handleB.leaseToken());

        // A 迟到写批被拒（token 联合校验）且零落库
        assertThatThrownBy(() -> write(handleA, DirectoryBatch.ofPersons(
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))))))
                .isInstanceOf(DirectoryLeaseLostException.class);
        assertThat(mirrorRowCount(handleA.generationId())).isEqualTo(2);
        assertThat(mirrorRowCount(handleB.generationId())).isZero();
    }

    // ---------------------------------------------------------- 矩阵 9

    @Test
    void matrix9_expiredLeaseWithoutTakeoverStillRejectsPublish() {
        GenerationHandle handle = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        clock.advance(TTL.plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> publish(handle))
                .isInstanceOf(DirectoryLeaseExpiredException.class);

        // 代状态/指针/committed_seq 全不变（过期无人接管 ≠ 允许发布）
        assertThat(genState(handle.generationId())).isEqualTo("VALIDATED");
        assertThat(activeGeneration(SCOPE)).isNull();
        assertThat(committedSeq(SCOPE)).isZero();
        assertThat(appliedSeq(SCOPE)).isZero();
        assertThat(factCount(SCOPE)).isZero();
    }

    // ---------------------------------------------------------- 矩阵 10

    @Test
    void matrix10_takeoverCoversValidatingAndValidatedStates() {
        // VALIDATING 期崩溃 → 接管
        GenerationHandle validating = begin(VALIDATING_SCOPE);
        write(validating, DirectoryBatch.ofDepartments(List.of(new DirectoryDepartment("D1", null, "总部"))));
        runInTx(() -> store.completePaging(validating));
        clock.advance(TTL.plus(Duration.ofMinutes(1)));
        DataSource dsB = Support.dataSource();
        GenerationHandle takeoverB = Support.runner(dsB).inTransaction(
                () -> new PgDirectoryMirrorStore(new JdbcTemplate(dsB), clock, TTL).beginGeneration(VALIDATING_SCOPE));
        assertThat(genState(validating.generationId())).isEqualTo("ABANDONED");
        assertThat(abandonReason(validating.generationId())).isEqualTo("lease-takeover");
        assertThat(genState(takeoverB.generationId())).isEqualTo("BUILDING");

        // VALIDATED 期崩溃 → 接管（同事务：旧代 ABANDONED + 新代成功）
        clock.advance(Duration.ofMinutes(11).negated()); // 拨回，两个子场景各自从有效租约出发
        GenerationHandle validated = buildGeneration(VALIDATED_SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        clock.advance(TTL.plus(Duration.ofMinutes(1)));
        DataSource dsC = Support.dataSource();
        GenerationHandle takeoverC = Support.runner(dsC).inTransaction(
                () -> new PgDirectoryMirrorStore(new JdbcTemplate(dsC), clock, TTL).beginGeneration(VALIDATED_SCOPE));
        assertThat(genState(validated.generationId())).isEqualTo("ABANDONED");
        assertThat(genState(takeoverC.generationId())).isEqualTo("BUILDING");
        assertThat(leaseToken(VALIDATED_SCOPE)).isEqualTo(takeoverC.leaseToken());
    }

    // ---------------------------------------------------------- 矩阵 11

    @Test
    void matrix11_lateHolderWriteAbandonPublishAllRejectedAfterTakeover() {
        GenerationHandle handleA = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        clock.advance(TTL.plus(Duration.ofMinutes(1)));
        DataSource dsB = Support.dataSource();
        GenerationHandle handleB = Support.runner(dsB).inTransaction(
                () -> new PgDirectoryMirrorStore(new JdbcTemplate(dsB), clock, TTL).beginGeneration(SCOPE));

        assertThatThrownBy(() -> write(handleA, DirectoryBatch.ofPersons(
                List.of(new DirectoryPerson("OU9", "迟到", null, List.of("D1"))))))
                .isInstanceOf(DirectoryLeaseLostException.class);
        assertThatThrownBy(() -> runInTx(() -> store.abandon(handleA, "late-cleanup")))
                .isInstanceOf(DirectoryLeaseLostException.class);
        assertThatThrownBy(() -> publish(handleA))
                .isInstanceOf(DirectoryLeaseLostException.class);

        // B 新代零影响
        assertThat(genState(handleB.generationId())).isEqualTo("BUILDING");
        assertThat(mirrorRowCount(handleB.generationId())).isZero();
        assertThat(activeGeneration(SCOPE)).isNull();
        assertThat(factCount(SCOPE)).isZero();
    }

    // ---------------------------------------------------------- 矩阵 12

    @Test
    void matrix12_writeBatchAfterValidatingOrValidatedRejectedWithZeroRows() {
        GenerationHandle handle = begin(SCOPE);
        write(handle, DirectoryBatch.ofDepartments(List.of(
                new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发"))));
        runInTx(() -> store.completePaging(handle));

        // VALIDATING：writeBatch 状态前置拒（需 BUILDING），零行落库、pages_done 不动
        assertThatThrownBy(() -> write(handle, DirectoryBatch.ofPersons(
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))))))
                .isInstanceOf(DirectoryInvalidGenerationStateException.class);
        assertThat(mirrorRowCount(handle.generationId())).isEqualTo(2);
        assertThat(pagesDone(handle.generationId())).isEqualTo(1);

        runInTx(() -> store.validate(handle));

        // VALIDATED：追加批次同样前置拒（§4：VALIDATED 之后一律拒绝）
        assertThatThrownBy(() -> write(handle, DirectoryBatch.ofPersons(
                List.of(new DirectoryPerson("OU2", "李四", null, List.of("D2"))))))
                .isInstanceOf(DirectoryInvalidGenerationStateException.class);
        assertThat(mirrorRowCount(handle.generationId())).isEqualTo(2);
        assertThat(pagesDone(handle.generationId())).isEqualTo(1);
    }

    // ---------------------------------------------------------- 矩阵 13

    @Test
    void matrix13_expiredRenewRejectedAndReacquireTakesOverWithNewToken() {
        GenerationHandle handleA = begin(SCOPE);
        clock.advance(TTL.plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> runInTx(() -> store.renewLease(handleA)))
                .isInstanceOf(DirectoryLeaseExpiredException.class);

        DataSource dsB = Support.dataSource();
        GenerationHandle handleB = Support.runner(dsB).inTransaction(
                () -> new PgDirectoryMirrorStore(new JdbcTemplate(dsB), clock, TTL).beginGeneration(SCOPE));

        // 过期不静默延期：重新 acquire 走接管、新 token ≠ 旧 token、旧代 ABANDONED
        assertThat(handleB.leaseToken()).isNotEqualTo(handleA.leaseToken());
        assertThat(genState(handleA.generationId())).isEqualTo("ABANDONED");
        assertThat(genState(handleB.generationId())).isEqualTo("BUILDING");
        assertThat(leaseToken(SCOPE)).isEqualTo(handleB.leaseToken());
    }

    // ---------------------------------------------------------- 矩阵 14

    @Test
    void matrix14_publishFailureRollsBackFullyAndRetrySucceeds() {
        GenerationHandle first = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        publish(first);
        GenerationHandle second = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                        new DirectoryPerson("OU2", "李四", null, List.of("D2"))));

        jdbc.execute("CREATE FUNCTION dir_boom() RETURNS trigger AS $$ "
                + "BEGIN RAISE EXCEPTION 'T7 injected failure'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER dir_fact_boom BEFORE INSERT ON directory_fact "
                + "FOR EACH ROW EXECUTE PROCEDURE dir_boom()");
        try {
            assertThatThrownBy(() -> publish(second))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("T7 injected failure");

            // 回滚不变量：指针/facts/committed/applied（H 回退至 H_before=1）全部回滚，
            // 活动代不变，代回到 VALIDATED、pub_* 未写、租约保留（时钟未走）
            assertThat(committedSeq(SCOPE)).isEqualTo(1);
            assertThat(appliedSeq(SCOPE)).isEqualTo(1);
            assertThat(activeGeneration(SCOPE)).isEqualTo(first.generationId());
            assertThat(factCount(SCOPE)).isEqualTo(2);
            assertThat(genState(second.generationId())).isEqualTo("VALIDATED");
            assertThat(pubResultColumns(second.generationId())).containsOnlyNulls();
            assertThat(leaseToken(SCOPE)).isEqualTo(second.leaseToken());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS dir_fact_boom ON directory_fact");
            jdbc.execute("DROP FUNCTION IF EXISTS dir_boom()");
        }

        // 重试成功：H 1→2、新增事实 updated_seq=2、applied_seq=2、active 切新代
        DirectoryPublication publication = publish(second);
        assertThat(publication.committedSeqBefore()).isEqualTo(1);
        assertThat(publication.committedSeqAfter()).isEqualTo(2);
        assertThat(publication.appliedSeq()).isEqualTo(2);
        assertThat(publication.factsInserted()).isEqualTo(2);
        assertThat(factCount(SCOPE)).isEqualTo(4);
        assertThat(fact(SCOPE, "PERSON", "OU2").get("updated_seq")).isEqualTo(2L);
        assertThat(fact(SCOPE, "PERSON", "OU1").get("updated_seq")).isEqualTo(1L);
        assertThat(activeGeneration(SCOPE)).isEqualTo(second.generationId());
        assertThat(committedSeq(SCOPE)).isEqualTo(2);
        assertThat(appliedSeq(SCOPE)).isEqualTo(2);
    }

    // ---------------------------------------------------------- 矩阵 15

    @Test
    void matrix15_seededTombstoneNotRevivedAndConfrontationRegistered() {
        List<DirectoryDepartment> departments =
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发"));
        List<DirectoryPerson> persons = List.of(
                new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                new DirectoryPerson("OU2", "李四", null, List.of("D2")),
                new DirectoryPerson("OU3", "王五", null, List.of("D2")));
        publish(buildGeneration(SCOPE, departments, persons));

        // 预置墓碑种子（M1 无墓碑写入方，矩阵 15 前置即测试置位）
        jdbc.update("UPDATE directory_fact SET tombstone = true "
                        + "WHERE tenant_id=? AND app_scope=? AND object_type='PERSON' AND external_id='OU2'",
                SCOPE.tenantKey(), SCOPE.appScope());

        publish(buildGeneration(SCOPE, departments, persons));

        // 规则 D：墓碑/revision 不变、不复活（pending 也不置位——墓碑行不进规则 B/E）
        Map<String, Object> tombstone = fact(SCOPE, "PERSON", "OU2");
        assertThat(tombstone.get("tombstone")).isEqualTo(true);
        assertThat(tombstone.get("local_revision")).isEqualTo(1L);
        assertThat(tombstone.get("updated_seq")).isEqualTo(1L);
        assertThat(tombstone.get("pending_confirm")).isEqualTo(false);
        // 待核对登记：范围级 tombstone-confrontation（PG 层新增怀疑类型）
        assertThat(suspicionTypes(SCOPE)).contains("tombstone-confrontation");
        // readObservation 如实含该对象（观察侧=本代镜像，墓碑不删观察）
        assertThat(store.readObservation(SCOPE).personCount()).isEqualTo(3);
        // 事实侧只读视图：墓碑行可见
        assertThat(store.readProtectedFacts(SCOPE).stream()
                .filter(f -> f.externalId().equals("OU2") && f.objectType() == ProtectedFactView.ObjectType.PERSON))
                .singleElement()
                .satisfies(f -> assertThat(f.tombstone()).isTrue());
    }

    // ---------------------------------------------------------- 矩阵 16

    @Test
    void matrix16_departmentShrinkRegistersScopeSuspectWithZeroFactChange() {
        publish(buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部"),
                        new DirectoryDepartment("D2", "D1", "研发"),
                        new DirectoryDepartment("D3", "D1", "运营")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")))));

        // 新代部门列表缺 D3（配置指纹不变——scope 相同，上游权限面收缩）
        GenerationHandle shrunk = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部"),
                        new DirectoryDepartment("D2", "D1", "研发")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        publish(shrunk);

        // 零墓碑、D3 事实行完全不动（部门缺失不进规则 B——人员级标记才适用）
        assertThat(tombstoneCount(SCOPE)).isZero();
        Map<String, Object> d3 = fact(SCOPE, "DEPARTMENT", "D3");
        assertThat(d3.get("tombstone")).isEqualTo(false);
        assertThat(d3.get("local_revision")).isEqualTo(1L);
        assertThat(d3.get("updated_seq")).isEqualTo(1L);
        assertThat(d3.get("pending_confirm")).isEqualTo(false);
        // 范围级待核对（scope-suspect）+ readObservation 附收缩 observed_scope
        assertThat(suspicionTypes(SCOPE)).contains("department-scope-shrunk");
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.observedScope().departmentIds()).containsExactly("D1", "D2");
        // 空差异：H/applied 保持 1
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        assertThat(factCount(SCOPE)).isEqualTo(4);
    }

    // ---------------------------------------------------------- 矩阵 17 + 18

    @Test
    void matrix17And18_missingPersonMarkedPendingThenClearedOnReappearance() {
        List<DirectoryDepartment> departments =
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", "D1", "研发"));
        publish(buildGeneration(SCOPE, departments, List.of(
                new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                new DirectoryPerson("OU2", "李四", null, List.of("D2")))));

        // 新代缺 OU2（部门全集不变：四条件成立——指纹一致、未收缩、有上一发布代）
        publish(buildGeneration(SCOPE, departments,
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")))));

        // 矩阵 17：零墓碑；仅 pending_confirm=true（scan-missing）；revision/updated_seq 不变
        assertThat(tombstoneCount(SCOPE)).isZero();
        Map<String, Object> missing = fact(SCOPE, "PERSON", "OU2");
        assertThat(missing.get("pending_confirm")).isEqualTo(true);
        assertThat(missing.get("local_revision")).isEqualTo(1L);
        assertThat(missing.get("updated_seq")).isEqualTo(1L);
        assertThat(missing.get("tombstone")).isEqualTo(false);
        // 置位不动事实水位（§5.1 列注）；主体状态不变（事实层不进授权路径，无 principal 写入）
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        // 差异报表待核对行：readProtectedFacts 事实侧 pending 过滤面
        assertThat(store.readProtectedFacts(SCOPE).stream()
                .filter(ProtectedFactView::pendingConfirm))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.objectType()).isEqualTo(ProtectedFactView.ObjectType.PERSON);
                    assertThat(f.externalId()).isEqualTo("OU2");
                });

        // 矩阵 18：下轮 OU2 复现 → 标记消解（规则 E 复位），revision/updated_seq 仍不动、零墓碑
        publish(buildGeneration(SCOPE, departments, List.of(
                new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                new DirectoryPerson("OU2", "李四", null, List.of("D2")))));
        Map<String, Object> reappeared = fact(SCOPE, "PERSON", "OU2");
        assertThat(reappeared.get("pending_confirm")).isEqualTo(false);
        assertThat(reappeared.get("local_revision")).isEqualTo(1L);
        assertThat(reappeared.get("updated_seq")).isEqualTo(1L);
        assertThat(tombstoneCount(SCOPE)).isZero();
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        assertThat(factCount(SCOPE)).isEqualTo(4);
    }

    // ------------------------------------------ 矩阵 17 缩容—恢复缺口（W3C-R1 回归）
    // 复评探针 S3 场景：无缩容直接缺失的对照 = 上方 matrix17And18；本测试为缺口组——
    // G1 全量 → G2 缩容（范围级 suspect，人员级零标记是设计正确行为）→ G3 恢复但
    // 人员未回：§6.4 四条件全部成立（指纹一致 ∧ 未缩容 ∧ 有上一发布代），规则 B 必须
    // 置 pending=true。旧实现候选集 join 上一发布代 person_mirror——OU2 不在被缩容的
    // G2 镜像中 → 永不置位，且 G3 起 scope_suspicion 已清空，人员从任何管理面消失。

    @Test
    void matrix17_personMissingAcrossShrunkGenerationStillPendingAfterScopeRestore() {
        List<DirectoryDepartment> full = List.of(
                new DirectoryDepartment("D1", null, "总部"),
                new DirectoryDepartment("D2", "D1", "研发"),
                new DirectoryDepartment("D3", "D1", "销售"));
        List<DirectoryDepartment> shrunk = List.of(
                new DirectoryDepartment("D1", null, "总部"),
                new DirectoryDepartment("D2", "D1", "研发"));
        List<DirectoryPerson> both = List.of(
                new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                new DirectoryPerson("OU2", "李四", null, List.of("D3")));
        List<DirectoryPerson> onlyOu1 = List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")));

        // G1 全量发布：OU2 获事实行（5 事实：D1/D2/D3/OU1/OU2）
        publish(buildGeneration(SCOPE, full, both));
        assertThat(fact(SCOPE, "PERSON", "OU2").get("pending_confirm")).isEqualTo(false);

        // G2 缩容（D3 整体缺 → OU2 随之缺）：范围级 suspect、零人员级标记——设计正确行为
        publish(buildGeneration(SCOPE, shrunk, onlyOu1));
        assertThat(fact(SCOPE, "PERSON", "OU2").get("pending_confirm")).isEqualTo(false);
        assertThat(suspicionTypes(SCOPE)).contains("department-scope-shrunk");

        // G3 恢复（部门全集回来、OU2 仍未回）：证据完整代——规则 B 应置 pending
        publish(buildGeneration(SCOPE, full, onlyOu1));

        assertThat(tombstoneCount(SCOPE)).isZero();
        Map<String, Object> missing = fact(SCOPE, "PERSON", "OU2");
        assertThat(missing.get("pending_confirm")).isEqualTo(true);
        assertThat(missing.get("tombstone")).isEqualTo(false);
        assertThat(missing.get("local_revision")).isEqualTo(1L);
        assertThat(missing.get("updated_seq")).isEqualTo(1L);
        // G3 起范围级怀疑已清空——人员级待核对是 OU2 在管理面的唯一信号，必须在场
        assertThat(suspicionTypes(SCOPE)).doesNotContain("department-scope-shrunk");
        assertThat(store.readProtectedFacts(SCOPE).stream()
                .filter(ProtectedFactView::pendingConfirm))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.objectType()).isEqualTo(ProtectedFactView.ObjectType.PERSON);
                    assertThat(f.externalId()).isEqualTo("OU2");
                });
        // 缩容代/恢复代均零新事实：水位全程不动
        assertThat(committedSeq(SCOPE)).isEqualTo(1);
        assertThat(appliedSeq(SCOPE)).isEqualTo(1);
        assertThat(factCount(SCOPE)).isEqualTo(5);
    }

    // ---------------------------------------------------------- 矩阵 28

    @Test
    void matrix28_fieldChangeUpdatesRawOnlyFactsAnchorUnchanged() {
        publish(buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "Alice", null, List.of("D1")))));

        GenerationHandle second = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "Alicia", null, List.of("D1"))));
        publish(second);

        // 第一层：raw = 新值（活动代镜像随每轮全量刷新）
        assertThat(jdbc.queryForObject(
                "SELECT name FROM directory_person_mirror WHERE generation_id=? AND open_id='OU1'",
                String.class, second.generationId())).isEqualTo("Alicia");
        // 第二层：facts 原内容不变（规则 C 零修改：锚点=首次观察，普通展示字段不在此层更新）
        assertThat(jdbc.queryForObject(
                "SELECT fact->>'name' FROM directory_fact "
                        + "WHERE tenant_id=? AND app_scope=? AND object_type='PERSON' AND external_id='OU1'",
                String.class, SCOPE.tenantKey(), SCOPE.appScope())).isEqualTo("Alice");
        assertThat(fact(SCOPE, "PERSON", "OU1").get("local_revision")).isEqualTo(1L);
        assertThat(fact(SCOPE, "PERSON", "OU1").get("updated_seq")).isEqualTo(1L);
        // 第三层：差异报表列内容分歧（SQL 级呈现——DiffReporter 冻结端口不暴露字段级，
        // 人员侧仅计数差；字段级分歧锚点 vs 观察的join 面属 M2 readEffective 消解）
        Map<String, Object> divergence = jdbc.queryForMap(
                "SELECT m.name AS observed_name, f.fact->>'name' AS anchored_name "
                        + "FROM directory_person_mirror m JOIN directory_fact f "
                        + "ON f.tenant_id=? AND f.app_scope=? AND f.object_type='PERSON' AND f.external_id=m.open_id "
                        + "WHERE m.generation_id=?",
                SCOPE.tenantKey(), SCOPE.appScope(), second.generationId());
        assertThat(divergence.get("observed_name")).isEqualTo("Alicia");
        assertThat(divergence.get("anchored_name")).isEqualTo("Alice");
        // readObservation 计数面不变（字段值不在冻结端口内，计数字段如实）
        assertThat(store.readObservation(SCOPE).personCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------- 矩阵 29

    @Test
    void matrix29_newObjectsAdvanceWatermark() {
        publish(buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")))));

        GenerationHandle second = buildGeneration(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "总部"),
                        new DirectoryDepartment("D9", "D1", "新设")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                        new DirectoryPerson("OU9", "新同事", null, List.of("D9"))));

        // start_seq=1（begin 事务内读到的 committed_seq）
        assertThat(startSeq(second.generationId())).isEqualTo(1);

        DirectoryPublication publication = publish(second);

        // §6.7.1 场景③-a 数值钉：H_before=1、INSERT×2、H_after=2、
        // 新事实 updated_seq=2、applied_seq=2、active→新代
        assertThat(publication.committedSeqBefore()).isEqualTo(1);
        assertThat(publication.factsInserted()).isEqualTo(2);
        assertThat(publication.committedSeqAfter()).isEqualTo(2);
        assertThat(publication.appliedSeq()).isEqualTo(2);
        assertThat(fact(SCOPE, "PERSON", "OU9").get("updated_seq")).isEqualTo(2L);
        assertThat(fact(SCOPE, "DEPARTMENT", "D9").get("updated_seq")).isEqualTo(2L);
        assertThat(fact(SCOPE, "PERSON", "OU1").get("updated_seq")).isEqualTo(1L);
        assertThat(activeGeneration(SCOPE)).isEqualTo(second.generationId());
        assertThat(committedSeq(SCOPE)).isEqualTo(2);
        assertThat(appliedSeq(SCOPE)).isEqualTo(2);
        assertThat(factCount(SCOPE)).isEqualTo(4);
    }

    // ---------------------------------------------------------- 编排门禁（矩阵 4 读面兜底）

    @Test
    void writeWithoutTransactionIsRejectedByMandatoryGate() {
        // MANDATORY 门禁：无事务直调写端口即拒（适配器不开事务不自行提交）
        assertThatThrownBy(() -> store.beginGeneration(SCOPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MANDATORY");
    }

    // ---------------------------------------------------------- 助手

    private GenerationHandle begin(DirectoryScope scope) {
        return runner.inTransaction(() -> store.beginGeneration(scope));
    }

    private void write(GenerationHandle handle, DirectoryBatch batch) {
        runner.inTransaction(() -> {
            store.writeBatch(handle, batch);
            return null;
        });
    }

    private void runInTx(Runnable work) {
        runner.inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** 建代到 VALIDATED：begin → 部门批 → 人员批 → completePaging → validate。 */
    private GenerationHandle buildGeneration(DirectoryScope scope,
                                             List<DirectoryDepartment> departments,
                                             List<DirectoryPerson> persons) {
        GenerationHandle handle = begin(scope);
        if (!departments.isEmpty()) {
            write(handle, DirectoryBatch.ofDepartments(departments));
        }
        if (!persons.isEmpty()) {
            write(handle, DirectoryBatch.ofPersons(persons));
        }
        runInTx(() -> store.completePaging(handle));
        runInTx(() -> store.validate(handle));
        return handle;
    }

    private DirectoryPublication publish(GenerationHandle handle) {
        return runner.inTransaction(() -> store.publish(handle));
    }

    private String genState(String generationId) {
        return jdbc.queryForObject(
                "SELECT state FROM directory_generation WHERE id=?", String.class, generationId);
    }

    private String abandonReason(String generationId) {
        return jdbc.queryForObject(
                "SELECT abandon_reason FROM directory_generation WHERE id=?", String.class, generationId);
    }

    private long startSeq(String generationId) {
        return jdbc.queryForObject(
                "SELECT start_seq FROM directory_generation WHERE id=?", Long.class, generationId);
    }

    private int pagesDone(String generationId) {
        return jdbc.queryForObject(
                "SELECT pages_done FROM directory_generation WHERE id=?", Integer.class, generationId);
    }

    private List<Long> pubResultColumns(String generationId) {
        // Arrays.asList 允许 null（发布前 pub_* 四列按设计为 NULL）
        return jdbc.queryForObject(
                "SELECT pub_committed_before, pub_committed_after, pub_applied_seq, pub_facts_inserted "
                        + "FROM directory_generation WHERE id=?",
                (rs, i) -> java.util.Arrays.asList(
                        (Long) rs.getObject(1), (Long) rs.getObject(2),
                        (Long) rs.getObject(3), (Long) rs.getObject(4)),
                generationId);
    }

    private String activeGeneration(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT active_generation FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, scope.tenantKey(), scope.appScope());
    }

    private long committedSeq(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT committed_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, scope.tenantKey(), scope.appScope());
    }

    private long appliedSeq(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT applied_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, scope.tenantKey(), scope.appScope());
    }

    private String leaseToken(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT lease_token FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, scope.tenantKey(), scope.appScope());
    }

    private String leaseHolder(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT lease_holder FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, scope.tenantKey(), scope.appScope());
    }

    private int factCount(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM directory_fact WHERE tenant_id=? AND app_scope=?",
                Integer.class, scope.tenantKey(), scope.appScope());
    }

    private int tombstoneCount(DirectoryScope scope) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM directory_fact WHERE tenant_id=? AND app_scope=? AND tombstone",
                Integer.class, scope.tenantKey(), scope.appScope());
    }

    private Map<String, Object> fact(DirectoryScope scope, String objectType, String externalId) {
        return jdbc.queryForMap(
                "SELECT local_revision, tombstone, pending_confirm, updated_seq, fact "
                        + "FROM directory_fact WHERE tenant_id=? AND app_scope=? AND object_type=? AND external_id=?",
                scope.tenantKey(), scope.appScope(), objectType, externalId);
    }

    private List<String> suspicionTypes(DirectoryScope scope) {
        return jdbc.queryForList(
                "SELECT e->>'type' FROM directory_publish_metadata m, jsonb_array_elements(m.scope_suspicion) e "
                        + "WHERE m.tenant_id=? AND m.app_scope=?",
                String.class, scope.tenantKey(), scope.appScope());
    }

    private int mirrorRowCount(String generationId) {
        return jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM directory_department_mirror WHERE generation_id=?) "
                        + "+ (SELECT count(*) FROM directory_person_mirror WHERE generation_id=?)",
                Integer.class, generationId, generationId);
    }
}
