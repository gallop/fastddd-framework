package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.DirectoryChangeType;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryIncrementEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.LocalFactView;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.domain.RecheckTarget;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.domain.RejectionRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 增量事件 worker 用例单测（W5 设计 §7，W5-d）：结论派生四分支 + 墓碑复活 +
 * 无行 INSERT + 表 7-1⑥ 零墓碑两族 + stale CAS + 键级互斥 DEFERRED + 纵深
 * （unconsumed/resolve-failed/unexpected）+ 轮次计数。
 *
 * @author gallop
 * @since 1.1.0
 */
class DirectoryEventWorkerUseCaseTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-a", "app-x");

    private static final Duration LEASE = Duration.ofMinutes(5);

    // ---- 桩（记录面 + 供给面；断言只看记录） ----

    /** 收件箱桩：claim 按批供给（队列），记录 processed/failed 调用序。 */
    static final class RecordingInbox implements DirectoryEventInbox {
        final Queue<List<EventEnvelope>> claimBatches = new ArrayDeque<>();
        final List<String> processed = new ArrayList<>();
        final List<String> failedReasons = new ArrayList<>();
        int reclaimedReturns = 3;
        boolean commitCasSeen;

        void enqueue(EventEnvelope... envelopes) {
            claimBatches.add(List.of(envelopes));
        }

        @Override
        public boolean persist(EventEnvelope envelope) {
            throw new UnsupportedOperationException("worker 路径不走 persist");
        }

        @Override
        public List<EventEnvelope> claim(int limit, Duration lease) {
            return claimBatches.isEmpty() ? List.of() : claimBatches.poll();
        }

        @Override
        public int reclaimExpired() {
            return reclaimedReturns;
        }

        @Override
        public void markProcessed(String eventId) {
            processed.add(eventId);
        }

        @Override
        public void markFailed(String eventId, String reason) {
            failedReasons.add(eventId + "|" + reason);
        }
    }

    /** 通道桩：resolveObject 定向返回/抛错（receive 不在 worker 路径）。 */
    static final class StubChannel implements DirectoryEventChannel {
        Optional<RecheckTarget> resolved = Optional.empty();
        RuntimeException resolveFailure;

        @Override
        public com.gallop.fastddd.identity.core.domain.WebhookReceipt receive(
                com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest request) {
            throw new UnsupportedOperationException("worker 路径不走 receive");
        }

        @Override
        public Optional<RecheckTarget> resolveObject(String eventType, String payloadJson) {
            if (resolveFailure != null) {
                throw resolveFailure;
            }
            return resolved;
        }
    }

    /** 回查桩：观测结果定向；可挂 fetch 副作用（键级互斥嵌套场景）。 */
    static final class StubRecheckQuery implements DirectoryRecheckQuery {
        Optional<ObservedDirectoryObject> observed = Optional.empty();
        RuntimeException failure;
        Runnable onFetch;

        @Override
        public Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType,
                                                               String externalId) {
            if (onFetch != null) {
                onFetch.run();
            }
            if (failure != null) {
                throw failure;
            }
            return observed;
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
            return new RecheckResult(staleReturns, false, 1L, 1L, null, "cl-test", eventsReturns);
        }
    }

    /** 门禁桩（W5-e）：只记录 record/clear 调用（读面不走 worker 路径）。 */
    static final class RecordingGate implements ExternalRejectionGate {
        final List<String> records = new ArrayList<>();
        final List<String> restorations = new ArrayList<>();

        @Override
        public Optional<RejectionRecord> findActive(ExternalIdentity identity) {
            return Optional.empty();
        }

        @Override
        public Optional<RejectionRecord> lockActive(ExternalIdentity identity) {
            return Optional.empty();
        }

        @Override
        public RejectionRecord record(ExternalIdentity identity, String reason) {
            records.add(identity.externalIdType() + "|" + identity.externalId() + "|" + reason);
            return null;
        }

        @Override
        public int clearControlled(ExternalIdentity identity, String operator, String reason) {
            throw new UnsupportedOperationException("worker 路径不走受控解除");
        }

        @Override
        public int clearByAuthoritativeRestoration(ExternalIdentity identity, String evidenceRef) {
            restorations.add(identity.externalIdType() + "|" + identity.externalId() + "|" + evidenceRef);
            return 0;
        }
    }

    /** 增量通知桩（W5-e）：机械转发记录面。 */
    static final class RecordingPublisher implements DirectoryIncrementPublisher {
        final List<DirectoryIncrementEvent> published = new ArrayList<>();

        @Override
        public void publishDirectoryChanged(DirectoryIncrementEvent event) {
            published.add(event);
        }
    }

    private static DirectoryEventWorkerUseCase newUseCase(DirectoryEventInbox inbox, StubChannel channel,
                                                          StubRecheckQuery recheck, StubFactReader reader,
                                                          StubMatcher matcher, RecordingCommitter committer) {
        IdentityTransactionRunner passthrough = new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
        return new DirectoryEventWorkerUseCase(inbox, channel, recheck, reader, matcher,
                committer, passthrough, 20, LEASE);
    }

    private static EventEnvelope personEvent(String eventId) {
        return EventEnvelope.received(eventId, SCOPE, "contact.user.updated_v3",
                "{\"object\":{\"open_id\":\"ou_1\"}}", Instant.now());
    }

    private static DirectoryPerson person() {
        return new DirectoryPerson("ou_1", "张三", "San", List.of("od_0"));
    }

    // ---- 结论派生四分支 ----

    @Test
    void absentConclusionWhenAuthorityConfirmsGone() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.empty();
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(4L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-absent"));

        DirectoryEventWorkerUseCase.InboxRunResult result =
                newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(result.reclaimed()).isEqualTo(3);
        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(inbox.processed).containsExactly("e-absent");
        assertThat(inbox.failedReasons).isEmpty();
        assertThat(committer.evidences).hasSize(1);
        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.ABSENT);
        assertThat(committer.evidences.get(0).observedPerson()).isNull();
        assertThat(committer.evidences.get(0).expectedRevision()).isEqualTo(4L);
        assertThat(committer.evidences.get(0).sourceEventId()).isEqualTo("e-absent");
    }

    @Test
    void deactivatedBeatsContentComparison() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), true));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(4L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = false;
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-deact"));

        newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.DEACTIVATED);
        assertThat(inbox.processed).containsExactly("e-deact");
    }

    @Test
    void identicalConclusionZeroDiff() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(7L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = true;
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-same"));

        newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.IDENTICAL);
        assertThat(committer.evidences.get(0).observedPerson()).isNull();
        assertThat(committer.evidences.get(0).expectedRevision()).isEqualTo(7L);
    }

    @Test
    void contentDiffersOnMismatchCarriesObservation() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(7L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = false;
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-diff"));

        newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.CONTENT_DIFFERS);
        assertThat(committer.evidences.get(0).observedPerson()).isEqualTo(person());
        assertThat(committer.evidences.get(0).expectedRevision()).isEqualTo(7L);
    }

    @Test
    void noLocalRowMeansInsertPathWithRevisionZero() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.empty();
        StubMatcher matcher = new StubMatcher();
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-insert"));

        newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.CONTENT_DIFFERS);
        assertThat(committer.evidences.get(0).expectedRevision()).isZero();
    }

    @Test
    void tombstoneReviveIsContentDiffersEvenWhenMatcherAgrees() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(9L, true, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = true;
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-revive"));

        newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(committer.evidences.get(0).conclusion()).isEqualTo(RecheckConclusion.CONTENT_DIFFERS);
        assertThat(committer.evidences.get(0).observedPerson()).isEqualTo(person());
    }

    // ---- 表 7-1⑥：故障 ≠ 删除，零墓碑零事实 ----

    @Test
    void unavailableRecheckMarksFailedWithoutAnyCommit() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.failure = new DirectorySourceUnavailableException("timeout", "回查读取超时");
        StubFactReader reader = new StubFactReader();
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-down"));

        DirectoryEventWorkerUseCase.InboxRunResult result =
                newUseCase(inbox, channel, recheck, reader, new StubMatcher(), committer).runOnce();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(inbox.failedReasons).containsExactly("e-down|recheck-unavailable:timeout");
        assertThat(inbox.processed).isEmpty();
        assertThat(committer.evidences).isEmpty();
    }

    @Test
    void forbiddenRecheckMarksFailedWithoutAnyCommit() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.failure = new DirectorySourceForbiddenException("no-perm", "通讯录权限缺失");
        StubFactReader reader = new StubFactReader();
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-forbid"));

        newUseCase(inbox, channel, recheck, reader, new StubMatcher(), committer).runOnce();

        assertThat(inbox.failedReasons).containsExactly("e-forbid|recheck-forbidden:no-perm");
        assertThat(committer.evidences).isEmpty();
    }

    // ---- stale CAS 与键级互斥 ----

    @Test
    void staleCasCountsAsProcessedStaleWithoutRetry() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(7L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = true;
        RecordingCommitter committer = new RecordingCommitter();
        committer.staleReturns = true;
        inbox.enqueue(personEvent("e-stale"));

        DirectoryEventWorkerUseCase.InboxRunResult result =
                newUseCase(inbox, channel, recheck, reader, matcher, committer).runOnce();

        assertThat(result.processedStale()).isEqualTo(1);
        assertThat(result.processed()).isZero();
        assertThat(inbox.processed).containsExactly("e-stale");
        assertThat(inbox.failedReasons).isEmpty();
    }

    @Test
    void sameKeyConcurrentProcessingDefersInsteadOfFailing() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery outerRecheck = new StubRecheckQuery();
        outerRecheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        StubFactReader reader = new StubFactReader();
        reader.local = Optional.of(new LocalFactView(7L, false, false, "{\"x\":1}"));
        StubMatcher matcher = new StubMatcher();
        matcher.matches = true;
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-outer"));
        inbox.enqueue(personEvent("e-inner"));

        DirectoryEventWorkerUseCase useCase = newUseCase(inbox, channel, outerRecheck, reader, matcher, committer);
        // 键级互斥 = 用例实例级 ReentrantLock 锁表（生产 = 单例 bean 单锁表）。
        // 同线程递归会重入成功（ReentrantLock 语义），故以真异线程制造竞争：外层
        // 回查期间第二线程处理同键事件 → tryLock 失败 → DEFERRED（租约到期重投
        // 语义，不 markFailed——attempts 不推进）
        DirectoryEventWorkerUseCase.InboxRunResult[] nestedResult = new DirectoryEventWorkerUseCase.InboxRunResult[1];
        Thread contender = new Thread(() -> nestedResult[0] = useCase.runOnce());
        outerRecheck.onFetch = () -> {
            contender.start();
            try {
                contender.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        DirectoryEventWorkerUseCase.InboxRunResult outerResult = useCase.runOnce();

        assertThat(outerResult.processed()).isEqualTo(1);
        assertThat(outerResult.deferred()).isZero();
        assertThat(nestedResult[0].deferred()).isEqualTo(1);
        assertThat(nestedResult[0].failed()).isZero();
        assertThat(inbox.failedReasons).isEmpty();
        assertThat(inbox.processed).containsExactly("e-outer");
    }

    // ---- 纵深与意外 ----

    @Test
    void unconsumedTypeIsProcessedWithZeroRecheck() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.empty();
        StubRecheckQuery recheck = new StubRecheckQuery();
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-ignore"));

        DirectoryEventWorkerUseCase.InboxRunResult result =
                newUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(), committer).runOnce();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(inbox.processed).containsExactly("e-ignore");
        assertThat(committer.evidences).isEmpty();
    }

    @Test
    void resolveFailureMarksFailedWithClassTag() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolveFailure = new IllegalStateException("bad envelope");
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-resolve"));

        newUseCase(inbox, channel, new StubRecheckQuery(), new StubFactReader(),
                new StubMatcher(), committer).runOnce();

        assertThat(inbox.failedReasons).containsExactly("e-resolve|resolve-failed:IllegalStateException");
        assertThat(committer.evidences).isEmpty();
    }

    @Test
    void unexpectedRuntimeMarksFailedAndContinuesBatch() {
        RecordingInbox inbox = new RecordingInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.failure = new RuntimeException("bug");
        RecordingCommitter committer = new RecordingCommitter();
        inbox.enqueue(personEvent("e-bug"), personEvent("e-bug2"));

        DirectoryEventWorkerUseCase.InboxRunResult result =
                newUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(), committer).runOnce();

        assertThat(result.failed()).isEqualTo(2);
        assertThat(inbox.failedReasons).containsExactly(
                "e-bug|unexpected:RuntimeException", "e-bug2|unexpected:RuntimeException");
    }

    @Test
    void emptyBatchIsAnIdleRound() {
        RecordingInbox inbox = new RecordingInbox();
        inbox.reclaimedReturns = 0;

        DirectoryEventWorkerUseCase.InboxRunResult result = newUseCase(inbox, new StubChannel(),
                new StubRecheckQuery(), new StubFactReader(), new StubMatcher(),
                new RecordingCommitter()).runOnce();

        assertThat(result.reclaimed()).isZero();
        assertThat(result.claimed()).isZero();
        assertThat(result.processed()).isZero();
        assertThat(inbox.processed).isEmpty();
        assertThat(inbox.failedReasons).isEmpty();
    }

    // ---- W5-e：门禁接线（record/clear）+ 增量通知机械转发 ----

    /** W5-e 装配形态：门禁 + 通知 publisher + provider（"feishu"）全接线。 */
    private static DirectoryEventWorkerUseCase newWiredUseCase(RecordingInbox inbox, StubChannel channel,
                                                               StubRecheckQuery recheck, StubFactReader reader,
                                                               StubMatcher matcher, RecordingCommitter committer,
                                                               RecordingGate gate, RecordingPublisher publisher) {
        IdentityTransactionRunner passthrough = new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
        return new DirectoryEventWorkerUseCase(inbox, channel, recheck, reader, matcher,
                committer, passthrough, 20, LEASE, gate, publisher, "feishu");
    }

    @Test
    void deactivatedPersonRecordsRejectionOnOpenIdKey() {
        RecordingInbox inbox = new RecordingInbox();
        inbox.enqueue(personEvent("e-deact"));
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), true));
        RecordingGate gate = new RecordingGate();

        newWiredUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(),
                new RecordingCommitter(), gate, new RecordingPublisher()).runOnce();

        // open_id 域键（RecheckTarget 冻结键域；消费面 = readEffective 剔除 §8.1(c)）
        assertThat(gate.records).containsExactly(
                "open_id|ou_1|authority-recheck-deactivated:e-deact");
        assertThat(gate.restorations).isEmpty();
    }

    @Test
    void restoredPersonClearsRejectionForBothRestorationConclusions() {
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        RecordingGate gate = new RecordingGate();
        StubMatcher matcher = new StubMatcher();

        // IDENTICAL（结构等值）：在场且非停用 → 权威恢复清除（§7.5 清除规则②）
        RecordingInbox first = new RecordingInbox();
        first.enqueue(personEvent("e-ident"));
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        newWiredUseCase(first, channel, recheck, new StubFactReader(), matcher,
                new RecordingCommitter(), gate, new RecordingPublisher()).runOnce();

        // CONTENT_DIFFERS（内容不等，仍非停用）：同支清除
        matcher.matches = false;
        RecordingInbox second = new RecordingInbox();
        second.enqueue(personEvent("e-diff"));
        newWiredUseCase(second, channel, recheck, new StubFactReader(), matcher,
                new RecordingCommitter(), gate, new RecordingPublisher()).runOnce();

        assertThat(gate.restorations).containsExactly(
                "open_id|ou_1|authority-recheck-restored:e-ident",
                "open_id|ou_1|authority-recheck-restored:e-diff");
        assertThat(gate.records).isEmpty();
    }

    @Test
    void departmentDeactivationSkipsGateEntirely() {
        RecordingInbox inbox = new RecordingInbox();
        inbox.enqueue(EventEnvelope.received("e-dept-deact", SCOPE, "contact.department.deleted_v3",
                "{\"object\":{\"open_department_id\":\"od_1\"}}", Instant.now()));
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.DEPARTMENT, "od_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(
                new DirectoryDepartment("od_1", "od_0", "研发部"), true));
        RecordingGate gate = new RecordingGate();

        newWiredUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(),
                new RecordingCommitter(), gate, new RecordingPublisher()).runOnce();

        // PERSON-only 裁决：部门停用无登录拦截面，走通知不进门禁
        assertThat(gate.records).isEmpty();
        assertThat(gate.restorations).isEmpty();
    }

    @Test
    void staleCommitSkipsGateAndPublishes() {
        RecordingInbox inbox = new RecordingInbox();
        inbox.enqueue(personEvent("e-stale"));
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), true));
        RecordingCommitter committer = new RecordingCommitter();
        committer.staleReturns = true;
        RecordingGate gate = new RecordingGate();
        RecordingPublisher publisher = new RecordingPublisher();

        newWiredUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(),
                committer, gate, publisher).runOnce();

        // stale = 零写零推零接线（并发赢家已按其自身证据接线）
        assertThat(gate.records).isEmpty();
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void publisherReceivesCommittedEventsMechanically() {
        RecordingInbox inbox = new RecordingInbox();
        inbox.enqueue(personEvent("e-notify"));
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        RecordingCommitter committer = new RecordingCommitter();
        DirectoryIncrementEvent personChanged = new DirectoryIncrementEvent(
                DirectoryIncrementEvent.eventIdFor(SCOPE, 7L, DirectoryChangeType.PERSON_CHANGED),
                SCOPE, 7L, DirectoryChangeType.PERSON_CHANGED, List.of("ou_1"), "cl-7");
        committer.eventsReturns = List.of(personChanged);
        RecordingPublisher publisher = new RecordingPublisher();

        newWiredUseCase(inbox, channel, recheck, new StubFactReader(), new StubMatcher(),
                committer, new RecordingGate(), publisher).runOnce();

        // 机械转发：不重算不过滤，逐枚同序（预合并/stale 的 events 已空 = 零转发）
        assertThat(publisher.published).containsExactly(personChanged);
    }

    @Test
    void gateWithBlankProviderIsRejectedAtConstruction() {
        // fail-loud：增量链 ∧ 门禁在场 ∧ provider 未配 = 配置错误（starter 装配层
        // 终检外的第二道防线；实测形态 = IdentityDirectoryEventsHttpFlowTest 属性补齐）
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new DirectoryEventWorkerUseCase(new RecordingInbox(), new StubChannel(),
                                new StubRecheckQuery(), new StubFactReader(), new StubMatcher(),
                                new RecordingCommitter(), new IdentityTransactionRunner() {
                                    @Override
                                    public <T> T inTransaction(
                                            IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
                                        return callback.doInTransaction();
                                    }
                                }, 20, LEASE, new RecordingGate(), null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionProvider");
    }

    // ---- R-7（W5-f f-3）：断网 → 滞留 → 恢复 → reclaim → 最终一致 ----

    /**
     * R-7（W5-f f-3 §7.2；M-15 编排级 + F6 stub-channel 形态）：断网 → 滞留 →
     * 恢复 → reclaim → 最终一致零丢失零重复——有状态收件箱桩（RECEIVED 驻留 +
     * 失败回 RECEIVED 重试 + PROCESSING 租约过期回收，真实收件箱状态机子集）。
     * ① 断网：回查源不可用 → evt-1..3 failed（recheck-unavailable:timeout）
     * 回 RECEIVED 驻留、零提交（滞留期 = RECEIVED 驻留 + 下轮重试；退避节拍
     * 在源适配器/调度侧承载——W5-d 已测）；② 恢复：源可用 → 同 3 事件重试
     * 成功处理；③ 崩溃模拟：evt-4..6 被短租约认领后 worker「死亡」→ 租约
     * 过期 → 下轮 reclaimExpired 回 RECEIVED 再消费；④ 终态：6 事件各恰处理
     * 一次（零丢失零重复）、提交证据恰 6、失败记录恰 3（断网面独占）。
     */
    @Test
    void r7_outageThenStallThenRecoveryThenReclaimReachesConsistencyExactlyOnce() throws Exception {
        StatefulInbox inbox = new StatefulInbox();
        StubChannel channel = new StubChannel();
        channel.resolved = Optional.of(new RecheckTarget(DirectoryObjectType.PERSON, "ou_1"));
        StubRecheckQuery recheck = new StubRecheckQuery();
        recheck.failure = new DirectorySourceUnavailableException("timeout", "断网（测试桩）");
        RecordingCommitter committer = new RecordingCommitter();
        DirectoryEventWorkerUseCase worker = newUseCase(inbox, channel, recheck,
                new StubFactReader(), new StubMatcher(), committer);

        // ① 断网：evt-1..3 驻留 RECEIVED（failed 回驻留、零提交）
        inbox.receive(personEvent("evt-1"));
        inbox.receive(personEvent("evt-2"));
        inbox.receive(personEvent("evt-3"));
        DirectoryEventWorkerUseCase.InboxRunResult outage = worker.runOnce();
        assertThat(outage.failed()).isEqualTo(3);
        assertThat(inbox.failedReasons).hasSize(3)
                .allSatisfy(r -> r.endsWith("|recheck-unavailable:timeout"));
        assertThat(committer.evidences).isEmpty();
        assertThat(inbox.receivedCount()).isEqualTo(3);

        // ② 恢复：源可用 + 观测在场 → 驻留 3 事件重试成功
        recheck.failure = null;
        recheck.observed = Optional.of(ObservedDirectoryObject.of(person(), false));
        DirectoryEventWorkerUseCase.InboxRunResult recovered = worker.runOnce();
        assertThat(recovered.processed()).isEqualTo(3);
        assertThat(committer.evidences).hasSize(3);

        // ③ 崩溃模拟：evt-4..6 短租约认领后 worker「死亡」→ 过期 → reclaim 再消费
        inbox.receive(personEvent("evt-4"));
        inbox.receive(personEvent("evt-5"));
        inbox.receive(personEvent("evt-6"));
        assertThat(inbox.claim(20, Duration.ofMillis(20))).hasSize(3); // 死亡 worker 的在场认领
        Thread.sleep(50); // 租约过期（墙钟推进）
        DirectoryEventWorkerUseCase.InboxRunResult reclaimed = worker.runOnce();
        assertThat(reclaimed.reclaimed()).isEqualTo(3);
        assertThat(reclaimed.processed()).isEqualTo(3);

        // ④ 终态：恰一次（零丢失零重复）+ 提交证据恰 6 + 失败记录恰 3 + 零驻留
        assertThat(inbox.processedEvents).containsExactly(
                "evt-1", "evt-2", "evt-3", "evt-4", "evt-5", "evt-6");
        assertThat(inbox.processedEvents.stream().distinct().count()).isEqualTo(6);
        assertThat(committer.evidences).hasSize(6);
        assertThat(inbox.failedReasons).hasSize(3);
        assertThat(inbox.receivedCount()).isZero();
    }

    /**
     * 有状态收件箱桩（R-7）：RECEIVED 插入序消费 + failed 回 RECEIVED 重试
     * （真实面 attempts&lt;max 语义子集）+ PROCESSING 纳秒租约过期回收。
     */
    static final class StatefulInbox implements DirectoryEventInbox {
        final LinkedHashMap<String, EventEnvelope> envelopes = new LinkedHashMap<>();
        final Map<String, String> states = new HashMap<>();
        private final Map<String, Long> leaseExpiryNano = new HashMap<>();
        final List<String> processedEvents = new ArrayList<>();
        final List<String> failedReasons = new ArrayList<>();

        void receive(EventEnvelope envelope) {
            envelopes.put(envelope.eventId(), envelope);
            states.put(envelope.eventId(), "RECEIVED");
        }

        @Override
        public boolean persist(EventEnvelope envelope) {
            receive(envelope);
            return true;
        }

        @Override
        public List<EventEnvelope> claim(int limit, Duration lease) {
            List<EventEnvelope> taken = new ArrayList<>();
            for (String id : envelopes.keySet()) {
                if (taken.size() >= limit) {
                    break;
                }
                if ("RECEIVED".equals(states.get(id))) {
                    states.put(id, "PROCESSING");
                    leaseExpiryNano.put(id, System.nanoTime() + lease.toNanos());
                    taken.add(envelopes.get(id));
                }
            }
            return taken;
        }

        @Override
        public int reclaimExpired() {
            long now = System.nanoTime();
            int reclaimed = 0;
            for (String id : envelopes.keySet()) {
                if ("PROCESSING".equals(states.get(id))
                        && leaseExpiryNano.getOrDefault(id, Long.MAX_VALUE) <= now) {
                    states.put(id, "RECEIVED");
                    reclaimed++;
                }
            }
            return reclaimed;
        }

        @Override
        public void markProcessed(String eventId) {
            states.put(eventId, "PROCESSED");
            processedEvents.add(eventId);
        }

        @Override
        public void markFailed(String eventId, String reason) {
            failedReasons.add(eventId + "|" + reason);
            states.put(eventId, "RECEIVED"); // 重试语义（真实面 attempts<max 回 RECEIVED）
        }

        int receivedCount() {
            return (int) states.values().stream().filter("RECEIVED"::equals).count();
        }
    }
}
