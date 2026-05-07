package com.gallop.fastddd.crud.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * 扩展MyBatis Plus BaseMapper的基础仓储接口。
 * 提供按字段查询、批量操作等便捷方法，不要求实体继承 BasePO。
 *
 * <p>继承 BasePO 的实体可通过 {@code BaseRepository<XxxPO>} 配合框架 IService 使用；
 * 纯数据表（无审计字段、无软删除）可直接使用 {@code BaseRepository<XxxTable>}，
 * 享受便捷查询方法而无需继承 BasePO。</p>
 *
 * @param <T> 实体类型
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public interface BaseRepository<T> extends BaseMapper<T> {

    /**
     * 将 Java 字段名转为数据库列名（camelCase → snake_case）。
     * 已是 snake_case 的字段名不受影响。
     */
    private static String toColumnName(String field) {
        if (field == null || field.isEmpty()) {
            return field;
        }
        // 已包含下划线，视为 snake_case，直接返回
        if (field.indexOf('_') >= 0) {
            return field;
        }
        StringBuilder sb = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Select one record by a specific field value.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match
     * @return Optional containing the found entity, or empty if not found
     */
    default Optional<T> selectOneByField(String field, Object value) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(toColumnName(field), value);
        T result = selectOne(queryWrapper);
        return Optional.ofNullable(result);
    }

    /**
     * Select multiple records by a specific field value.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match
     * @return List of entities matching the criteria
     */
    default List<T> selectListByField(String field, Object value) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(toColumnName(field), value);
        return selectList(queryWrapper);
    }

    /**
     * Select records by multiple field values using AND condition.
     *
     * @param field1 first field name (camelCase or snake_case)
     * @param value1 first field value
     * @param field2 second field name (camelCase or snake_case)
     * @param value2 second field value
     * @return List of entities matching both criteria
     */
    default List<T> selectListByFields(String field1, Object value1, String field2, Object value2) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(toColumnName(field1), value1).eq(toColumnName(field2), value2);
        return selectList(queryWrapper);
    }

    /**
     * Select records by field value using LIKE condition.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match (supports wildcards)
     * @return List of entities matching the LIKE criteria
     */
    default List<T> selectListByFieldLike(String field, Object value) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(toColumnName(field), value);
        return selectList(queryWrapper);
    }

    /**
     * Select records by field values using IN condition.
     *
     * @param field the field name (camelCase or snake_case)
     * @param values the collection of field values to match
     * @return List of entities matching any of the values
     */
    default List<T> selectListByFieldIn(String field, List<? extends Serializable> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(toColumnName(field), values);
        return selectList(queryWrapper);
    }

    /**
     * Count records by a specific field value.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match
     * @return count of records matching the criteria
     */
    default Long countByField(String field, Object value) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(toColumnName(field), value);
        return selectCount(queryWrapper);
    }

    /**
     * Check if a record exists by a specific field value.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match
     * @return true if at least one record exists, false otherwise
     */
    default boolean existsByField(String field, Object value) {
        return countByField(field, value) > 0;
    }

    /**
     * Delete records by a specific field value.
     *
     * @param field the field name (camelCase or snake_case)
     * @param value the field value to match
     * @return number of deleted records
     */
    default int deleteByField(String field, Object value) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(toColumnName(field), value);
        return delete(queryWrapper);
    }
    
    /**
     * Batch insert multiple records in a single SQL statement.
     * This method is provided by the custom SQL injector.
     * 
     * @param entityList the list of entities to insert
     * @return number of inserted records
     */
    int batchInsert(List<T> entityList);
    
    /**
     * Batch update multiple records by their IDs in a single SQL statement.
     * This method is provided by the custom SQL injector.
     * 
     * @param entityList the list of entities to update (must have valid IDs)
     * @return number of updated records
     */
    int batchUpdateById(List<T> entityList);
}