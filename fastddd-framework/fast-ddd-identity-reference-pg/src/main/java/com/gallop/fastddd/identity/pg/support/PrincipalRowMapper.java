package com.gallop.fastddd.identity.pg.support;

import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * principal 行 → {@link VerifiedPrincipal} 映射（单源，所有查询共用）。
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PrincipalRowMapper {

    private PrincipalRowMapper() {
    }

    /** RowMapper 形态（两参）：供 {@code JdbcTemplate.query(sql, PrincipalRowMapper::map, args...)}。 */
    public static VerifiedPrincipal map(ResultSet rs, int rowNum) throws SQLException {
        return new VerifiedPrincipal(
                rs.getString("tenant_id"),
                PrincipalId.of(rs.getString("principal_id")),
                rs.getString("login_name"),
                rs.getString("display_name"),
                PrincipalType.valueOf(rs.getString("principal_type")),
                PrincipalStatus.valueOf(rs.getString("status")),
                rs.getLong("auth_epoch"));
    }

    public static final String PRINCIPAL_COLUMNS =
            "p.principal_id, p.tenant_id, p.principal_type, p.status, p.auth_epoch, p.login_name, p.display_name";
}
