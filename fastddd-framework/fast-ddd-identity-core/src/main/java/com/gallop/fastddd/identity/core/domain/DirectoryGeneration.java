package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 目录同步代（W3 设计 §4 状态机）：一代 = 一次全量扫描的不可变快照构建 +
 * 原子发布单元。构建代全程不可见（读取一律经发布元数据的活动代指针）。
 *
 * <ul>
 *   <li>{@link State#BUILDING}：分页写批中；writeBatch/completePaging 前置；</li>
 *   <li>{@link State#VALIDATING}：三流分页已结束，等待 validate；</li>
 *   <li>{@link State#VALIDATED}：校验通过；此后追加批次一律拒绝（矩阵 12），
 *       仅 publish 前置；</li>
 *   <li>{@link State#PUBLISHED}：唯一发布点，恰一次；同 handle 重复 publish
 *       = 幂等 no-op 成功；</li>
 *   <li>{@link State#ABANDONED}：终态，保留行排障；任何非终态（BUILDING/
 *       VALIDATING/VALIDATED）在租约失效后都可被接管废弃。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryGeneration {

    private final String generationId;
    private final DirectoryScope scope;
    private final State state;

    public DirectoryGeneration(String generationId, DirectoryScope scope, State state) {
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.state = Objects.requireNonNull(state, "state");
    }

    public String generationId() {
        return generationId;
    }

    public DirectoryScope scope() {
        return scope;
    }

    public State state() {
        return state;
    }

    /** 非终态 = 可被接管/废弃的状态集合（每 scope 至多一个非终态代，部分唯一索引承载）。 */
    public boolean isTerminal() {
        return state == State.PUBLISHED || state == State.ABANDONED;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DirectoryGeneration
                && generationId.equals(((DirectoryGeneration) o).generationId)
                && scope.equals(((DirectoryGeneration) o).scope)
                && state == ((DirectoryGeneration) o).state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(generationId, scope, state);
    }

    @Override
    public String toString() {
        return "DirectoryGeneration[" + generationId + "," + state + "]";
    }

    /** 代状态（转换前置见 W3 设计 §4 操作前置表；非法转换 = DirectoryInvalidGenerationState）。 */
    public enum State {
        BUILDING, VALIDATING, VALIDATED, PUBLISHED, ABANDONED
    }
}
