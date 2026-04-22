package com.gallop.fastddd.common.database;

public class PostgreSqlDialect implements DatabaseDialect {

    @Override
    public String getDbType() { return "postgresql"; }

    @Override
    public String getPkDefinition() { return "BIGSERIAL PRIMARY KEY"; }

    @Override
    public String getBooleanType() { return "SMALLINT"; }

    @Override
    public String getSmallIntType() { return "SMALLINT"; }

    @Override
    public String getTimestampType() { return "TIMESTAMP"; }

    @Override
    public String getClobType() { return "TEXT"; }

    @Override
    public String getNowFunction() { return "NOW()"; }

    @Override
    public boolean supportsOnUpdateTimestamp() { return false; }

    @Override
    public String getTableOptions() { return ""; }
}
