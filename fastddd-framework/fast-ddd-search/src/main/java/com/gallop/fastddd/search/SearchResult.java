package com.gallop.fastddd.search;

import com.gallop.fastddd.common.result.PageResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 搜索结果，组合分页信息并扩展高亮和聚合。
 *
 * @author gallop
 */
@Data
public class SearchResult<T> {

    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;
    private boolean hasPrevious;
    private boolean hasNext;

    /** 高亮片段：fieldName → [snippet1, snippet2, ...]（仅 Meilisearch 后端） */
    private Map<String, List<String>> highlights;

    /** 聚合统计：fieldName → {value → count}（仅 Meilisearch 后端） */
    private Map<String, Map<String, Long>> facets;

    /** 实际使用的后端类型："sql" | "meilisearch" */
    private String backendType;

    /** SQL / 简化结果构造（无高亮/聚合） */
    public SearchResult(List<T> records, long total, int pageNum, int pageSize, String backendType) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        this.hasPrevious = pageNum > 1;
        this.hasNext = pageNum < pages;
        this.backendType = backendType;
    }

    /** 完整结果构造（含高亮/聚合） */
    public SearchResult(List<T> records, long total, int pageNum, int pageSize,
                        String backendType,
                        Map<String, List<String>> highlights,
                        Map<String, Map<String, Long>> facets) {
        this(records, total, pageNum, pageSize, backendType);
        this.highlights = highlights;
        this.facets = facets;
    }

    /** 转换为通用 PageResult，丢失高亮/聚合信息 */
    public PageResult<T> toPageResult() {
        return PageResult.of(pageNum, pageSize, total, records);
    }

    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
}
