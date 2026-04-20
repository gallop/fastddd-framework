package com.gallop.fastddd.crud.injector.methods;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import java.util.List;

/**
 * MyBatis Plus SQL注入的批量插入方法实现。
 * 生成用于在单个语句中插入多条记录的SQL。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public class BatchInsert extends AbstractMethod {

    /**
     * The method name that will be available in the mapper interface.
     */
    public static final String METHOD_NAME = "batchInsert";

    /**
     * Constructor that sets the method name.
     */
    public BatchInsert() {
        super(METHOD_NAME);
    }

    /**
     * Inject the batch insert method into the mapper.
     * 
     * @param mapperClass the mapper class
     * @param modelClass the entity class
     * @param tableInfo the table information
     * @return the mapped statement
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // Build the SQL template for batch insert
        String sql = buildBatchInsertSql(tableInfo);
        
        // Create SQL source
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, List.class);
        
        // Return the mapped statement
        return this.addMappedStatement(mapperClass, METHOD_NAME, sqlSource, SqlCommandType.INSERT, List.class, null, null, new NoKeyGenerator(), null, null);
    }

    /**
     * Build the batch insert SQL statement.
     * 
     * @param tableInfo the table information
     * @return the SQL string
     */
    private String buildBatchInsertSql(TableInfo tableInfo) {
        StringBuilder sql = new StringBuilder();
        
        // Start with INSERT INTO
        sql.append("<script>\n");
        sql.append("<if test=\"list != null and list.size() > 0\">\n");
        sql.append("INSERT INTO ").append(tableInfo.getTableName()).append(" (");
        
        // Add column names
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        
        // Add key column if exists and not AUTO type
        if (tableInfo.getKeyColumn() != null && !tableInfo.getIdType().equals(IdType.AUTO)) {
            columns.append(tableInfo.getKeyColumn());
            values.append("#{item.").append(tableInfo.getKeyProperty()).append("}");
        }
        
        // Add other columns
        tableInfo.getFieldList().forEach(field -> {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(field.getColumn());
            values.append("#{item.").append(field.getProperty()).append("}");
        });
        
        sql.append(columns).append(") VALUES ");
        
        // Add foreach for batch values
        sql.append("\n<foreach collection=\"list\" item=\"item\" separator=\",\">\n");
        sql.append("(").append(values).append(")\n");
        sql.append("</foreach>\n");
        sql.append("</if>\n");
        sql.append("</script>");
        
        return sql.toString();
    }
}