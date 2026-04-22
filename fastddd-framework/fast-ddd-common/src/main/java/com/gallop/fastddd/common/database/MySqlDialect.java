package com.gallop.fastddd.common.database;

public class MySqlDialect implements DatabaseDialect {

    @Override
    public String getDbType() { return "mysql"; }

    @Override
    public String getPkDefinition() { return "BIGINT PRIMARY KEY AUTO_INCREMENT"; }

    @Override
    public String getBooleanType() { return "TINYINT(1)"; }

    @Override
    public String getSmallIntType() { return "TINYINT"; }

    @Override
    public String getTimestampType() { return "DATETIME"; }

    @Override
    public String getClobType() { return "TEXT"; }

    @Override
    public String getNowFunction() { return "NOW()"; }

    @Override
    public boolean supportsOnUpdateTimestamp() { return true; }

    @Override
    public String getTableOptions() { return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"; }

    @Override
    public boolean supportsFullTextSearch() { return true; }

    @Override
    public String getFullTextSearchExpression(String[] fields, String keywordPlaceholder) {
        String fieldList = String.join(", ", fields);
        return "MATCH(" + fieldList + ") AGAINST(" + keywordPlaceholder + " IN BOOLEAN MODE)";
    }
}
