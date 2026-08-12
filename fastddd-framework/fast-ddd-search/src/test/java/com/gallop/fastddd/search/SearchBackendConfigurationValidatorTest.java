package com.gallop.fastddd.search;

import com.gallop.fastddd.search.sql.SqlSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.StaticApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEARCH-001：配置故障必须与真实零结果可区分。 */
class SearchBackendConfigurationValidatorTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddSearchAutoConfiguration.class));

    @Test
    void sqlModeFailsFastWhenMyBatisPlusIsAbsent() {
        runner.withClassLoader(new FilteredClassLoader("com.baomidou.mybatisplus"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("fastddd.search.backend=sql")
                            .hasMessageContaining("BaseMapper");
                });
    }

    @Test
    void disabledModeStartsWithoutSearchService() {
        runner.withClassLoader(new FilteredClassLoader("com.baomidou.mybatisplus"))
                .withPropertyValues("fastddd.search.backend=disabled")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SearchService.class);
                });
    }

    @Test
    void meilisearchModeFailsFastWhenSdkIsAbsent() {
        runner.withClassLoader(new FilteredClassLoader("com.meilisearch.sdk"))
                .withPropertyValues("fastddd.search.backend=meilisearch")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("meilisearch-java");
                });
    }

    @Test
    void rejectsUnknownBackendInsteadOfTreatingItAsSql() {
        runner.withPropertyValues("fastddd.search.backend=elasticsearch")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("Unsupported fastddd.search.backend")
                            .hasMessageContaining("sql, meilisearch, disabled");
                });
    }

    @Test
    void sqlMapperResolutionFailureIsNotReportedAsEmptyResult() {
        SearchProperties properties = new SearchProperties();
        SearchIndexRegistry registry = new SearchIndexRegistry(new StaticApplicationContext());
        SqlSearchService service = new SqlSearchService(registry, properties, null);

        assertThatThrownBy(() -> service.search(SearchQuery.builder().keyword("missing").build(), Object.class))
                .isInstanceOf(SearchBackendException.class)
                .hasMessageContaining("cannot resolve a BaseMapper");
    }
}
