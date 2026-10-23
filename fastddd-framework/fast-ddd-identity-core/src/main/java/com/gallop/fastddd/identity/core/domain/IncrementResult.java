package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 增量落库结果（W5 设计 §5.2/表 6-1，W5-b 新增）：{@code DirectoryIncrementApplier.apply}
 * 在提交事务内返回的落库结论。
 *
 * <p>字段语义（表 6-1 交错推演的落点）：{@code changed} = 是否产生有效变更
 * （false → 零写零推零水位变化，含空批与重复投递）；{@code premerged} = 是否
 * 走预合并分支（触发条件 = 锁 metadata 时存在非终态构建代，§6.2 :314；推
 * committed_seq 不推 applied_seq，该分支不发增量事件——"尾部过大"系差异登记
 * D8 的裁定措辞"无操作数、不继承"、非触发条件）；{@code changeLogRef} = INCREMENT_APPLIED 载体条目 id
 * （预铸 ULID，P2-5）；{@code publishedEvents} = 本事务发布的完整
 * {@link DirectoryIncrementEvent} 列表（混型批按 type 分组多枚；预合并/无效变更
 * 为空）——W5-e 通知链接线：调用方（用例层）将其机械转发
 * {@code DirectoryIncrementPublisher.publishDirectoryChanged}（同事务），
 * 不重算、不过滤。{@link #publishedEventIds()} 为派生只读视图。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IncrementResult {

    private final boolean changed;
    private final boolean premerged;
    private final long committedSeqAfter;
    private final long appliedSeqAfter;
    private final Long premergedSeq;
    private final String changeLogRef;
    private final List<DirectoryIncrementEvent> publishedEvents;

    public IncrementResult(boolean changed,
                           boolean premerged,
                           long committedSeqAfter,
                           long appliedSeqAfter,
                           Long premergedSeq,
                           String changeLogRef,
                           List<DirectoryIncrementEvent> publishedEvents) {
        this.changed = changed;
        this.premerged = premerged;
        this.committedSeqAfter = committedSeqAfter;
        this.appliedSeqAfter = appliedSeqAfter;
        this.premergedSeq = premergedSeq;
        this.changeLogRef = changeLogRef;
        this.publishedEvents = publishedEvents == null ? List.of() : List.copyOf(publishedEvents);
    }

    public boolean changed() {
        return changed;
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
        if (!(o instanceof IncrementResult)) {
            return false;
        }
        IncrementResult that = (IncrementResult) o;
        return changed == that.changed
                && premerged == that.premerged
                && committedSeqAfter == that.committedSeqAfter
                && appliedSeqAfter == that.appliedSeqAfter
                && Objects.equals(premergedSeq, that.premergedSeq)
                && Objects.equals(changeLogRef, that.changeLogRef)
                && publishedEvents.equals(that.publishedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changed, premerged, committedSeqAfter, appliedSeqAfter,
                premergedSeq, changeLogRef, publishedEvents);
    }

    @Override
    public String toString() {
        return "IncrementResult[changed=" + changed + ", premerged=" + premerged
                + ", committedSeqAfter=" + committedSeqAfter
                + ", appliedSeqAfter=" + appliedSeqAfter + "]";
    }
}
