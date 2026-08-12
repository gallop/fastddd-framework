package com.gallop.fastddd.search;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.ClassUtils;

import java.util.Locale;
import java.util.Set;

/**
 * SEARCH-001：将错误的后端配置转化为启动期的可操作诊断，禁止静默降级为“没有结果”。
 */
final class SearchBackendConfigurationValidator {

    private static final Set<String> SUPPORTED_BACKENDS = Set.of("sql", "meilisearch", "disabled");
    private static final String BASE_MAPPER = "com.baomidou.mybatisplus.core.mapper.BaseMapper";
    private static final String MEILISEARCH_CLIENT = "com.meilisearch.sdk.Client";

    SearchBackendConfigurationValidator(SearchProperties properties, ListableBeanFactory beanFactory) {
        String backend = normalize(properties.getBackend());
        if (!SUPPORTED_BACKENDS.contains(backend)) {
            throw new IllegalStateException("Unsupported fastddd.search.backend='" + properties.getBackend()
                    + "'. Supported values are sql, meilisearch, disabled.");
        }
        if ("sql".equals(backend)) {
            validateSqlBackend(beanFactory);
        } else if ("meilisearch".equals(backend)
                && !ClassUtils.isPresent(MEILISEARCH_CLIENT, Thread.currentThread().getContextClassLoader())) {
            throw new IllegalStateException("fastddd.search.backend=meilisearch requires the optional "
                    + "dependency com.meilisearch.sdk:meilisearch-java on the runtime classpath. "
                    + "Add the dependency or set fastddd.search.backend=disabled.");
        }
    }

    private static void validateSqlBackend(ListableBeanFactory beanFactory) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (!ClassUtils.isPresent(BASE_MAPPER, classLoader)) {
            throw new IllegalStateException("fastddd.search.backend=sql requires MyBatis-Plus and at least one "
                    + "BaseMapper bean. Add com.gallop.fastddd:fast-ddd-crud and register a mapper, "
                    + "or set fastddd.search.backend=disabled.");
        }
        try {
            Class<?> baseMapper = ClassUtils.forName(BASE_MAPPER, classLoader);
            if (beanFactory.getBeanNamesForType(baseMapper, true, false).length == 0) {
                throw new IllegalStateException("fastddd.search.backend=sql requires at least one BaseMapper bean. "
                        + "Register a MyBatis-Plus mapper or set fastddd.search.backend=disabled.");
            }
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Unable to inspect the SQL search BaseMapper dependency.", ex);
        }
    }

    private static String normalize(String backend) {
        return backend == null ? "" : backend.trim().toLowerCase(Locale.ROOT);
    }
}
