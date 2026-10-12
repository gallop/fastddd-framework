package com.gallop.fastddd.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 表元数据模型
 *
 * @author gallop
 * @date 2023-10-10
 */
@Data
public class TableMetadata {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;

    /**
     * 实体名称（驼峰命名）
     */
    private String entityName;

    /**
     * 列元数据列表
     */
    private List<ColumnMetadata> columns = new ArrayList<>();

    /**
     * 主键列
     */
    private List<ColumnMetadata> primaryKeys = new ArrayList<>();
    /**
     * 聚合名称（可空，供代码生成模板语义标注使用）
     */
    private String aggregateName;

    /**
     * DDD 角色（可空，如 AGGREGATE_ROOT/ENTITY/VALUE_OBJECT，供代码生成模板语义标注使用）
     */
    private String dddRole;

    /**
     * 关系摘要（可空，供代码生成模板语义标注使用）
     */
    private String relationsSummary;

    /**
     * 获取表名（用于模板访问）
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 获取实体名称（用于模板访问）
     */
    public String getEntityName() {
        return entityName;
    }
}
