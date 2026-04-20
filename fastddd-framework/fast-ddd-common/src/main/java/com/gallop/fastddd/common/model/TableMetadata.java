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
