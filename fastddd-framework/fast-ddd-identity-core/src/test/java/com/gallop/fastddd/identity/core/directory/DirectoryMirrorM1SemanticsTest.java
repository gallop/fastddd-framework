package com.gallop.fastddd.identity.core.directory;

import com.gallop.fastddd.identity.core.domain.DirectoryGeneration;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.error.DirectoryInvalidGenerationStateException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.person;
import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.root;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内存替身的 M1 语义测试（W3 设计 §6.2 水位四组数值 / §6.3 规则 A–E /
 * §6.4 墓碑与待核对分离 / §4 状态前置 / §6.1 接管与迟到操作）。
 *
 * <p>口径：<b>只证明协议语义在编排层成立</b>——PG 行锁/原子性/回滚属 W3-c
 * 真实存储验收，本类不作为其证据（复评钉住事项 ⑤）。</p>
 */
class DirectoryMirrorM1SemanticsTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-a", "app-1");

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-09-28T00:00:00Z"));
    private final InMemoryDirectoryMirrorStore store =
            new InMemoryDirectoryMirrorStore(Duration.ofMinutes(10));

    DirectoryMirrorM1SemanticsTest() {
        store.setClock(now::get);
    }

    /** 跑一轮全量同步（脚本源 + 本替身）并返回发布结果。 */
    private DirectoryPublication runSync(List<String> deptIds, Map<String, List<String>> personsByDept) {
        ScriptedDirectorySource source = new ScriptedDirectorySource(SCOPE,
                deptIds.stream().map(DirectoryTestDsl::root).toList(),
                personsByDept.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(id -> person(id, e.getKey())).toList())));
        return new DirectorySyncUseCase(source, store, 100, 100).runFullSync(SCOPE);
    }

    private ProtectedFactView personFact(String openId) {
        return store.readProtectedFacts(SCOPE).stream()
                .filter(f -> f.objectType() == ProtectedFactView.ObjectType.PERSON)
                .filter(f -> f.externalId().equals(openId))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------- 水位四组数值（§6.7.1）

    @Test
    void watermarkTable_firstPublish_thenNoChange_thenAdditions() {
        // ① 空库首次发布（1 部门 + 2 人员 = 3 对象）
        DirectoryPublication first = runSync(List.of("D1"), Map.of("D1", List.of("P1", "P2")));
        assertThat(first.committedSeqBefore()).isZero();
        assertThat(first.committedSeqAfter()).isEqualTo(1);
        assertThat(first.appliedSeq()).isEqualTo(1);
        assertThat(first.factsInserted()).isEqualTo(3);
        assertThat(personFact("P1").updatedSeq()).isEqualTo(1);
        assertThat(personFact("P2").updatedSeq()).isEqualTo(1);
        assertThat(store.committedSeq(SCOPE)).isEqualTo(1);

        // ② 无变更再发布：committed/applied 保持 1，仅 generation 前进
        DirectoryPublication second = runSync(List.of("D1"), Map.of("D1", List.of("P1", "P2")));
        assertThat(second.factsInserted()).isZero();
        assertThat(second.committedSeqBefore()).isEqualTo(1);
        assertThat(second.committedSeqAfter()).isEqualTo(1);
        assertThat(second.appliedSeq()).isEqualTo(1);
        assertThat(store.committedSeq(SCOPE)).isEqualTo(1);
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(second.generationId());
        assertThat(second.generationId()).isNotEqualTo(first.generationId());

        // ③-a 新增发布（+1 人员 +1 部门 = 2 新键）：H 1→2、新事实 updated_seq=2
        DirectoryPublication third = runSync(List.of("D1", "D2"),
                Map.of("D1", List.of("P1", "P2"), "D2", List.of("P3")));
        assertThat(third.committedSeqBefore()).isEqualTo(1);
        assertThat(third.committedSeqAfter()).isEqualTo(2);
        assertThat(third.appliedSeq()).isEqualTo(2);
        assertThat(third.factsInserted()).isEqualTo(2);
        assertThat(personFact("P3").updatedSeq()).isEqualTo(2);
        // 既有事实不被触碰（规则 E）
        assertThat(personFact("P1").updatedSeq()).isEqualTo(1);
        assertThat(personFact("P1").localRevision()).isEqualTo(1);
    }

    // ------------------------------------------------------------ 规则 B/D（§6.3/§6.4）

    @Test
    void personMissing_setsPendingConfirmOnly_noTombstoneNoWatermarkAdvance() {
        runSync(List.of("D1", "D2"), Map.of("D1", List.of("P1"), "D2", List.of("P2")));

        // 人员级过滤/真删（M1 不可区分同形）：P1 不再返回，部门范围不变
        DirectoryPublication after = runSync(List.of("D1", "D2"), Map.of("D1", List.of(), "D2", List.of("P2")));

        ProtectedFactView p1 = personFact("P1");
        assertThat(p1.pendingConfirm()).isTrue();      // 仅置待核对
        assertThat(p1.tombstone()).isFalse();          // 零墓碑（M1 无写入方）
        assertThat(p1.localRevision()).isEqualTo(1);   // revision 不动
        assertThat(p1.updatedSeq()).isEqualTo(1);      // updated_seq 不动
        assertThat(after.factsInserted()).isZero();    // 水位不推进
        assertThat(store.committedSeq(SCOPE)).isEqualTo(1);

        // 复现后标记消解（§6.4 复位条件：后续扫描再次观察到）
        runSync(List.of("D1", "D2"), Map.of("D1", List.of("P1"), "D2", List.of("P2")));
        assertThat(personFact("P1").pendingConfirm()).isFalse();
        assertThat(store.committedSeq(SCOPE)).isEqualTo(1);
    }

    @Test
    void departmentScopeShrunk_scopeSuspectsRegistered_personRowsUntouched() {
        runSync(List.of("D1", "D2"), Map.of("D1", List.of("P1"), "D2", List.of("P2")));

        // 部门级范围收缩：D2 整部门不再返回（无错误码）
        runSync(List.of("D1"), Map.of("D1", List.of("P1")));

        // 范围级待核对 = 可查询承载（ObservedSnapshot.scopeSuspicions，非仅日志）
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.scopeSuspicions()).hasSize(1);
        assertThat(observation.scopeSuspicions().get(0).type()).isEqualTo("department-scope-shrunk");
        // 范围存疑 → 人员行完全不动（不置 pending_confirm——scope-suspect 与 scan-missing 分流）
        ProtectedFactView p2 = personFact("P2");
        assertThat(p2.pendingConfirm()).isFalse();
        assertThat(p2.tombstone()).isFalse();
        assertThat(p2.updatedSeq()).isEqualTo(1);
    }

    @Test
    void seededTombstone_neverClearedNorResurrected_byScans() {
        // 矩阵 15 同法：M1 无墓碑写入方，用种子验证"不被扫描清除"（规则 D）
        store.seedFact(SCOPE, new InMemoryDirectoryMirrorStore.SeedFact(
                ProtectedFactView.ObjectType.PERSON, "PX", 5, true));
        store.seedFact(SCOPE, new InMemoryDirectoryMirrorStore.SeedFact(
                ProtectedFactView.ObjectType.DEPARTMENT, "DX", 3, true));

        // 墓碑对象在新扫描中再次出现：观察层如实含、事实层不变不复活
        runSync(List.of("DX"), Map.of("DX", List.of("PX")));

        ProtectedFactView px = personFact("PX");
        assertThat(px.tombstone()).isTrue();
        assertThat(px.localRevision()).isEqualTo(5);
        ProtectedFactView dx = store.readProtectedFacts(SCOPE).stream()
                .filter(f -> f.objectType() == ProtectedFactView.ObjectType.DEPARTMENT
                        && f.externalId().equals("DX"))
                .findFirst().orElseThrow();
        assertThat(dx.tombstone()).isTrue();
        assertThat(dx.localRevision()).isEqualTo(3);
        // 观察快照如实呈现观察结果（不代表有效——§6.7.2 第一行）
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.personCount()).isEqualTo(1);
        assertThat(observation.observedScope().departmentIds()).containsExactly("DX");
    }

    @Test
    void publishFailure_rollsBackAllWatermarkAndFactChanges() {
        // §6.7.1 ④：发布事务回滚——指针/facts/committed/applied 全部回退，代保持可处置
        store.publishFailureHook = () -> {
            throw new IllegalStateException("T7 注入失败");
        };
        assertThatThrownBy(() -> runSync(List.of("D1"), Map.of("D1", List.of("P1"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.readProtectedFacts(SCOPE)).isEmpty();
        assertThat(store.committedSeq(SCOPE)).isZero();
        assertThat(store.appliedSeq(SCOPE)).isZero();
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        assertThat(store.generationIds(SCOPE)).hasSize(1);
        assertThat(store.stateOf(SCOPE, store.generationIds(SCOPE).get(0)))
                .isEqualTo(DirectoryGeneration.State.ABANDONED);

        // 恢复后再跑：从零正常发布（失败不残留）
        store.publishFailureHook = null;
        DirectoryPublication retried = runSync(List.of("D1"), Map.of("D1", List.of("P1")));
        assertThat(retried.committedSeqAfter()).isEqualTo(1);
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(retried.generationId());
    }

    // ------------------------------------------------------- 状态前置与幂等（§4）

    @Test
    void validatedGeneration_rejectsLateWriteBatch_andPublishIsIdempotent() {
        ScriptedDirectorySource source = new ScriptedDirectorySource(SCOPE,
                List.of(root("D1")), Map.of("D1", List.of(person("P1", "D1"))));
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, store, 100, 100);
        DirectoryPublication publication = useCase.runFullSync(SCOPE);

        GenerationHandle stale = new GenerationHandle(SCOPE, publication.generationId(),
                "stale-token");
        // 矩阵 12：验证完成后追加批次被拒（此替身以 token 失配先行拒绝——状态前置
        // 与 token 校验同事务联合判定，二者任一不满足即拒绝零落库）
        assertThatThrownBy(() -> store.writeBatch(stale,
                com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofPersons(
                        List.of(person("P9", "D1")))))
                .isInstanceOf(DirectoryLeaseLostException.class);

        // publish 幂等：已 PUBLISHED 同代 → no-op 成功（返回同值）
        GenerationHandle published = new GenerationHandle(SCOPE, publication.generationId(),
                "token-irrelevant-for-idempotent-republish");
        // 本替身：PUBLISHED 分支先于联合校验（与 W3 设计 §4 一致）
        DirectoryPublication again = store.publish(published);
        assertThat(again).isEqualTo(publication);
        assertThat(store.committedSeq(SCOPE)).isEqualTo(1);
    }

    @Test
    void statePreconditions_rejectInvalidTransitions() {
        GenerationHandle handle = store.beginGeneration(SCOPE);

        // BUILDING 期直接发布 → 状态前置拒绝
        assertThatThrownBy(() -> store.publish(handle))
                .isInstanceOf(DirectoryInvalidGenerationStateException.class)
                .satisfies(ex -> assertThat(((DirectoryInvalidGenerationStateException) ex).actual())
                        .isEqualTo(DirectoryGeneration.State.BUILDING));

        // BUILDING 期 validate → 拒绝（须先 completePaging）
        assertThatThrownBy(() -> store.validate(handle))
                .isInstanceOf(DirectoryInvalidGenerationStateException.class);

        store.completePaging(handle);
        assertThat(store.stateOf(SCOPE, handle.generationId()))
                .isEqualTo(DirectoryGeneration.State.VALIDATING);

        // VALIDATING 追加批次 → 拒绝（矩阵 12 同族）
        assertThatThrownBy(() -> store.writeBatch(handle,
                com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofDepartments(
                        List.of(root("D9")))))
                .isInstanceOf(DirectoryInvalidGenerationStateException.class);

        store.validate(handle);
        store.publish(handle);

        // 终态代再废弃 → 拒绝。表现形态：发布事务清理租约（lease_token=NULL）后
        // 联合校验以 token 失配拒绝（真实 PG 同形：UPDATE ... WHERE lease_token=?
        // AND state IN (非终态) 影响 0 行）；状态守卫是同事务内的防御层
        assertThatThrownBy(() -> store.abandon(handle, "late"))
                .isInstanceOf(DirectoryLeaseLostException.class);
    }

    // ------------------------------------------------------------ 接管与迟到操作（§6.1）

    @Test
    void takeoverAfterExpiry_abandonsStaleGenerationAndLateOperationsRejected() {
        GenerationHandle holderA = store.beginGeneration(SCOPE);

        // 租约过期（无人操作，时钟推进）
        now.set(now.get().plus(Duration.ofMinutes(11)));

        // B 重新 acquire：走接管——旧非终态代 ABANDONED('lease-takeover') + 新代新 token
        GenerationHandle holderB = store.beginGeneration(SCOPE);
        assertThat(store.stateOf(SCOPE, holderA.generationId()))
                .isEqualTo(DirectoryGeneration.State.ABANDONED);
        assertThat(store.abandonReasonOf(SCOPE, holderA.generationId())).isEqualTo("lease-takeover");

        // 迟到操作表：A 的 writeBatch/publish/abandon 全拒，B 新代零影响
        assertThatThrownBy(() -> store.writeBatch(holderA,
                com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofDepartments(
                        List.of(root("D1")))))
                .isInstanceOf(DirectoryLeaseLostException.class);
        assertThatThrownBy(() -> store.abandon(holderA, "late"))
                .isInstanceOf(DirectoryLeaseLostException.class);
        assertThat(store.stateOf(SCOPE, holderB.generationId()))
                .isEqualTo(DirectoryGeneration.State.BUILDING);
    }

    @Test
    void expiredLease_renewRejectedWithoutSilentExtension() {
        GenerationHandle handle = store.beginGeneration(SCOPE);
        now.set(now.get().plus(Duration.ofMinutes(11)));

        // 矩阵 13：过期续租被拒、不静默延期（须重新 acquire 走接管）
        assertThatThrownBy(() -> store.renewLease(handle))
                .isInstanceOf(DirectoryLeaseExpiredException.class);
        assertThat(store.generationIds(SCOPE)).hasSize(1);
        assertThat(store.stateOf(SCOPE, handle.generationId()))
                .isEqualTo(DirectoryGeneration.State.BUILDING);
    }

    @Test
    void staleGeneration_takeoverCoversAllNonTerminalStates() {
        // 矩阵 10：VALIDATED 崩溃后同样可被接管（覆盖所有非终态）
        GenerationHandle a = store.beginGeneration(SCOPE);
        store.completePaging(a);
        store.validate(a);
        assertThat(store.stateOf(SCOPE, a.generationId()))
                .isEqualTo(DirectoryGeneration.State.VALIDATED);

        now.set(now.get().plus(Duration.ofMinutes(11)));
        GenerationHandle b = store.beginGeneration(SCOPE);

        assertThat(store.stateOf(SCOPE, a.generationId()))
                .isEqualTo(DirectoryGeneration.State.ABANDONED);
        assertThat(store.activeGenerationId(SCOPE)).isNull();
        // 过期无人抢占时迟到 publish 仍被拒（矩阵 9：代保持 VALIDATED 只对未过期持有者成立；
        // 此处 A 已被 B 接管 → LeaseLost）
        assertThatThrownBy(() -> store.publish(a))
                .isInstanceOf(DirectoryLeaseLostException.class);
    }
}
