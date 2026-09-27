package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 一页目录结果（W3 设计 §2 / §6.5 分页契约）。
 *
 * <p><b>一致性契约（源实现必须满足，编排用例逐页校验）</b>：</p>
 * <ul>
 *   <li>{@code hasMore == true} ⇒ {@code nextCursor != null} 且与进入本页的游标不同
 *       （游标不前进即视为分页异常，防无限页）；</li>
 *   <li>{@code hasMore == false} ⇒ 分页正常终止（终页），{@code nextCursor} 应为
 *       {@code null}；终页允许空 items（空目录/空部门是合法结果，不是错误）；</li>
 *   <li>违反上述形态（或 items 含 null 元素）= 契约违反，按
 *       {@code DirectoryContractViolationException} 整代废弃，不重试。</li>
 * </ul>
 *
 * @param <T> {@link DirectoryDepartment} 或 {@link DirectoryPerson}
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryPage<T> {

    private final List<T> items;
    private final String nextCursor;
    private final boolean hasMore;

    public DirectoryPage(List<T> items, String nextCursor, boolean hasMore) {
        this.items = List.copyOf(items);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<T> items() {
        return items;
    }

    /** 下一页游标（{@code hasMore == false} 时应为 {@code null}）。 */
    public String nextCursor() {
        return nextCursor;
    }

    public boolean hasMore() {
        return hasMore;
    }

    /** 终页便捷工厂（hasMore=false、无游标）。 */
    public static <T> DirectoryPage<T> last(List<T> items) {
        return new DirectoryPage<>(items, null, false);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DirectoryPage)) {
            return false;
        }
        DirectoryPage<?> that = (DirectoryPage<?>) o;
        return hasMore == that.hasMore
                && Objects.equals(nextCursor, that.nextCursor)
                && items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, nextCursor, hasMore);
    }

    @Override
    public String toString() {
        return "DirectoryPage[" + items.size() + " items, hasMore=" + hasMore + "]";
    }
}
