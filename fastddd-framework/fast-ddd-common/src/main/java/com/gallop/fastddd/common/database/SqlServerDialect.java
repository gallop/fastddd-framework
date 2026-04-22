package com.gallop.fastddd.common.database;

public class SqlServerDialect implements DatabaseDialect {

    @Override
    public String getDbType() { return "sqlserver"; }

    @Override
    public String getPkDefinition() { return "BIGINT IDENTITY(1,1) PRIMARY KEY"; }

    @Override
    public String getBooleanType() { return "BIT"; }

    @Override
    public String getSmallIntType() { return "SMALLINT"; }

    @Override
    public String getTimestampType() { return "DATETIME2"; }

    @Override
    public String getClobType() { return "NVARCHAR(MAX)"; }

    @Override
    public String getNowFunction() { return "GETDATE()"; }

    @Override
    public boolean supportsOnUpdateTimestamp() { return false; }

    @Override
    public String getTableOptions() { return ""; }
}
