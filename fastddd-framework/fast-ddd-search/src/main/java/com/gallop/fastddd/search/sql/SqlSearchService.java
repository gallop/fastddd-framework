package com.gallop.fastddd.search.sql;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.common.database.DatabaseDialect;
import com.gallop.fastddd.search.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL 后端搜索实现，基于 MyBatis Plus QueryWrapper。
 * <p>
 * 零额外依赖，使用 LIKE 子串匹配（或 MySQL FULLTEXT），直接查询数据库。
 *
 * @author gallop
 */
@Slf4j
public class SqlSearchService implements SearchService {

    private final SearchIndexRegistry registry;
    private final SearchProperties properties;
    private final QueryWrapperBuilder queryBuilder;
    private final @Nullable DataSource dataSource;
    private volatile @Nullable DatabaseDialect dialect;

    public SqlSearchService(SearchIndexRegistry registry,
                            SearchProperties properties,
                            @Nullable DataSource dataSource) {
        this.registry = registry;
        this.properties = properties;
        this.dataSource = dataSource;
        this.queryBuilder = new QueryWrapperBuilder();
        log.info("SqlSearchService initialized: fullText={}", properties.getSql().getFullTextEnabled());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SearchResult<T> search(SearchQuery query, Class<T> entityType) {
        // 自动注册索引
        String indexName = registry.resolveIndexName(entityType)
                .orElseGet(() -> {
                    registry.register(entityType.getSimpleName().toLowerCase(), entityType);
                    return entityType.getSimpleName().toLowerCase();
                });

        Object mapperObj = registry.resolveMapper(entityType);
        if (!(mapperObj instanceof BaseMapper<?>)) {
            throw new SearchBackendException("SQL search cannot resolve a BaseMapper for "
                    + entityType.getName() + ". Register the entity mapper before querying; "
                    + "this is a configuration error, not an empty search result.");
        }
        BaseMapper<Object> mapper = (BaseMapper<Object>) mapperObj;

        // 构建全文谓词（优先 FULLTEXT，降级到 LIKE）
        String fullTextPredicate = buildFullTextPredicate(query);

        // 统一通过 QueryWrapperBuilder 构建查询
        QueryWrapper<Object> wrapper = queryBuilder.build(query, fullTextPredicate);
        Page<Object> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Object> result = mapper.selectPage(page, wrapper);

        List<T> records = (List<T>) result.getRecords();
        return new SearchResult<>(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize(), getBackendType());
    }

    @Override
    public <T> void index(String indexName, T entity) {
        // SQL 后端无需索引操作
    }

    @Override
    public <T> void indexBatch(String indexName, List<T> entities) {
        // SQL 后端无需索引操作
    }

    @Override
    public void removeFromIndex(String indexName, String id) {
        // SQL 后端无需索引操作
    }

    @Override
    public void createIndex(String indexName, Class<?> entityType) {
        registry.register(indexName, entityType);
    }

    @Override
    public void dropIndex(String indexName) {
        // SQL 后端无需索引操作
    }

    @Override
    public String getBackendType() {
        return "sql";
    }

    /**
     * 根据配置和方言，决定是否构建全文检索谓词。
     * 返回 null 表示使用 LIKE 降级（由 QueryWrapperBuilder 处理）。
     */
    @Nullable
    private String buildFullTextPredicate(SearchQuery query) {
        String mode = properties.getSql().getFullTextEnabled();
        if ("never".equals(mode)) return null;
        if (query.getKeyword() == null || query.getFields() == null || query.getFields().isEmpty()) return null;

        DatabaseDialect dialect = getDialect();
        if (dialect == null || !dialect.supportsFullTextSearch()) {
            if ("always".equals(mode)) {
                log.warn("full-text-enabled=always but dialect {} does not support FULLTEXT, falling back to LIKE",
                        dialect != null ? dialect.getDbType() : "unknown");
            }
            return null;
        }

        // 字段名安全校验：仅允许字母数字下划线
        String[] cols = new String[query.getFields().size()];
        for (int i = 0; i < query.getFields().size(); i++) {
            String col = QueryWrapperBuilder.toColumn(query.getFields().get(i));
            if (!col.matches("^[a-zA-Z0-9_]+$")) {
                log.warn("Invalid full-text column name '{}', skipping FULLTEXT", col);
                return null;
            }
            cols[i] = col;
        }

        // 使用参数化占位符避免 SQL 注入
        return dialect.getFullTextSearchExpression(cols, "{0}");
    }

    private DatabaseDialect getDialect() {
        if (dialect == null && dataSource != null) {
            dialect = DatabaseDialect.detect(dataSource);
        }
        return dialect;
    }
}
