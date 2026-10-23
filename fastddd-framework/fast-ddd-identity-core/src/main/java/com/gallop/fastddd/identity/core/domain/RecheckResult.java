package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 权威回查提交结果（W5 设计 §7，W5-b 新增）：{@code DirectoryRecheckCommitter.commitCas}
 * 的裁决返回。
 *
 * <p>{@code stale} = CAS 失败（expectedRevision 已前移——他方先提交）：零写零推，
 * 调用方以新基线重走回查；false 时按 {@link RecheckConclusion} 分支落库——
 * IDENTICAL 仅复位 pending_confirm，ABSENT/DEACTIVATED 写墓碑（后者加门禁链）。
 * 其余字段语义同 {@link IncrementResult}（{@code changeLogRef} 指向
 * RECHECK_COMMITTED 条目；{@code publishedEvents} = 本事务发布的完整事件列表，
 * W5-e 通知链接线——stale/预合并/IDENTICAL 复位为空）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class RecheckResult {

    private final boolean stale;
    private final boolean premerged;
    private final long committedSeqAfter;
    private final long appliedSeqAfter;
    private final Long premergedSeq;
    private final String changeLogRef;
    private final List<DirectoryIncrementEvent> publishedEvents;

    public RecheckResult(boolean stale,
                         boolean premerged,
                         long committedSeqAfter,
                         long appliedSeqAfter,
                         Long premergedSeq,
                         String changeLogRef,
                         List<DirectoryIncrementEvent> publishedEvents) {
        this.stale = stale;
        this.premerged = premerged;
        this.committedSeqAfter = committedSeqAfter;
        this.appliedSeqAfter = appliedSeqAfter;
        this.premergedSeq = premergedSeq;
        this.changeLogRef = changeLogRef;
        this.publishedEvents = publishedEvents == null ? List.of() : List.copyOf(publishedEvents);
    }

    public boolean stale() {
        return stale;
    }

    public boolean premerged() {
        return premerged;
    }

    public long committedSeqAfter() {
        return committedSeqAfter;
    }

    public long appliedSeqAfter() {
        return appliedSeqAfter;
    }

    /** 预合并停下位（仅 {@link #premerged()} 为 true 时非 null）。 */
    public Long premergedSeq() {
        return premergedSeq;
    }

    public String changeLogRef() {
        return changeLogRef;
    }

    public List<DirectoryIncrementEvent> publishedEvents() {
        return publishedEvents;
    }

    /** 派生只读视图：{@code publishedEvents().stream().map(eventId)}（W5-e 起 ids 不再独立存储）。 */
    public List<String> publishedEventIds() {
        return publishedEvents.stream().map(DirectoryIncrementEvent::eventId).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecheckResult)) {
            return false;
        }
        RecheckResult that = (RecheckResult) o;
        return stale == that.stale
                && premerged == that.premerged
                && committedSeqAfter == that.committedSeqAfter
                && appliedSeqAfter == that.appliedSeqAfter
                && Objects.equals(premergedSeq, that.premergedSeq)
                && Objects.equals(changeLogRef, that.changeLogRef)
                && publishedEvents.equals(that.publishedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stale, premerged, committedSeqAfter, appliedSeqAfter,
                premergedSeq, changeLogRef, publishedEvents);
    }

    @Override
    public String toString() {
        return "RecheckResult[stale=" + stale + ", premerged=" + premerged
                + ", committedSeqAfter=" + committedSeqAfter
                + ", appliedSeqAfter=" + appliedSeqAfter + "]";
    }
}
