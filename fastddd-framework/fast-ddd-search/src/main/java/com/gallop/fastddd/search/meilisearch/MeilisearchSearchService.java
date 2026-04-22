package com.gallop.fastddd.search.meilisearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.common.util.JsonUtils;
import com.gallop.fastddd.search.SearchBackendException;
import com.gallop.fastddd.search.SearchIndexRegistry;
import com.gallop.fastddd.search.SearchProperties;
import com.gallop.fastddd.search.SearchQuery;
import com.gallop.fastddd.search.SearchResult;
import com.gallop.fastddd.search.SearchService;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Meilisearch 后端搜索实现。
 * <p>
 * 使用 Jackson 序列化桥接（SDK 内部用 GSON，但我们传入原始 JSON 字符串）。
 *
 * @author gallop
 */
@Slf4j
public class MeilisearchSearchService implements SearchService {

    private final Client client;
    private final SearchIndexRegistry registry;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public MeilisearchSearchService(SearchIndexRegistry registry, SearchProperties properties) {
        this.registry = registry;
        this.properties = properties;
        this.objectMapper = JsonUtils.getObjectMapper();

        SearchProperties.Meilisearch config = properties.getMeilisearch();
        this.client = new Client(new Config(config.getHost(), config.getApiKey()));
        log.info("MeilisearchSearchService initialized: host={}, indexPrefix={}",
                config.getHost(), config.getIndexPrefix());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SearchResult<T> search(SearchQuery query, Class<T> entityType) {
        String indexName = resolveIndexName(entityType);
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;

        try {
            Index index = client.index(uid);

            var reqBuilder = SearchRequest.builder()
                    .q(query.getKeyword() != null ? query.getKeyword() : "");

            // Pagination
            reqBuilder.page(query.getPageNum());
            reqBuilder.hitsPerPage(query.getPageSize());

            // Searchable attributes
            if (query.getFields() != null && !query.getFields().isEmpty()) {
                reqBuilder.attributesToRetrieve(
                        query.getFields().toArray(new String[0]));
            }

            // Highlight
            if (query.isHighlight() && query.getFields() != null) {
                reqBuilder.attributesToHighlight(
                        query.getFields().toArray(new String[0]));
            }

            // Filters — convert to Meilisearch filter expressions
            if (query.getFilters() != null && !query.getFilters().isEmpty()) {
                List<String> filterExprs = buildFilterExpressions(query.getFilters());
                reqBuilder.filter(filterExprs.toArray(new String[0]));
            }

            // Sort
            if (query.getSorts() != null && !query.getSorts().isEmpty()) {
                String[] sortExprs = query.getSorts().stream()
                        .map(s -> s.getField() + ":" +
                                (s.getDirection() == SearchQuery.Direction.ASC ? "asc" : "desc"))
                        .toArray(String[]::new);
                reqBuilder.sort(sortExprs);
            }

            // Execute search
            Searchable result = index.search(reqBuilder.build());
            var hits = result.getHits();

            // Deserialize hits to target type
            List<T> records = hits.stream()
                    .map(hit -> objectMapper.convertValue(hit, entityType))
                    .collect(Collectors.toList());

            // Extract pagination info
            long total = 0;
            int pageNum = query.getPageNum();
            int pageSize = query.getPageSize();
            if (result instanceof SearchResultPaginated paginated) {
                total = paginated.getTotalHits();
                pageNum = paginated.getPage();
            } else if (result instanceof com.meilisearch.sdk.model.SearchResult sr) {
                total = sr.getEstimatedTotalHits();
            } else {
                total = hits.size();
            }

            // Extract highlights from _formatted field (V1: per-hit)
            Map<String, List<String>> highlights = null;
            if (query.isHighlight()) {
                highlights = extractHighlights(hits, query.getFields());
            }

            return new SearchResult<>(records, total, pageNum, pageSize,
                    getBackendType(), highlights, null);

        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch search failed for index " + uid + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during Meilisearch search for index {}: {}", uid, e.getMessage(), e);
            return new SearchResult<>(List.of(), 0, query.getPageNum(), query.getPageSize(), getBackendType());
        }
    }

