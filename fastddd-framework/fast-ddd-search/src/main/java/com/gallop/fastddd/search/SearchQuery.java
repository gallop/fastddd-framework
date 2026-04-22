package com.gallop.fastddd.search;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * 搜索查询参数，支持关键词搜索、过滤、排序、分页。
 *
 * @author gallop
 */
@Getter
@Builder
public class SearchQuery {

    /** 全文搜索关键词 */
    private final String keyword;

    /** 搜索目标字段（Java 属性名，自动转下划线） */
    @Singular("field")
    private final List<String> fields;

    /** 精确/范围过滤条件 */
    @Singular("filter")
    private final Map<String, Filter> filters;

    /** 排序规则 */
    @Singular("sort")
    private final List<Sort> sorts;

    /** 页码（从 1 开始） */
    @Builder.Default
    private final int pageNum = 1;

    /** 每页大小 */
    @Builder.Default
    private final int pageSize = 10;

    /** 是否启用高亮（仅 Meilisearch 后端有效） */
    @Builder.Default
    private final boolean highlight = false;

    /** 创建新的 SearchQuery，替换分页参数（不可变对象，返回新实例） */
    public SearchQuery withPage(int pageNum, int pageSize) {
        return SearchQuery.builder()
                .keyword(this.keyword)
                .fields(this.fields)
                .filters(this.filters)
                .sorts(this.sorts)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .highlight(this.highlight)
                .build();
    }

    // ---- inner types ----

    public enum FilterOp {
        EQ, NE, LIKE, GT, GTE, LT, LTE, IN, NOT_IN, IS_NULL, NOT_NULL
    }

    @Getter
    public static class Filter {
        private final FilterOp op;
        private final Object value;

        private Filter(FilterOp op, Object value) {
            this.op = op;
            this.value = value;
        }

        public static Filter eq(Object value) { return new Filter(FilterOp.EQ, value); }
        public static Filter ne(Object value) { return new Filter(FilterOp.NE, value); }
        public static Filter like(String pattern) { return new Filter(FilterOp.LIKE, pattern); }
        public static Filter gt(Object value) { return new Filter(FilterOp.GT, value); }
        public static Filter gte(Object value) { return new Filter(FilterOp.GTE, value); }
        public static Filter lt(Object value) { return new Filter(FilterOp.LT, value); }
        public static Filter lte(Object value) { return new Filter(FilterOp.LTE, value); }
        public static Filter in(List<?> values) { return new Filter(FilterOp.IN, values); }
        public static Filter notIn(List<?> values) { return new Filter(FilterOp.NOT_IN, values); }
        public static Filter isNull() { return new Filter(FilterOp.IS_NULL, null); }
        public static Filter notNull() { return new Filter(FilterOp.NOT_NULL, null); }
    }

    public enum Direction { ASC, DESC }

    @Getter
    public static class Sort {
        private final String field;
        private final Direction direction;

        private Sort(String field, Direction direction) {
            this.field = field;
            this.direction = direction;
        }

        public static Sort asc(String field) { return new Sort(field, Direction.ASC); }
        public static Sort desc(String field) { return new Sort(field, Direction.DESC); }
    }
}
