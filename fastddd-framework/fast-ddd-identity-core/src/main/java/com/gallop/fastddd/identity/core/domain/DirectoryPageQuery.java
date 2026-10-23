package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 分页查询（W3 设计 §2 DirectorySource 契约）。
 *
 * <p>{@code cursor == null} 表示首页；非首页游标来自上一页
 * {@link DirectoryPage#nextCursor()} 原样透传（不解释内容）。
 * {@code pageSize} 为期望页大小（正数），源侧可按自身上限收缩但不得放大。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryPageQuery {

    private final String cursor;
    private final int pageSize;

    public DirectoryPageQuery(String cursor, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须为正数（收到：" + pageSize + "）");
        }
        this.cursor = cursor;
        this.pageSize = pageSize;
    }

    /** 首页便捷工厂。 */
    public static DirectoryPageQuery firstPage(int pageSize) {
        return new DirectoryPageQuery(null, pageSize);
    }

    /** 游标（首页为 {@code null}）。 */
    public String cursor() {
        return cursor;
    }

    public int pageSize() {
        return pageSize;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DirectoryPageQuery
                && Objects.equals(cursor, ((DirectoryPageQuery) o).cursor)
                && pageSize == ((DirectoryPageQuery) o).pageSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cursor, pageSize);
    }

    @Override
    public String toString() {
        return "DirectoryPageQuery[cursor=" + cursor + ", pageSize=" + pageSize + "]";
    }
}
