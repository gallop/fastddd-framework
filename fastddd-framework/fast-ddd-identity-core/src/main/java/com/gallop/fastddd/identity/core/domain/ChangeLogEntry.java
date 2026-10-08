package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 不可变变更日志条目（W5 设计 §3.3/§4.1，W5-b 新增）：append-only 载体行——
 * 目录域每次产生水位推进的提交事务（代激活 / 增量落库 / 回查提交）各写一行。
 *
 * <p>不可变性 = 代码零 UPDATE 面 + 守护测试，不引入触发器（D-3）。{@code id} =
 * 调用方预铸 ULID（P2-5：先铸 id 后写事实，事实行 change_ref 与事件
 * changeLogRef 引用该值；{@code DirectoryChangeLog.append} 原样返回其为
 * changeLogRef）。{@code createdAt} 不入域形态——载体 DEFAULT now() 定值。
 * {@code summary} 为无 PII 的紧凑 JSON 文本（core 零依赖手工拼装）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ChangeLogEntry {

    /** 条目类别（V4 kind 列 CHECK 值域）。 */
    public enum Kind {
        /** 代激活（行携带 generationId；部分唯一约束锚）。 */
        GENERATION_ACTIVATED,
        /** 增量落库（generationId 为 null）。 */
        INCREMENT_APPLIED,
        /** 回查提交（generationId 为 null）。 */
        RECHECK_COMMITTED
    }

    private final String id;
    private final DirectoryScope scope;
    private final Kind kind;
    private final String generationId;
    private final long committedSeq;
    private final String summary;

    public ChangeLogEntry(String id,
                          DirectoryScope scope,
                          Kind kind,
                          String generationId,
                          long committedSeq,
                          String summary) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ChangeLogEntry.id 不能为空");
        }
        this.id = id;
        this.scope = Objects.requireNonNull(scope, "scope");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.generationId = generationId;
        this.committedSeq = committedSeq;
        this.summary = summary;
    }

    public String id() {
        return id;
    }

    public DirectoryScope scope() {
        return scope;
    }

    public Kind kind() {
        return kind;
    }

    /** 仅 GENERATION_ACTIVATED 行非 null（增量/回查行无代语义）。 */
    public String generationId() {
        return generationId;
    }

    public long committedSeq() {
        return committedSeq;
    }

    /** 无 PII 紧凑 JSON 文本（可 null）。 */
    public String summary() {
        return summary;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChangeLogEntry)) {
            return false;
        }
        ChangeLogEntry that = (ChangeLogEntry) o;
        return committedSeq == that.committedSeq
                && id.equals(that.id)
                && scope.equals(that.scope)
                && kind == that.kind
                && Objects.equals(generationId, that.generationId)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scope, kind, generationId, committedSeq, summary);
    }

    @Override
    public String toString() {
        return "ChangeLogEntry[" + id + ", kind=" + kind + ", committedSeq=" + committedSeq + "]";
    }
}
