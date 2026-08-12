package com.gallop.fastddd.search;

import com.gallop.fastddd.search.meilisearch.MeilisearchSearchService;
import com.gallop.fastddd.search.sql.SqlSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 搜索模块自动配置。
 * <p>
 * 根据 {@code fastddd.search.backend} 选择后端:
 * <ul>
 *   <li>"sql"（默认）— 基于 MyBatis Plus QueryWrapper，需要 fast-ddd-crud</li>
 *   <li>"meilisearch" — 基于 Meilisearch SDK，需要 meilisearch-java</li>
 * </ul>
 *
 * @author gallop
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(prefix = "fastddd.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FastDddSearchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SearchBackendConfigurationValidator searchBackendConfigurationValidator(
            SearchProperties properties, ApplicationContext applicationContext) {
        return new SearchBackendConfigurationValidator(properties, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchIndexRegistry searchIndexRegistry(ApplicationContext applicationContext) {
        return new SearchIndexRegistry(applicationContext);
    }

    // ---- SQL Backend ----

    @Configuration
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @ConditionalOnProperty(prefix = "fastddd.search", name = "backend", havingValue = "sql", matchIfMissing = true)
    static class SqlSearchConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(type = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
        public SearchService sqlSearchService(SearchIndexRegistry registry,
                                              SearchProperties properties,
                                              ApplicationContext applicationContext) {
            DataSource dataSource = applicationContext.getBeanProvider(DataSource.class).getIfAvailable();
            log.info("SQL search backend enabled: fullText={}", properties.getSql().getFullTextEnabled());
            return new SqlSearchService(registry, properties, dataSource);
        }
    }

    // ---- Meilisearch Backend ----

    @Configuration
    @ConditionalOnClass(name = "com.meilisearch.sdk.Client")
    @ConditionalOnProperty(prefix = "fastddd.search", name = "backend", havingValue = "meilisearch")
    static class MeilisearchSearchConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SearchService meilisearchSearchService(SearchIndexRegistry registry,
                                                      SearchProperties properties) {
            log.info("Meilisearch search backend enabled: host={}", properties.getMeilisearch().getHost());
            return new MeilisearchSearchService(registry, properties);
        }
    }
}