    @Override
    public <T> void index(String indexName, T entity) {
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;
        try {
            String json = objectMapper.writeValueAsString(List.of(entity));
            TaskInfo task = client.index(uid).addDocuments(json);
            client.waitForTask(task.getTaskUid());
        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch index failed for " + uid + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Meilisearch index failed for {}: {}", uid, e.getMessage());
        }
    }

    @Override
    public <T> void indexBatch(String indexName, List<T> entities) {
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;
        try {
            String json = objectMapper.writeValueAsString(entities);
            TaskInfo task = client.index(uid).addDocuments(json);
            client.waitForTask(task.getTaskUid());
        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch batch index failed for " + uid + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Meilisearch batch index failed for {}: {}", uid, e.getMessage());
        }
    }

    @Override
    public void removeFromIndex(String indexName, String id) {
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;
        try {
            TaskInfo task = client.index(uid).deleteDocument(id);
            client.waitForTask(task.getTaskUid());
        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch remove failed for " + uid + "/" + id + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Meilisearch remove failed for {}/{}: {}", uid, id, e.getMessage());
        }
    }

    @Override
    public void createIndex(String indexName, Class<?> entityType) {
        registry.register(indexName, entityType);
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;

        try {
            TaskInfo createTask = client.createIndex(uid, "id");
            client.waitForTask(createTask.getTaskUid());

            SearchProperties.IndexConfig config = properties.getIndexes().get(indexName);
            if (config != null) {
                Settings settings = new Settings();
                if (!config.getSearchableFields().isEmpty()) {
                    settings.setSearchableAttributes(config.getSearchableFields().toArray(new String[0]));
                }
                if (!config.getFilterableFields().isEmpty()) {
                    settings.setFilterableAttributes(config.getFilterableFields().toArray(new String[0]));
                }
                if (!config.getSortableFields().isEmpty()) {
                    settings.setSortableAttributes(config.getSortableFields().toArray(new String[0]));
                }
                TaskInfo settingsTask = client.index(uid).updateSettings(settings);
                client.waitForTask(settingsTask.getTaskUid());
            }

            log.info("Meilisearch index created: {}", uid);
        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch createIndex failed for " + uid + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Meilisearch createIndex failed for {}: {}", uid, e.getMessage());
        }
    }

    @Override
    public void dropIndex(String indexName) {
        String uid = properties.getMeilisearch().getIndexPrefix() + indexName;
        try {
            TaskInfo task = client.deleteIndex(uid);
            client.waitForTask(task.getTaskUid());
            log.info("Meilisearch index dropped: {}", uid);
        } catch (MeilisearchException e) {
            throw new SearchBackendException(
                    "Meilisearch dropIndex failed for " + uid + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Meilisearch dropIndex failed for {}: {}", uid, e.getMessage());
        }
    }

    @Override
    public String getBackendType() {
        return "meilisearch";
    }

    // ---- helpers ----

    private String resolveIndexName(Class<?> entityType) {
        return registry.resolveIndexName(entityType)
                .orElseGet(() -> {
                    registry.register(entityType.getSimpleName().toLowerCase(), entityType);
                    return entityType.getSimpleName().toLowerCase();
                });
    }

    /**
     * 提取高亮片段。Meilisearch 在每个 hit 的 {@code _formatted} 子 Map 中返回高亮标记。
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> extractHighlights(
            ArrayList<HashMap<String, Object>> hits, List<String> fields) {
        if (hits.isEmpty() || fields == null || fields.isEmpty()) return null;

        Map<String, List<String>> result = new HashMap<>();
        for (String field : fields) {
            List<String> snippets = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                Object formatted = hit.get("_formatted");
                if (formatted instanceof Map<?, ?> fmtMap) {
                    Object value = fmtMap.get(field);
                    if (value != null) {
                        snippets.add(value.toString());
                    }
                }
            }
            if (!snippets.isEmpty()) {
                result.put(field, snippets);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private List<String> buildFilterExpressions(Map<String, SearchQuery.Filter> filters) {
        List<String> exprs = new ArrayList<>();
        filters.forEach((field, filter) -> {
            switch (filter.getOp()) {
                case EQ -> exprs.add(field + " = " + quote(filter.getValue()));
                case NE -> exprs.add(field + " != " + quote(filter.getValue()));
                case GT -> exprs.add(field + " > " + filter.getValue());
                case GTE -> exprs.add(field + " >= " + filter.getValue());
                case LT -> exprs.add(field + " < " + filter.getValue());
                case LTE -> exprs.add(field + " <= " + filter.getValue());
                case IN -> {
                    String values = ((List<?>) filter.getValue()).stream()
                            .map(this::quote)
                            .collect(Collectors.joining(", "));
                    exprs.add(field + " IN [" + values + "]");
                }
                case NOT_IN -> {
                    String values = ((List<?>) filter.getValue()).stream()
                            .map(this::quote)
                            .collect(Collectors.joining(", "));
                    exprs.add(field + " NOT IN [" + values + "]");
                }
                case IS_NULL -> exprs.add(field + " IS NULL");
                case NOT_NULL -> exprs.add(field + " IS NOT NULL");
                default -> {}
            }
        });
        return exprs;
    }

    /** 转义双引号和反斜杠，防止过滤表达式注入 */
    private String quote(Object value) {
        if (value instanceof String s) {
            String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }
        return String.valueOf(value);
    }
}
