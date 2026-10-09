package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 目录增量事件 worker 编排用例（W5 设计 §7，W5-d）：回收过期租约 → 批量
 * claim → 逐事件「触发回查 + local_revision CAS」→ markProcessed/markFailed。
 *
 * <p><b>生产路径裁决（W5-d 差异登记）</b>：飞书源无版本可比字段（W0-6 P-8：
 * 单查两侧均无 version/modified_time），一切事件（含删除事件）<b>一律触发
 * 回查</b>（§7.1/§7.2：删除事件经回查确证，事件体不直接写墓碑）——
 * {@code IncrementApplier} 无飞书生产调用方（保留供未来版本可比源 + 测试），
 * 本用例即 M2 事件消费主链。</p>
 *
 * <p><b>编排约束</b>：</p>
 * <ul>
 *   <li><b>三步事务模型（§7.3，W3A-01 同源纪律）</b>：① 事务外读本地事实
 *       （{@code DirectoryFactReader}）→ ② 事务外权威单查
 *       （{@code DirectoryRecheckQuery}，外部 GET 绝不入事务）→ 结论派生 →
 *       ③ {@code runner.inTransaction(commitCas)} 单短事务提交（CAS +
 *       变更日志 + 事件，W5-c 冻结承载）；</li>
 *   <li><b>stale CAS 处置（W5-d 裁决）</b>：他方已提交该对象更近事实
 *       （全量发布/另一回查先到）→ markProcessed——事件触发职责已完成，
 *       不重试不告警（计数面 PROCESSED_STALE 供观测）；</li>
 *   <li><b>不可用处置（表 7-1⑥）</b>：回查不可用 = 不确证任何结论 →
 *       markFailed(reason)（attempts 至上限转 FAILED 终态，管理面重放），
 *       <b>零墓碑零事实写入</b>；</li>
 *   <li><b>键级互斥（§7.1）</b>：同键（scope+类型+external_id）进程内
 *       {@code tryLock} 串行，抢锁失败 → DEFERRED（不 markProcessed——
 *       租约自然到期回收重投，at-least-once 代价计入 attempts）；跨实例
 *       由 commitCas 的 CAS 兜底。锁条目不回收（上界 = 目录对象数，有界）；</li>
 *   <li><b>非消费类型纵深</b>：接收链已忽略 unconsumedType，入箱后再遇
 *       （防御纵深）→ markProcessed + 计数，零事实；</li>
 *   <li><b>单事件故障不炸批</b>：resolveObject/结论派生的意外运行时异常 →
 *       markFailed + 继续（实现缺陷以 last_error 可观测，不静默）。</li>
 * </ul>
 *
 * <p><b>不持有任何调度</b>：轮询节奏归宿主调度器（starter Worker 调度，
 * 镜像 {@code DirectorySyncScheduler} 形态）；本用例无状态可重入。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryEventWorkerUseCase {

    /** 单事件处置结果（计数/观测面；结构化日志归宿主调度器）。 */
    public enum Outcome {
        /** 已处理（含 IDENTICAL 零写、ABSENT 无行零写、unconsumed 纵深）。 */
        PROCESSED,
        /** 已处理且 CAS stale（他方先提交——职责完成，零重试）。 */
        PROCESSED_STALE,
        /** 已 markFailed（不可用/意外异常——attempts 推进）。 */
        FAILED,
        /** 键级锁冲突让位（租约到期重投，不 markFailed）。 */
        DEFERRED
    }

    /** 单轮结果（claim 批粒度计数；观测与结构化日志输入）。 */
    public record InboxRunResult(int reclaimed, int claimed, int processed,
                                 int processedStale, int failed, int deferred) {
    }

    private final DirectoryEventInbox inbox;
    private final DirectoryEventChannel channel;
    private final DirectoryRecheckQuery recheckQuery;
    private final DirectoryFactReader factReader;
    private final DirectoryFactMatcher factMatcher;
    private final DirectoryRecheckCommitter committer;
    private final IdentityTransactionRunner runner;
    private final int batchSize;
    private final Duration claimLease;

    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    public DirectoryEventWorkerUseCase(DirectoryEventInbox inbox,
                                       DirectoryEventChannel channel,
                                       DirectoryRecheckQuery recheckQuery,
                                       DirectoryFactReader factReader,
                                       DirectoryFactMatcher factMatcher,
                                       DirectoryRecheckCommitter committer,
                                       IdentityTransactionRunner runner,
                                       int batchSize,
                                       Duration claimLease) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize 必须 ≥ 1（收到：" + batchSize + "）");
        }
        if (claimLease == null || claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("claimLease 必须为正时长（收到：" + claimLease + "）");
        }
        this.inbox = inbox;
        this.channel = channel;
        this.recheckQuery = recheckQuery;
        this.factReader = factReader;
        this.factMatcher = factMatcher;
        this.committer = committer;
        this.runner = runner;
        this.batchSize = batchSize;
        this.claimLease = claimLease;
    }

    /** 单轮：回收过期 → claim 批 → 逐事件处置。可重入、无隐性状态。 */
    public InboxRunResult runOnce() {
        int reclaimed = inbox.reclaimExpired();
        List<EventEnvelope> events = inbox.claim(batchSize, claimLease);
        int processed = 0;
        int processedStale = 0;
        int failed = 0;
        int deferred = 0;
        for (EventEnvelope envelope : events) {
            switch (processOne(envelope)) {
                case PROCESSED -> processed++;
                case PROCESSED_STALE -> processedStale++;
                case FAILED -> failed++;
                case DEFERRED -> deferred++;
                default -> throw new IllegalStateException("不可达分支");
            }
        }
        return new InboxRunResult(reclaimed, events.size(), processed, processedStale, failed, deferred);
    }

    private Outcome processOne(EventEnvelope envelope) {
        Optional<RecheckTarget> target;
        try {
            target = channel.resolveObject(envelope.eventType(), envelope.payload());
        } catch (RuntimeException e) {
            inbox.markFailed(envelope.eventId(), "resolve-failed:" + e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
        if (target.isEmpty()) {
            // 非消费类型纵深（接收链应已忽略）：职责完成，零事实
            inbox.markProcessed(envelope.eventId());
            return Outcome.PROCESSED;
        }
        RecheckTarget resolved = target.get();
        String key = envelope.scope() + ":" + resolved.objectType() + ":" + resolved.externalId();
        ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return Outcome.DEFERRED;
        }
        try {
            return recheckAndCommit(envelope, resolved);
        } catch (DirectorySourceUnavailableException e) {
            // 表 7-1⑥：故障 ≠ 删除——零墓碑零事实，attempts 推进
            inbox.markFailed(envelope.eventId(), "recheck-unavailable:" + e.reasonCode());
            return Outcome.FAILED;
        } catch (DirectorySourceForbiddenException e) {
            // 权限/配置族 fail-safe：不确证任何结论——零墓碑零事实，attempts 推进
            // 至 FAILED 终态（重试无价值但有界，last_error 保留原因码供管理面）
            inbox.markFailed(envelope.eventId(), "recheck-forbidden:" + e.reasonCode());
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            inbox.markFailed(envelope.eventId(), "unexpected:" + e.getClass().getSimpleName());
            return Outcome.FAILED;
        } finally {
            lock.unlock();
        }
    }

    private Outcome recheckAndCommit(EventEnvelope envelope, RecheckTarget target) {
        // ① 事务外：本地基线
        Optional<LocalFactView> local =
                factReader.readFact(envelope.scope(), target.objectType(), target.externalId());
        // ② 事务外：权威单查（不可用上抛由 processOne 捕获）
        Optional<ObservedDirectoryObject> observed =
                recheckQuery.fetchObserved(target.objectType(), target.externalId());
        RecheckConclusion conclusion = deriveConclusion(local, observed);
        DirectoryPerson person = conclusion == RecheckConclusion.CONTENT_DIFFERS
                && observed.get().person() != null ? observed.get().person() : null;
        DirectoryDepartment department = conclusion == RecheckConclusion.CONTENT_DIFFERS
                && observed.get().department() != null ? observed.get().department() : null;
        RecheckEvidence evidence = new RecheckEvidence(
                target.objectType(), target.externalId(), conclusion,
                local.map(LocalFactView::localRevision).orElse(0L),
                person, department, envelope.eventId());
        // ③ 单短事务：CAS 提交（stale = 零写零推）
        RecheckResult result =
                runner.inTransaction(() -> committer.commitCas(envelope.scope(), evidence));
        inbox.markProcessed(envelope.eventId());
        return result.stale() ? Outcome.PROCESSED_STALE : Outcome.PROCESSED;
    }

    /**
     * 结论派生（§7.3 推演表）：ABSENT（权威确证不存在）> DEACTIVATED（权威在场
     * 且停用）> CONTENT_DIFFERS（无本地行/本地墓碑复活/内容不等）> IDENTICAL
     * （结构等值——零 H，仅可能复位 pending）。
     */
    private RecheckConclusion deriveConclusion(Optional<LocalFactView> local,
                                               Optional<ObservedDirectoryObject> observed) {
        if (observed.isEmpty()) {
            return RecheckConclusion.ABSENT;
        }
        ObservedDirectoryObject present = observed.get();
        if (present.deactivated()) {
            return RecheckConclusion.DEACTIVATED;
        }
        if (local.isEmpty()) {
            // 无本地行 + 权威在场：确证存在形态 INSERT（W5-c committer rev=1 路径）
            return RecheckConclusion.CONTENT_DIFFERS;
        }
        LocalFactView baseline = local.get();
        if (baseline.tombstone()) {
            // 墓碑 + 权威在场：复活 = 内容替换（IDENTICAL 只复位 pending，不复活）
            return RecheckConclusion.CONTENT_DIFFERS;
        }
        boolean identical = present.person() != null
                ? factMatcher.matches(baseline.factJson(), present.person())
                : factMatcher.matches(baseline.factJson(), present.department());
        return identical ? RecheckConclusion.IDENTICAL : RecheckConclusion.CONTENT_DIFFERS;
    }
}
