package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
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
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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

    /** 提交器桩：记录证据，结果定向（缺省非 stale）。 */
    static final class RecordingCommitter implements DirectoryRecheckCommitter {
        final List<RecheckEvidence> evidences = new ArrayList<>();
        boolean staleReturns = false;

        @Override
        public RecheckResult commitCas(DirectoryScope scope, RecheckEvidence evidence) {
            evidences.add(evidence);
            return new RecheckResult(staleReturns, false, 1L, 1L, null, "cl-test", List.of());
        }
    }

    private static DirectoryEventWorkerUseCase newUseCase(RecordingInbox inbox, StubChannel channel,
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
}
