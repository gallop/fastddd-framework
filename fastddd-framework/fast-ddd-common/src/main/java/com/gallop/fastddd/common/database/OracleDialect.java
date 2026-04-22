package com.gallop.fastddd.common.database;

public class OracleDialect implements DatabaseDialect {

    @Override
    public String getDbType() { return "oracle"; }

    @Override
    public String getPkDefinition() { return "NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY"; }

    @Override
    public String getBooleanType() { return "NUMBER(1)"; }

    @Override
    public String getSmallIntType() { return "NUMBER(3)"; }

    @Override
    public String getTimestampType() { return "TIMESTAMP"; }

    @Override
    public String getClobType() { return "CLOB"; }

    @Override
    public String getNowFunction() { return "SYSTIMESTAMP"; }

    @Override
    public boolean supportsOnUpdateTimestamp() { return false; }

    @Override
    public String getTableOptions() { return ""; }
}
