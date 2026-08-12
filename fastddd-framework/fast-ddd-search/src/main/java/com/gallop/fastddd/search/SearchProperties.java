package com.gallop.fastddd.search;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索模块配置属性
 *
 * @author gallop
 */
@Data
@ConfigurationProperties(prefix = "fastddd.search")
public class SearchProperties {

    private boolean enabled = true;

    /** 搜索后端: "sql" | "meilisearch" | "disabled" */
    private String backend = "sql";

    private Sql sql = new Sql();

    private Meilisearch meilisearch = new Meilisearch();

    /** 按索引名定制配置 */
    private Map<String, IndexConfig> indexes = new HashMap<>();

    @Data
    public static class Sql {
        /** 全文检索策略: auto(检测方言) | always | never */
        private String fullTextEnabled = "auto";
    }

    @Data
    public static class Meilisearch {
        private String host = "http://localhost:7700";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(5);
        private String indexPrefix = "fastddd_";
    }

    @Data
    public static class IndexConfig {
        /** 可搜索字段 */
        private List<String> searchableFields = new ArrayList<>();

        /** 可过滤字段 */
        private List<String> filterableFields = new ArrayList<>();

        /** 可排序字段 */
        private List<String> sortableFields = new ArrayList<>();
    }

    public boolean isMeilisearch() {
        return "meilisearch".equalsIgnoreCase(backend);
    }

    public boolean isSql() {
        return "sql".equalsIgnoreCase(backend);
    }

    public boolean isDisabled() {
        return "disabled".equalsIgnoreCase(backend);
    }
}
