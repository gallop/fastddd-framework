package com.gallop.fastddd.identity.core.directory;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryGeneration;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.person;
import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.root;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3-a 编排用例测试（W3 设计 §2/§5.2/§6.5；复评钉住事项：编排以测试替身验证，
 * 不伪称 PG 并发协议已成立）。覆盖：正常多页流程与调用顺序、空目录、
 * 分页失败/页数护栏/游标异常、validate 失败不发布、租约占用/过期/丢失、
 * 无 handle 不 abandon、清理失败不掩盖原始异常、系统作业身份、M1 形态零要求钉
 * （W5-b T8 演进，见 m1FormConstructorRemainsZeroRequirement）。
 */
class DirectorySyncUseCaseTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-a", "app-1");

    /** 记录调用顺序的委托包装（顺序断言用）。 */
    private static final class RecordingStore implements DirectoryMirrorStore {
        final DirectoryMirrorStore delegate;
        final List<String> calls = new ArrayList<>();

        RecordingStore(DirectoryMirrorStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            calls.add("begin");
            return delegate.beginGeneration(scope);
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            calls.add("renew");
            return delegate.renewLease(handle);
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            calls.add("write:" + batch.departments().size() + "d/" + batch.persons().size() + "p");
            delegate.writeBatch(handle, batch);
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            calls.add("completePaging");
            delegate.completePaging(handle);
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            calls.add("validate");
            return delegate.validate(handle);
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            calls.add("publish");
            return delegate.publish(handle);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            calls.add("abandon:" + reason);
            delegate.abandon(handle, reason);
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return delegate.readObservation(scope);
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return delegate.readProtectedFacts(scope);
        }
    }

    private final InMemoryDirectoryMirrorStore store =
            new InMemoryDirectoryMirrorStore(Duration.ofMinutes(10));

    /** 直通事务 runner：core 层只验编排逻辑；事务所有者语义在 starter 的
     *  DirectoryTransactionBoundaryTest 用真实 Spring 事务设施验证（W3A-01）。 */
    private static final IdentityTransactionRunner DIRECT_TX = new IdentityTransactionRunner() {
        @Override
        public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
            return callback.doInTransaction();
        }
    };

    private ScriptedDirectorySource sourceOf(List<String> deptIds, Map<String, List<String>> personsByDept) {
        Map<String, List<com.gallop.fastddd.identity.core.domain.DirectoryPerson>> persons = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : personsByDept.entrySet()) {
            List<com.gallop.fastddd.identity.core.domain.DirectoryPerson> list = new ArrayList<>();
            for (String openId : entry.getValue()) {
                list.add(person(openId, entry.getKey()));
            }
            persons.put(entry.getKey(), list);
        }
        List<com.gallop.fastddd.identity.core.domain.DirectoryDepartment> depts = new ArrayList<>();
        for (String id : deptIds) {
            depts.add(root(id));
        }
        return new ScriptedDirectorySource(SCOPE, depts, persons);
    }

    private DirectorySyncUseCase useCase(ScriptedDirectorySource source, int pageSize, int maxPages) {
        return new DirectorySyncUseCase(source, store, DIRECT_TX, pageSize, maxPages);
    }

    // --------------------------------------------------------------- 正常流程

    @Test
    void happyPathMultiPageFollowsContractOrderAndPublishes() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1", "P2"), "D2", List.of("P3")));
        RecordingStore recording = new RecordingStore(store);
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 100);

        DirectoryPublication publication = useCase.runFullSync(SCOPE);

        // 调用顺序（W3A-03 后）：begin → 部门页0写批（全局首次源调用前不续租）
        //          → 续租 → 部门页1写批 → 续租 → D1 人员页0写批 → 续租
        //          → D1 人员页1写批 → 续租 → D2 人员页0写批 → 续租（末页到
        //          completePaging 的阶段边界）→ completePaging → validate → publish
        assertThat(recording.calls).containsExactly(
                "begin",
                "write:1d/0p", "renew", "write:1d/0p",
                "renew", "write:0d/1p", "renew", "write:0d/1p", "renew", "write:0d/1p",
                "renew", "completePaging", "validate", "publish");
        // 水位（§6.7.1 ① 形态）：2 部门 + 3 人员 = 5 新键，committed 0→1、applied=1
        assertThat(publication.factsInserted()).isEqualTo(5);
        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(publication.generationId());
        assertThat(store.stateOf(SCOPE, publication.generationId()))
                .isEqualTo(DirectoryGeneration.State.PUBLISHED);
    }

    @Test
    void emptyDirectoryPublishesWithNoFactAdvance() {
        // 复评数值复核：空目录首次发布按"无 INSERT"分支——committed_seq 不推进
        ScriptedDirectorySource source = new ScriptedDirectorySource(SCOPE, List.of(), Map.of());
        DirectoryPublication publication = useCase(source, 100, 100).runFullSync(SCOPE);

        assertThat(publication.factsInserted()).isZero();
        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isZero();
        assertThat(publication.appliedSeq()).isZero();
        assertThat(store.committedSeq(SCOPE)).isZero();
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
        // 但代已发布、成为活动代（空目录合法，不是错误）
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(publication.generationId());
    }

    // --------------------------------------------------------------- 源侧失败

    @Test
    void sourceUnavailableAbandonsWholeGeneration() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        source.departmentsHook = ScriptedDirectorySource.unavailableAt(1);
        RecordingStore recording = new RecordingStore(store);
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 100);

        assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class);

        assertThat(recording.calls).contains("abandon:source-unavailable");
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        assertThat(store.committedSeq(SCOPE)).isZero();
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
        // 用例不重试分页（重试责任在源——一次失败即整代废弃）
        assertThat(source.calls.stream().filter(c -> c.startsWith("departments:")).count())
                .isEqualTo(2);
    }

    @Test
    void sourceForbiddenFailsFastAndAbandons() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        source.departmentsHook = ScriptedDirectorySource.forbiddenAt(0);
        RecordingStore recording = new RecordingStore(store);

        assertThatThrownBy(() -> new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceForbiddenException.class);

        assertThat(recording.calls).contains("abandon:source-forbidden");
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
    }

    @Test
    void personsPageFailureAbandonsWholeGeneration() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        source.personsHook = dept -> {
            throw new DirectorySourceUnavailableException("transport", "人员页瞬时不可用");
        };

        assertThatThrownBy(() -> useCase(source, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class);

        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
        assertThat(store.activeGenerationId(SCOPE)).isNull();
    }

    // --------------------------------------------------------- 分页护栏与契约

    @Test
    void cursorNotAdvancingIsContractViolationAndAbandons() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        source.departmentsCustomizer = ScriptedDirectorySource.cursorNotAdvancing();
        RecordingStore recording = new RecordingStore(store);

        assertThatThrownBy(() -> new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 100).runFullSync(SCOPE))
                .isInstanceOfSatisfying(DirectoryContractViolationException.class,
                        ex -> assertThat(ex.reasonCode()).isEqualTo("cursor-not-advancing"));

        assertThat(recording.calls).contains("abandon:contract-violation");
    }

    @Test
    void hasMoreWithoutCursorIsContractViolation() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        source.departmentsCustomizer = ScriptedDirectorySource.hasMoreWithoutCursor();

        assertThatThrownBy(() -> useCase(source, 1, 100).runFullSync(SCOPE))
                .isInstanceOfSatisfying(DirectoryContractViolationException.class,
                        ex -> assertThat(ex.reasonCode()).isEqualTo("has-more-without-cursor"));
    }

    @Test
    void maxPagesGuardRejectsInfinitePaging() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2", "D3"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2"), "D3", List.of("P3")));
        RecordingStore recording = new RecordingStore(store);
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 2);

        assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                .isInstanceOfSatisfying(DirectoryContractViolationException.class,
                        ex -> assertThat(ex.reasonCode()).isEqualTo("max-pages-exceeded"));

        // W3A-05：预算在外呼前检查——恰好 2 次源调用（第 3 页不再发出即拒绝）
        assertThat(source.calls).containsExactly("departments:0", "departments:1");
        assertThat(recording.calls).contains("abandon:contract-violation");
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
    }

    // ------------------------------------------- W3A-03：按扫描进度续租（复评反例）

    /**
     * 复评反例（W3-a 实现独立复评意见 W3A-03）：3 个单页部门，每次源调用推进
     * 可控时钟 4 分钟——每次请求远短于租约 10 分钟，但累计 16 分钟超过 TTL。
     * 旧实现只在同流 hasMore=true 时续租（单页部门零续租），跨部门扫描在写批时
     * 过期、整代废弃。修复后按完整扫描进度续租（覆盖部门流结束/切换部门/末页到
     * 校验发布的阶段边界），总耗时超过 TTL 仍可发布。
     */
    @Test
    void crossDepartmentSinglePageScanRenewsByProgressAndPublishesPastTtl() {
        AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-09-28T00:00:00Z"));
        store.setClock(now::get);
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2", "D3"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2"), "D3", List.of("P3")));
        source.departmentsHook = page -> now.set(now.get().plus(Duration.ofMinutes(4)));
        source.personsHook = dept -> now.set(now.get().plus(Duration.ofMinutes(4)));

        DirectoryPublication publication = useCase(source, 100, 100).runFullSync(SCOPE);

        // 4 次源调用 × 4 分钟 = 累计 16 分钟 > 租约 10 分钟：进度续租下仍完成发布
        assertThat(store.stateOf(SCOPE, publication.generationId()))
                .isEqualTo(DirectoryGeneration.State.PUBLISHED);
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(publication.generationId());
        assertThat(publication.factsInserted()).isEqualTo(6);
    }

    /** W3A-03 验收：混合多页/单页部门在慢时钟（每页推进超过半程 TTL）下同样发布。 */
    @Test
    void mixedMultiAndSinglePageDepartmentsRenewAcrossBoundariesAndPublish() {
        AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-09-28T00:00:00Z"));
        store.setClock(now::get);
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1", "P2"), "D2", List.of("P3")));
        source.departmentsHook = page -> now.set(now.get().plus(Duration.ofMinutes(4)));
        source.personsHook = dept -> now.set(now.get().plus(Duration.ofMinutes(4)));

        DirectoryPublication publication = useCase(source, 1, 100).runFullSync(SCOPE);

        // 5 次源调用 × 4 分钟 = 累计 20 分钟 > 10 分钟 TTL（页内/跨部门/末页边界均有覆盖）
        assertThat(store.stateOf(SCOPE, publication.generationId()))
                .isEqualTo(DirectoryGeneration.State.PUBLISHED);
    }

    // --------------------------------------- W3A-05：整轮共享页数预算（复评反例）

    /**
     * 复评反例（W3A-05）：maxPagesPerRun=1——旧实现部门流与人员流各 new 一个
     * PageCounter 分别计数，且计数发生在读取之后（先发出超限请求再拒绝），
     * 独立探针得到 {@code budget=1 published reads=2}。修复后整轮共享预算、
     * 在发出下一次源调用前检查：预算耗尽即不再外呼、不 publish、废弃自家构建代。
     */
    @Test
    void pageBudgetIsSharedAcrossStreamsAndCheckedBeforeSourceCall() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        RecordingStore recording = new RecordingStore(store);
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, recording, DIRECT_TX, 100, 1);

        assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                .isInstanceOfSatisfying(DirectoryContractViolationException.class,
                        ex -> assertThat(ex.reasonCode()).isEqualTo("max-pages-exceeded"));

        // 预算 1 已被部门页消耗：人员页外呼在发出前被拒（源调用恰 1 次，非 2 次）
        assertThat(source.calls).containsExactly("departments:0");
        assertThat(recording.calls).contains("abandon:contract-violation");
        assertThat(recording.calls).doesNotContain("publish");
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
    }

    /** W3A-05 验收：跨流与跨部门边界共用预算；恰好达上限可发布，超限的下一次外呼被前置于拒绝。 */
    @Test
    void pageBudgetCoversCrossStreamAndCrossDepartmentBoundaries() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));

        // 部门 2 页 + D1 人员 1 页 + D2 人员 1 页 = 4 次外呼：预算恰为 4 → 发布
        assertThat(useCase(source, 1, 4).runFullSync(SCOPE).factsInserted()).isEqualTo(4);

        // 预算 3：D2 人员页在发出前被拒（旧实现两流各计 1+2=均未超限，会错误发布）
        ScriptedDirectorySource again = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        RecordingStore recording = new RecordingStore(store);
        DirectorySyncUseCase tight = new DirectorySyncUseCase(again, recording, DIRECT_TX, 1, 3);
        assertThatThrownBy(() -> tight.runFullSync(SCOPE))
                .isInstanceOfSatisfying(DirectoryContractViolationException.class,
                        ex -> assertThat(ex.reasonCode()).isEqualTo("max-pages-exceeded"));
        assertThat(again.calls).containsExactly("departments:0", "departments:1",
                "persons:D1:0");
        assertThat(recording.calls).doesNotContain("publish");
    }

    // ---------------------------------------------------------- 校验与发布门禁

    @Test
    void validateFailureNeverPublishes() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        store.validateFailureHook = () -> {
            throw new DirectoryContractViolationException("dangling-relation", "校验失败注入");
        };
        RecordingStore recording = new RecordingStore(store);

        assertThatThrownBy(() -> new DirectorySyncUseCase(source, recording, DIRECT_TX, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectoryContractViolationException.class);

        assertThat(recording.calls).contains("validate");
        assertThat(recording.calls).doesNotContain("publish");
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        assertThat(recording.calls).contains("abandon:contract-violation");
    }

    @Test
    void publishFailureLeavesNoPublishedGenerationAndAbandons() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        store.publishFailureHook = () -> {
            throw new IllegalStateException("T7 中途失败注入");
        };

        assertThatThrownBy(() -> useCase(source, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(IllegalStateException.class);

        // T7 失败：活动代不变（仍 null）、facts 零变化、代经 abandon 废弃
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        assertThat(store.committedSeq(SCOPE)).isZero();
        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
        assertThat(store.generationIds(SCOPE)).hasSize(1);
        assertThat(store.stateOf(SCOPE, store.generationIds(SCOPE).get(0)))
                .isEqualTo(DirectoryGeneration.State.ABANDONED);
    }

    // --------------------------------------------------------------- 租约语义

    @Test
    void leaseHeldPropagatesWithoutAbandoningForeignGeneration() {
        // 预置他方有效租约（其代处于 BUILDING）
        GenerationHandle foreign = store.beginGeneration(SCOPE);
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        RecordingStore recording = new RecordingStore(store);

        assertThatThrownBy(() -> new DirectorySyncUseCase(source, recording, DIRECT_TX, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectoryLeaseHeldException.class);

        // 无 handle 不 abandon：他方的代保持 BUILDING、未被废弃
        assertThat(recording.calls).containsExactly("begin");
        assertThat(store.stateOf(SCOPE, foreign.generationId()))
                .isEqualTo(DirectoryGeneration.State.BUILDING);
        assertThat(store.abandonReasonOf(SCOPE, foreign.generationId())).isNull();
    }

    @Test
    void expiredLeaseAbandonsOwnGenerationWithStableReason() {
        AtomicReference<Instant> now =
                new AtomicReference<>(Instant.parse("2026-09-28T00:00:00Z"));
        store.setClock(now::get);
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        // 部门第 1 页读取时推进时钟越过租约有效期 → 下一次写批联合校验过期
        source.departmentsHook = page -> {
            if (page >= 1) {
                now.set(now.get().plus(Duration.ofMinutes(11)));
            }
        };

        assertThatThrownBy(() -> useCase(source, 1, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectoryLeaseExpiredException.class);

        // 过期后自家代被废弃（abandon 仅要求 token 匹配，不要求未过期）
        String own = store.generationIds(SCOPE).get(0);
        assertThat(store.abandonReasonOf(SCOPE, own)).isEqualTo("lease-expired");
        assertThat(store.stateOf(SCOPE, own)).isEqualTo(DirectoryGeneration.State.ABANDONED);
        assertThat(store.activeGenerationId(SCOPE)).isNull();
    }

    @Test
    void leaseLostAfterTakeoverAbandonSuppressedNotMasking() {
        ScriptedDirectorySource source = sourceOf(List.of("D1", "D2"),
                Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        // 部门页 0 完成后另一实例接管：后续联合校验全部 LeaseLost；
        // abandon 也因 token 不匹配被拒（保护新持有者）→ suppressed
        source.departmentsHook = page -> {
            if (page >= 1) {
                store.simulateTakeover(SCOPE);
            }
        };
        RecordingStore recording = new RecordingStore(store);

        assertThatThrownBy(() -> new DirectorySyncUseCase(source, recording, DIRECT_TX, 1, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectoryLeaseLostException.class)
                .satisfies(ex -> assertThat(ex.getSuppressed())
                        .hasAtLeastOneElementOfType(DirectoryLeaseLostException.class));

        // 清理已尝试（abandon 出现在调用序列）且原始异常不被掩盖
        assertThat(recording.calls).anySatisfy(call -> assertThat(call).startsWith("abandon:"));
    }

    @Test
    void abandonFailureDoesNotMaskOriginalException() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        source.departmentsHook = ScriptedDirectorySource.unavailableAt(0);
        store.abandonFailureHook = () -> {
            throw new RuntimeException("清理失败注入");
        };

        assertThatThrownBy(() -> useCase(source, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class)
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).hasSize(1);
                    assertThat(ex.getSuppressed()[0]).hasMessage("清理失败注入");
                });
    }

    // ----------------------------------------------------------- 身份与依赖纯度

    @Test
    void runsUnderSystemJobDirectorySyncActor() {
        ScriptedDirectorySource source = sourceOf(List.of("D1"), Map.of("D1", List.of("P1")));
        useCase(source, 100, 100).runFullSync(SCOPE);

        assertThat(source.observedActors).isNotEmpty();
        assertThat(source.observedActors).allSatisfy(actorOpt -> {
            assertThat(actorOpt).isPresent();
            Actor actor = actorOpt.orElseThrow();
            assertThat(actor.principalId().value()).isEqualTo("system-job:directory-sync");
            assertThat(actor.principalType()).isEqualTo(PrincipalType.SYSTEM_JOB);
        });
    }

    @Test
    void m1FormConstructorRemainsZeroRequirement() {
        // W5-b T8 演进（W5 设计 §12 ⑤）：5 参 M1 构造形态保留、签名不变——
        // 既有装配（starter/宿主）零改动零新增依赖。W3-a「构造参数与字段不得
        // 出现 publisher」钉随 T8 组合解除（usecase 包 T8 组合测试钉行为面：
        // M1 形态不 append 不发事件）。
        boolean m1FormFound = false;
        for (Constructor<?> constructor : DirectorySyncUseCase.class.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            m1FormFound |= types.length == 5
                    && types[0] == DirectorySource.class
                    && types[1] == DirectoryMirrorStore.class
                    && types[2] == IdentityTransactionRunner.class
                    && types[3] == int.class
                    && types[4] == int.class;
        }
        assertThat(m1FormFound)
                .as("5 参 M1 构造形态必须保留（W5-b T8 兼容钉）")
                .isTrue();
    }
}
