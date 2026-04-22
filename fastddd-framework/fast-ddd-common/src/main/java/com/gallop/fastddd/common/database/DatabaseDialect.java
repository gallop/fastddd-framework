package com.gallop.fastddd.common.database;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 框架级数据库方言接口，用于生成数据库特定的 DDL。
 * 通过 DatabaseMetaData 自动检测数据库类型。
 *
 * <p>注意：本接口独立于 Studio 模块的
 * {@code com.gallop.fastddd.studio.dialect.DatabaseDialect}，
 * 后者绑定 TableDesign 等设计器实体，本接口只关注框架运行时最小能力。</p>
 */
public interface DatabaseDialect {

    String getDbType();

    String getPkDefinition();

    String getBooleanType();

    String getSmallIntType();

    String getTimestampType();

    /** 大文本类型: MySQL/PG→"TEXT", Oracle→"CLOB", SQLServer→"NVARCHAR(MAX)" */
    String getClobType();

    /** 获取当前时间的 SQL 函数: MySQL/PG→"NOW()", Oracle→"SYSTIMESTAMP", SQLServer→"GETDATE()" */
    String getNowFunction();

    boolean supportsOnUpdateTimestamp();

    String getTableOptions();

    /**
     * 从 DataSource 检测数据库方言。
     * 检测失败时 fallback 到 MySqlDialect（向后兼容）。
     */
    static DatabaseDialect detect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            DatabaseDialect dialect = forProductName(productName);
            if ("mysql".equals(dialect.getDbType()) && productName != null
                    && !productName.toUpperCase().contains("MYSQL")) {
                System.getLogger(DatabaseDialect.class.getName()).log(System.Logger.Level.DEBUG,
                        "Unrecognized database product '{0}', falling back to MySQL dialect", productName);
            }
            return dialect;
        } catch (Exception e) {
            return new MySqlDialect();
        }
    }

    static DatabaseDialect forProductName(String productName) {
        if (productName == null) return new MySqlDialect();
        String upper = productName.toUpperCase();
        if (upper.contains("POSTGRE")) return new PostgreSqlDialect();
        if (upper.contains("ORACLE")) return new OracleDialect();
        if (upper.contains("SQL SERVER")) return new SqlServerDialect();
        return new MySqlDialect();
    }
}
