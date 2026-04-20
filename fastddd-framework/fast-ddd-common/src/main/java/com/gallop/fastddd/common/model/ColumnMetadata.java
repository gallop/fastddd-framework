package com.gallop.fastddd.common.model;

import lombok.Data;

/**
 * 列元数据模型
 *
 * @author gallop
 * @date 2023-10-10
 */
@Data
public class ColumnMetadata {

    /**
     * 列名
     */
    private String columnName;

    /**
     * 列注释
     */
    private String columnComment;

    /**
     * 字段名称（驼峰命名）
     */
    private String fieldName;

    /**
     * 数据类型（JDBC类型）
     */
    private int dataType;

    /**
     * 数据类型名称
     */
    private String dataTypeName;

    /**
     * Java类型
     */
    private String javaType;

    /**
     * 列大小
     */
    private int columnSize;

    /**
     * 小数位数
     */
    private int decimalDigits;

    /**
     * 是否为主键
     */
    private boolean primaryKey;

    /**
     * 是否允许为空
     */
    private boolean nullable;

    /**
     * 是否自增
     */
    private boolean autoIncrement;

    /**
     * 获取列名（用于模板访问）
     */
    public String getName() {
        return columnName;
    }
}
