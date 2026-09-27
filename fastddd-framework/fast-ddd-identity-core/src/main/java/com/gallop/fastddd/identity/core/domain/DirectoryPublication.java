package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 发布结果（W3 设计 §5.2 T7 / §6.2 水位模型）：观察快照切换 + 新增受保护事实
 * 落库（M1 发布的准确表述，不称"已完成事实合并"）。
 *
 * <p>水位语义（V2-03 冻结口径）：本事务有事实 INSERT ⇒ H_after = H_before + 1
 * （一次 T7 = 一次事实提交，同事务新写事实共享同一序值）；无 ⇒ H_after = H_before
 * （空差异发布不推进事实序号，仅 generation 前进）。appliedSeq = H_after。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryPublication {

    private final String generationId;
    private final long committedSeqBefore;
    private final long committedSeqAfter;
    private final long appliedSeq;
    private final int factsInserted;

    public DirectoryPublication(String generationId,
                                long committedSeqBefore,
                                long committedSeqAfter,
                                long appliedSeq,
                                int factsInserted) {
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.committedSeqBefore = committedSeqBefore;
        this.committedSeqAfter = committedSeqAfter;
        this.appliedSeq = appliedSeq;
        this.factsInserted = factsInserted;
    }

    public String generationId() {
        return generationId;
    }

    /** H_before（进入发布事务时的 committed_seq）。 */
    public long committedSeqBefore() {
        return committedSeqBefore;
    }

    /** H_after（提交后 committed_seq；无事实写入时与 before 相同）。 */
    public long committedSeqAfter() {
        return committedSeqAfter;
    }

    /** 最终已应用水位（= H_after；无变更发布 = 重写同值）。 */
    public long appliedSeq() {
        return appliedSeq;
    }

    /** 本事务新增对象键数（规则 A；已有键零修改——规则 B/C/D/E 均零修改）。 */
    public int factsInserted() {
        return factsInserted;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DirectoryPublication)) {
            return false;
        }
        DirectoryPublication that = (DirectoryPublication) o;
        return generationId.equals(that.generationId)
                && committedSeqBefore == that.committedSeqBefore
                && committedSeqAfter == that.committedSeqAfter
                && appliedSeq == that.appliedSeq
                && factsInserted == that.factsInserted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(generationId, committedSeqBefore, committedSeqAfter,
                appliedSeq, factsInserted);
    }

    @Override
    public String toString() {
        return "DirectoryPublication[gen=" + generationId + ", committedSeq "
                + committedSeqBefore + "->" + committedSeqAfter
                + ", appliedSeq=" + appliedSeq + ", factsInserted=" + factsInserted + "]";
    }
}
