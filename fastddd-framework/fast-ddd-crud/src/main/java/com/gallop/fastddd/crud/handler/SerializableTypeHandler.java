package com.gallop.fastddd.crud.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * TypeHandler for {@link Serializable} — bridges BasePO.id (Serializable)
 * to any JDBC column type (BIGINT, VARCHAR, etc.).
 *
 * <p>Delegates to {@link ResultSet#getObject(String)} which returns the
 * driver's native Java type (Long for BIGINT, String for VARCHAR, etc.),
 * all of which implement Serializable.</p>
 */
@MappedTypes(Serializable.class)
public class SerializableTypeHandler extends BaseTypeHandler<Serializable> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Serializable parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public Serializable getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return (Serializable) rs.getObject(columnName);
    }

    @Override
    public Serializable getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return (Serializable) rs.getObject(columnIndex);
    }

    @Override
    public Serializable getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return (Serializable) cs.getObject(columnIndex);
    }
}
