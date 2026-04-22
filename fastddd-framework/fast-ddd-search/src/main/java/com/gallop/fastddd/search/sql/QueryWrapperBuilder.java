package com.gallop.fastddd.search.sql;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gallop.fastddd.search.SearchQuery;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 将 {@link SearchQuery} 转换为 MyBatis Plus {@link QueryWrapper}。
 *
 * @author gallop
 */
class QueryWrapperBuilder {

    <T> QueryWrapper<T> build(SearchQuery query, @Nullable String fullTextPredicate) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        applyKeyword(wrapper, query, fullTextPredicate);
        applyFilters(wrapper, query);
        applySorts(wrapper, query);

        return wrapper;
    }

    private <T> void applyKeyword(QueryWrapper<T> wrapper, SearchQuery query,
                                   @Nullable String fullTextPredicate) {
        String keyword = query.getKeyword();
        List<String> fields = query.getFields();
        if (keyword == null || keyword.isBlank() || fields == null || fields.isEmpty()) {
            return;
        }

        if (fullTextPredicate != null) {
            // 参数化全文检索，防止 SQL 注入
            wrapper.apply(fullTextPredicate, keyword);
        } else {
            // LIKE 降级：多字段 OR
            wrapper.and(w -> {
                for (int i = 0; i < fields.size(); i++) {
                    String column = toColumn(fields.get(i));
                    if (i == 0) {
                        w.like(column, keyword);
                    } else {
                        w.or().like(column, keyword);
                    }
                }
            });
        }
    }

    private <T> void applyFilters(QueryWrapper<T> wrapper, SearchQuery query) {
        if (query.getFilters() == null) return;

        query.getFilters().forEach((field, filter) -> {
            String column = toColumn(field);
            switch (filter.getOp()) {
                case EQ -> wrapper.eq(column, filter.getValue());
                case NE -> wrapper.ne(column, filter.getValue());
                case LIKE -> wrapper.like(column, filter.getValue());
                case GT -> wrapper.gt(column, filter.getValue());
                case GTE -> wrapper.ge(column, filter.getValue());
                case LT -> wrapper.lt(column, filter.getValue());
                case LTE -> wrapper.le(column, filter.getValue());
                case IN -> wrapper.in(column, (List<?>) filter.getValue());
                case NOT_IN -> wrapper.notIn(column, (List<?>) filter.getValue());
                case IS_NULL -> wrapper.isNull(column);
                case NOT_NULL -> wrapper.isNotNull(column);
            }
        });
    }

    private <T> void applySorts(QueryWrapper<T> wrapper, SearchQuery query) {
        if (query.getSorts() == null || query.getSorts().isEmpty()) return;

        for (SearchQuery.Sort sort : query.getSorts()) {
            String column = toColumn(sort.getField());
            if (sort.getDirection() == SearchQuery.Direction.ASC) {
                wrapper.orderByAsc(column);
            } else {
                wrapper.orderByDesc(column);
            }
        }
    }

    /** Java camelCase field → database snake_case column */
    static String toColumn(String field) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
