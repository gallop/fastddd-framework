package com.gallop.fastddd.crud.injector.methods;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import java.util.List;

/**
 * MyBatis Plus SQL注入的批量按ID更新方法实现。
 * 生成用于在单个语句中按主键更新多条记录的SQL。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public class BatchUpdateById extends AbstractMethod {

    /**
     * The method name that will be available in the mapper interface.
     */
    public static final String METHOD_NAME = "batchUpdateById";

    /**
     * Constructor that sets the method name.
     */
    public BatchUpdateById() {
        super(METHOD_NAME);
    }

    /**
     * Inject the batch update by ID method into the mapper.
     * 
     * @param mapperClass the mapper class
     * @param modelClass the entity class
     * @param tableInfo the table information
     * @return the mapped statement
     */
    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // Build the SQL template for batch update by ID
        String sql = buildBatchUpdateByIdSql(tableInfo);
        
        // Create SQL source
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, List.class);
        
        // Return the mapped statement
        return this.addMappedStatement(mapperClass, METHOD_NAME, sqlSource, SqlCommandType.UPDATE, List.class, null, null, new NoKeyGenerator(), null, null);
    }

    /**
     * Build the batch update by ID SQL statement using CASE WHEN approach.
     * This generates a single UPDATE statement that can update multiple records.
     * 
     * @param tableInfo the table information
     * @return the SQL string
     */
    private String buildBatchUpdateByIdSql(TableInfo tableInfo) {
        StringBuilder sql = new StringBuilder();
        
        sql.append("<script>\n");
        sql.append("<if test=\"list != null and list.size() > 0\">\n");
        sql.append("UPDATE ").append(tableInfo.getTableName()).append(" SET ");
        
        // Build SET clause with CASE WHEN for each field
        StringBuilder setClauses = new StringBuilder();
        
        tableInfo.getFieldList().forEach(field -> {
            if (setClauses.length() > 0) {
                setClauses.append(", ");
            }
            
            setClauses.append(field.getColumn()).append(" = CASE ").append(tableInfo.getKeyColumn());
            setClauses.append("\n<foreach collection=\"list\" item=\"item\">\n");
            setClauses.append(" WHEN #{item.").append(tableInfo.getKeyProperty()).append("} THEN #{item.").append(field.getProperty()).append("}\n");
            setClauses.append("</foreach>\n");
            setClauses.append(" END");
        });
        
        sql.append(setClauses);
        
        // Add WHERE clause to limit updates to the provided IDs
        sql.append("\nWHERE ").append(tableInfo.getKeyColumn()).append(" IN ");
        sql.append("\n<foreach collection=\"list\" item=\"item\" open=\"(\" close=\")\" separator=\",\">\n");
        sql.append("#{item.").append(tableInfo.getKeyProperty()).append("}\n");
        sql.append("</foreach>\n");
        sql.append("</if>\n");
        sql.append("</script>");
        
        return sql.toString();
    }
}