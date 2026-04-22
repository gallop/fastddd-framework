package com.gallop.fastddd.search;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 搜索索引注册表：管理 indexName ↔ entityClass ↔ tableName 映射。
 * <p>
 * MyBatis Plus 相关代码隔离在 {@link MyBatisPlusHelper} 内部类中，
 * 确保在 fast-ddd-crud 不在 classpath 时不会触发类加载失败。
 *
 * @author gallop
 */
@Slf4j
public class SearchIndexRegistry {

    private final Map<String, IndexDescriptor> indexes = new ConcurrentHashMap<>();
    private final ListableBeanFactory beanFactory;

    public SearchIndexRegistry(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 注册索引描述符。如果 indexName 已存在则跳过。
     */
    public void register(String indexName, Class<?> entityClass) {
        indexes.computeIfAbsent(indexName, name -> {
            String tableName = resolveTableName(entityClass);
            log.info("Search index registered: {} -> {} (table: {})",
                    indexName, entityClass.getSimpleName(), tableName);
            return new IndexDescriptor(indexName, entityClass, tableName);
        });
    }

    public Optional<IndexDescriptor> getDescriptor(String indexName) {
        return Optional.ofNullable(indexes.get(indexName));
    }

    /**
     * 根据实体类查找对应的 indexName。
     */
    public Optional<String> resolveIndexName(Class<?> entityClass) {
        return indexes.entrySet().stream()
                .filter(e -> e.getValue().getEntityClass() == entityClass)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * 尝试获取实体类对应的 Mapper（延迟解析，有缓存）。
     * 仅在 fast-ddd-crud 在 classpath 时可用。
     *
     * @return Mapper 实例，如果无法解析则返回 null
     */
    @Nullable
    public Object resolveMapper(Class<?> entityClass) {
        Optional<IndexDescriptor> desc = indexes.values().stream()
                .filter(d -> d.getEntityClass() == entityClass)
                .findFirst();
        if (desc.isEmpty()) return null;

        IndexDescriptor descriptor = desc.get();
        Object cached = descriptor.getCachedMapper();
        if (cached != null) return cached;

        // 首次解析，委托给隔离的 Helper
        Object mapper = MyBatisPlusHelper.resolveMapper(beanFactory, entityClass);
        if (mapper != null) {
            descriptor.setCachedMapper(mapper);
        }
        return mapper;
    }

    private String resolveTableName(Class<?> entityClass) {
        String tableName = MyBatisPlusHelper.resolveTableName(entityClass);
        if (tableName != null) return tableName;

        // fallback: 类名转下划线
        return camelToSnake(entityClass.getSimpleName());
    }

    static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    @Getter
    public static class IndexDescriptor {
        private final String indexName;
        private final Class<?> entityClass;
        private final String tableName;
        private volatile @Nullable Object cachedMapper;

        public IndexDescriptor(String indexName, Class<?> entityClass, String tableName) {
            this.indexName = indexName;
            this.entityClass = entityClass;
            this.tableName = tableName;
        }

        public void setCachedMapper(Object mapper) {
            this.cachedMapper = mapper;
        }

        @Nullable
        public Object getCachedMapper() {
            return cachedMapper;
        }
    }
}

/**
 * MyBatis Plus 依赖隔离层。
 * <p>
 * 所有对 {@code com.baomidou.mybatisplus} 包的引用都在此类内部。
 * 当 fast-ddd-crud 不在 classpath 时，JVM 不会加载此类，
 * 调用方通过 catch {@link NoClassDefFoundError} 安全降级。
 */
@Slf4j
class MyBatisPlusHelper {

    @Nullable
    static Object resolveMapper(ListableBeanFactory beanFactory, Class<?> entityClass) {
        try {
            com.baomidou.mybatisplus.core.metadata.TableInfo tableInfo =
                    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityClass);
            if (tableInfo != null) {
                String mapperName = tableInfo.getCurrentNamespace();
                if (mapperName != null) {
                    Class<?> mapperType = Class.forName(mapperName);
                    Map<String, ?> mappers = beanFactory.getBeansOfType(mapperType);
                    if (!mappers.isEmpty()) {
                        return mappers.values().iterator().next();
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            log.debug("MyBatis Plus not on classpath, cannot resolve mapper for {}",
                    entityClass.getSimpleName());
        } catch (ClassNotFoundException e) {
            log.debug("Mapper class not found for {}: {}", entityClass.getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to resolve mapper for {}: {}", entityClass.getSimpleName(), e.getMessage());
        }
        return null;
    }

    @Nullable
    static String resolveTableName(Class<?> entityClass) {
        try {
            com.baomidou.mybatisplus.core.metadata.TableInfo tableInfo =
                    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityClass);
            if (tableInfo != null) {
                return tableInfo.getTableName();
            }
        } catch (NoClassDefFoundError ignored) {
            // MyBatis Plus not on classpath
        }
        return null;
    }
}
