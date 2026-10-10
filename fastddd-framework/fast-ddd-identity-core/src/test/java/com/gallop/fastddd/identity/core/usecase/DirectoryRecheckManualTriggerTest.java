package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryChangeType;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryIncrementEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.LocalFactView;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-f f-2 M-17 核心编排四面验收（W5-f 设计 §5/§7.2；与
 * {@code DirectoryEventWorkerUseCaseTest} 同构面）：① 仅 pending_confirm 枚举面
 * （非 pending 行零回查）② 结论派生四分支（§7.3 推演表——ABSENT/DEACTIVATED/
 * CONTENT_DIFFERS/IDENTICAL）+ 证据形态（观察内容仅 CONTENT_DIFFERS 携带、
 * sourceEventId=null 管理端形态、基线零缺省 0）③ W5-e 门禁/通知接线同构
 * （DEACTIVATED→record〔manual 标记〕/权威恢复→clear、PERSON-only、stale 零
 * 接线、events 机械转发）④ 构造防线（gate 在场而 provider 空 fail-loud）。
 * 设计测试清单未列本类（R-2/R-8 之外）——按「每核心用例必有核心测试」仓库
 * 先例补齐，f-4 差异登记。
 */
class DirectoryRecheckManualTriggerTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-man", "app");

    // ---- ① 仅 pending_confirm 枚举面 ----

    @Test
    void onlyPendingConfirmRowsAreRechecked() {
        StubMirrorStore mirror = new StubMirrorStore();
        mirror.facts.add(pendingPerson("ou-p1"));
        mirror.facts.add(settledPerson("ou-p2"));
        RecordingCommitter committer = new RecordingCommitter();

        DirectoryRecheckManualTrigger.ManualRecheckReport report =
                newTrigger(mirror, new StubFactReader(), new StubRecheckQuery(),
                        new StubMatcher(), committer).runPendingRecheck(SCOPE);

        assertThat(mirror.enumerations).isEqualTo(1);
        assertThat(report.outcomes()).hasSize(1);
        assertThat(report.outcomes().get(0).externalId()).isEqualTo("ou-p1");
        assertThat(committer.evidences).hasSize(1); // 非 pending 行零回查
    }

    // ---- ② 结论派生 + 证据形态 ----

    @Test
    void identicalEvidenceShapeAndGateClearWithManualReason() {
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(3L, false, true, "{\"name\":\"张三\"}"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        RecordingGate gate = new RecordingGate();
        RecordingCommitter committer = new RecordingCommitter();

        newTrigger(new StubMirrorStore(List.of(pendingPerson("ou-p1"))), reader, recheck,
                new StubMatcher(), committer, gate, null, "feishu").runPendingRecheck(SCOPE);

        RecheckEvidence evidence = committer.evidences.get(0);
        assertThat(evidence.conclusion()).isEqualTo(RecheckConclusion.IDENTICAL);
        assertThat(evidence.expectedRevision()).isEqualTo(3L);
        assertThat(evidence.observedPerson()).isNull(); // 仅 CONTENT_DIFFERS 携带观察内容
        assertThat(evidence.observedDepartment()).isNull();
        assertThat(evidence.sourceEventId()).isNull(); // 管理端手动触发形态
        assertThat(gate.restorations).containsExactly("open_id|ou-p1|authority-recheck-restored:manual");
        assertThat(gate.records).isEmpty();
    }

    @Test
    void deactivatedPersonRecordsGateWithoutObservation() {
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), true));
        RecordingGate gate = new RecordingGate();
        RecordingCommitter committer = new RecordingCommitter();

        DirectoryRecheckManualTrigger.ManualRecheckReport report = newTrigger(
                new StubMirrorStore(List.of(pendingPerson("ou-p1"))), new StubFactReader(),
                recheck, new StubMatcher(), committer, gate, null, "feishu").runPendingRecheck(SCOPE);

        assertThat(report.outcomes().get(0).conclusion()).isEqualTo(RecheckConclusion.DEACTIVATED);
        RecheckEvidence evidence = committer.evidences.get(0);
        assertThat(evidence.observedPerson()).isNull(); // DEACTIVATED 不携带观察内容
        assertThat(gate.records).containsExactly("open_id|ou-p1|authority-recheck-deactivated:manual");
        assertThat(gate.restorations).isEmpty();
    }

    @Test
    void deactivatedDepartmentDoesNotTouchGate() {
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(department(), true));
        RecordingGate gate = new RecordingGate();

        DirectoryRecheckManualTrigger.ManualRecheckReport report = newTrigger(
                new StubMirrorStore(List.of(pendingDepartment("od-d1"))), new StubFactReader(),
                recheck, new StubMatcher(), new RecordingCommitter(), gate, null, "feishu")
                .runPendingRecheck(SCOPE);

        assertThat(report.outcomes().get(0).objectType()).isEqualTo(DirectoryObjectType.DEPARTMENT);
        assertThat(report.outcomes().get(0).conclusion()).isEqualTo(RecheckConclusion.DEACTIVATED);
        assertThat(gate.records).isEmpty(); // PERSON-only 裁决面
        assertThat(gate.restorations).isEmpty();
    }

    @Test
    void contentDiffersCarriesObservedPersonWithZeroBaseline() {
        StubRecheckQuery recheck = new StubRecheckQuery();
        DirectoryPerson observed = person();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(observed, false));
        RecordingCommitter committer = new RecordingCommitter();

        DirectoryRecheckManualTrigger.ManualRecheckReport report = newTrigger(
                new StubMirrorStore(List.of(pendingPerson("ou-p1"))), new StubFactReader(),
                recheck, new StubMatcher(), committer).runPendingRecheck(SCOPE);

        assertThat(report.outcomes().get(0).conclusion()).isEqualTo(RecheckConclusion.CONTENT_DIFFERS);
        RecheckEvidence evidence = committer.evidences.get(0);
        assertThat(evidence.observedPerson()).isEqualTo(observed); // 权威新值随证
        assertThat(evidence.expectedRevision()).isZero(); // 无本地行基线 0
    }

    @Test
    void absentConclusionWhenAuthorityConfirmsGone() {
        RecordingGate gate = new RecordingGate();

        DirectoryRecheckManualTrigger.ManualRecheckReport report = newTrigger(
                new StubMirrorStore(List.of(pendingPerson("ou-p1"))), new StubFactReader(),
                new StubRecheckQuery(), new StubMatcher(), new RecordingCommitter(),
                gate, null, "feishu").runPendingRecheck(SCOPE);

        assertThat(report.outcomes().get(0).conclusion()).isEqualTo(RecheckConclusion.ABSENT);
        assertThat(gate.records).isEmpty(); // ABSENT 不进门禁分支（worker 同构）
        assertThat(gate.restorations).isEmpty();
    }

    // ---- ③ W5-e 接线：stale 零接线 + events 机械转发 ----

    @Test
    void staleResultSkipsGateWiring() {
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(3L, false, true, "{\"name\":\"张三\"}"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        RecordingGate gate = new RecordingGate();
        RecordingCommitter committer = new RecordingCommitter();
        committer.staleReturns = true;

        DirectoryRecheckManualTrigger.ManualRecheckReport report = newTrigger(
                new StubMirrorStore(List.of(pendingPerson("ou-p1"))), reader, recheck,
                new StubMatcher(), committer, gate, null, "feishu").runPendingRecheck(SCOPE);

        assertThat(report.outcomes().get(0).stale()).isTrue();
        assertThat(gate.restorations).isEmpty(); // stale = 零写零接线（worker 同构）
    }

    @Test
    void eventsForwardedMechanically() {
        RecordingCommitter committer = new RecordingCommitter();
        committer.eventsReturns = List.of(new DirectoryIncrementEvent(
                "dir-change:tenant-man/app:2:PERSON_CHANGED", SCOPE, 2L,
                DirectoryChangeType.PERSON_CHANGED, List.of("ou-p1"), "cl-manual"));
        RecordingPublisher publisher = new RecordingPublisher();

        newTrigger(new StubMirrorStore(List.of(pendingPerson("ou-p1"))), new StubFactReader(),
                new StubRecheckQuery(), new StubMatcher(), committer, null, publisher, null)
                .runPendingRecheck(SCOPE);

        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.published.get(0).eventId())
                .isEqualTo("dir-change:tenant-man/app:2:PERSON_CHANGED");
    }

    // ---- ④ 构造防线 ----

    @Test
    void gatePresentWithoutProviderFailsConstruction() {
        assertThatThrownBy(() -> newTrigger(new StubMirrorStore(), new StubFactReader(),
                new StubRecheckQuery(), new StubMatcher(), new RecordingCommitter(),
                new RecordingGate(), null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection-provider");
    }

    // ------------------------------------------------------------- 夹具

    private static DirectoryRecheckManualTrigger newTrigger(StubMirrorStore mirror,
                                                            StubFactReader reader,
                                                            StubRecheckQuery recheck,
                                                            StubMatcher matcher,
                                                            RecordingCommitter committer) {
        return newTrigger(mirror, reader, recheck, matcher, committer, null, null, null);
    }

    private static DirectoryRecheckManualTrigger newTrigger(StubMirrorStore mirror,
                                                            StubFactReader reader,
                                                            StubRecheckQuery recheck,
                                                            StubMatcher matcher,
                                                            RecordingCommitter committer,
                                                            ExternalRejectionGate gate,
                                                            DirectoryIncrementPublisher publisher,
                                                            String provider) {
        return new DirectoryRecheckManualTrigger(mirror, reader, recheck, matcher, committer,
                passthroughRunner(), gate, publisher, provider);
    }

    private static IdentityTransactionRunner passthroughRunner() {
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
    }

    private static DirectoryPerson person() {
        return new DirectoryPerson("ou-p1", "张三", "San", List.of("od-d1"));
    }

    private static DirectoryDepartment department() {
        return new DirectoryDepartment("od-d1", null, "总部");
    }

    private static ProtectedFactView pendingPerson(String openId) {
        return new ProtectedFactView(ProtectedFactView.ObjectType.PERSON, openId, 3L, false,
                ProtectedFactView.Origin.RECHECK, "chg-1", true, "g1", 5L);
    }

    private static ProtectedFactView settledPerson(String openId) {
        return new ProtectedFactView(ProtectedFactView.ObjectType.PERSON, openId, 3L, false,
                ProtectedFactView.Origin.RECHECK, "chg-1", false, "g1", 5L);
    }

    private static ProtectedFactView pendingDepartment(String departmentId) {
        return new ProtectedFactView(ProtectedFactView.ObjectType.DEPARTMENT, departmentId, 2L,
                false, ProtectedFactView.Origin.RECHECK, "chg-2", true, "g1", 6L);
    }

    /** 镜像枚举桩：readProtectedFacts 定向返回（其余写面不在本用例路径）。 */
    static final class StubMirrorStore implements DirectoryMirrorStore {
        final List<ProtectedFactView> facts = new ArrayList<>();
        int enumerations;

        StubMirrorStore() {
        }

        StubMirrorStore(List<ProtectedFactView> initial) {
            facts.addAll(initial);
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            enumerations++;
            return List.copyOf(facts);
        }

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            throw new UnsupportedOperationException("手动回查不走写面");
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            throw new UnsupportedOperationException("手动回查不走观察面");
        }
    }

    /** 事实读取桩：单键基线定向。 */
    static final class StubFactReader implements DirectoryFactReader {
        Optional<LocalFactView> local = Optional.empty();

        @Override
        public Optional<LocalFactView> readFact(DirectoryScope scope, DirectoryObjectType objectType,
                                                String externalId) {
            return local;
        }
    }

    /** 回查桩：观测结果定向。 */
    static final class StubRecheckQuery implements DirectoryRecheckQuery {
        Optional<ObservedDirectoryObject> observed = Optional.empty();

        @Override
        public Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType,
                                                               String externalId) {
            return observed;
        }
    }

    /** 匹配桩：等值结论定向。 */
    static final class StubMatcher implements DirectoryFactMatcher {
        boolean matches = true;

        @Override
        public boolean matches(String storedFactJson, DirectoryPerson observed) {
            return matches;
        }

        @Override
        public boolean matches(String storedFactJson, DirectoryDepartment observed) {
            return matches;
        }
    }

    /** 提交器桩：记录证据，结果定向（缺省非 stale；events 供通知转发断言）。 */
    static final class RecordingCommitter implements DirectoryRecheckCommitter {
        final List<RecheckEvidence> evidences = new ArrayList<>();
        boolean staleReturns = false;
        List<DirectoryIncrementEvent> eventsReturns = List.of();

        @Override
        public RecheckResult commitCas(DirectoryScope scope, RecheckEvidence evidence) {
            evidences.add(evidence);
            return new RecheckResult(staleReturns, false, 2L, 2L, null, "cl-manual", eventsReturns);
        }
    }

    /** 门禁桩（W5-e 同构）：只记录 record/clear 调用。 */
    static final class RecordingGate implements ExternalRejectionGate {
        final List<String> records = new ArrayList<>();
        final List<String> restorations = new ArrayList<>();

        @Override
        public Optional<com.gallop.fastddd.identity.core.domain.RejectionRecord> findActive(
                com.gallop.fastddd.identity.core.domain.ExternalIdentity identity) {
            return Optional.empty();
        }

        @Override
        public Optional<com.gallop.fastddd.identity.core.domain.RejectionRecord> lockActive(
                com.gallop.fastddd.identity.core.domain.ExternalIdentity identity) {
            return Optional.empty();
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.RejectionRecord record(
                com.gallop.fastddd.identity.core.domain.ExternalIdentity identity, String reason) {
            records.add(identity.externalIdType() + "|" + identity.externalId() + "|" + reason);
            return null;
        }

        @Override
        public int clearControlled(com.gallop.fastddd.identity.core.domain.ExternalIdentity identity,
                                   String operator, String reason) {
            throw new UnsupportedOperationException("手动回查不走受控解除");
        }

        @Override
        public int clearByAuthoritativeRestoration(
                com.gallop.fastddd.identity.core.domain.ExternalIdentity identity, String evidenceRef) {
            restorations.add(identity.externalIdType() + "|" + identity.externalId() + "|" + evidenceRef);
            return 0;
        }
    }

    /** 增量通知桩（W5-e 同构）：机械转发记录面。 */
    static final class RecordingPublisher implements DirectoryIncrementPublisher {
        final List<DirectoryIncrementEvent> published = new ArrayList<>();

        @Override
        public void publishDirectoryChanged(DirectoryIncrementEvent event) {
            published.add(event);
        }
    }
}
