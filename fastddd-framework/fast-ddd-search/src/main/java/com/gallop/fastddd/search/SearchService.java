package com.gallop.fastddd.search;

import java.util.List;

/**
 * 搜索引擎 SPI 接口（非泛型，避免 Spring 类型擦除问题）。
 * <p>
 * 运行时只有一个实现处于 active 状态：SQL 或 Meilisearch。
 *
 * @author gallop
 */
public interface SearchService {

    /** 执行搜索查询 */
    <T> SearchResult<T> search(SearchQuery query, Class<T> entityType);

    /** 索引单个文档（Meilisearch 后端有效；SQL 后端为 no-op） */
    <T> void index(String indexName, T entity);

    /** 批量索引文档 */
    <T> void indexBatch(String indexName, List<T> entities);

    /** 从索引中移除文档 */
    void removeFromIndex(String indexName, String id);

    /** 创建/更新索引结构 */
    void createIndex(String indexName, Class<?> entityType);

    /** 删除索引 */
    void dropIndex(String indexName);

    /** 返回后端标识: "sql" | "meilisearch" */
    String getBackendType();
}
