package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.IssuedSession;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.UnknownSessionFormatException;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import com.gallop.fastddd.identity.pg.support.PrincipalRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 本地会话 PG 适配器：T2 建（epoch 快照 + 格式版本）、每请求权威复验
 * （会话行 JOIN 主体行，不锁定）、T3 撤单、T4 撤全。
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgLocalSessionStore implements LocalSessionPort {

    private static final String INSERT_SESSION_SQL = """
            INSERT INTO local_session (session_id, principal_id, tenant_id, auth_epoch_at_issue,
                                       session_format_version, issued_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String VALIDATE_SQL = """
            SELECT s.session_id, s.auth_epoch_at_issue, s.session_format_version, s.issued_at, s.expires_at,
                   s.revoked_at, %s
            FROM local_session s JOIN principal p ON p.principal_id = s.principal_id
            WHERE s.session_id = ?
            """.formatted(PrincipalRowMapper.PRINCIPAL_COLUMNS);

    private static final String REVOKE_ONE_SQL =
            "UPDATE local_session SET revoked_at = ? WHERE session_id = ? AND revoked_at IS NULL";

    private static final String REVOKE_ALL_SQL =
            "UPDATE local_session SET revoked_at = ? WHERE principal_id = ? AND revoked_at IS NULL";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /** 可注入时钟（测试确定性）。 */
    public interface Clock {
        Instant now();
    }

    public PgLocalSessionStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Instant::now);
    }

    public PgLocalSessionStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public IssuedSession issueSession(VerifiedPrincipal principal, Duration ttl) {
        PgTransactionGate.requireActiveTransaction("issueSession");
        Instant now = clock.now();
        // ≥256bit（32 字节）随机，base64url 不透明文本（43 字符）
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(ttl);
        jdbcTemplate.update(INSERT_SESSION_SQL, sessionId, principal.principalId().value(), principal.tenantId(),
                principal.authEpoch(), IssuedSession.FORMAT_VERSION, Timestamp.from(now), Timestamp.from(expiresAt));
        return new IssuedSession(SessionId.of(sessionId), principal.tenantId(), principal.principalId(),
                principal.authEpoch(), IssuedSession.FORMAT_VERSION, now, expiresAt);
    }

    @Override
    public Optional<VerifiedPrincipal> validateSession(SessionId sessionId) {
        // 每请求权威读取：不锁定、不要求事务；M1 不引入认证缓存
        List<SessionJoin> rows = jdbcTemplate.query(VALIDATE_SQL, this::mapJoin, sessionId.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SessionJoin row = rows.get(0);
        if (row.sessionFormatVersion != IssuedSession.FORMAT_VERSION) {
            throw new UnknownSessionFormatException(
                    "未知会话格式版本：" + row.sessionFormatVersion + "（支持矩阵仅 " + IssuedSession.FORMAT_VERSION
                            + "），拒绝认证（§13.8）");
        }
        if (row.revokedAt != null || clock.now().isAfter(row.expiresAt)) {
            return Optional.empty();
        }
        if (row.principal.status() != com.gallop.fastddd.identity.core.domain.PrincipalStatus.ACTIVE) {
            return Optional.empty();
        }
        if (row.principal.authEpoch() != row.authEpochAtIssue) {
            return Optional.empty();
        }
        return Optional.of(row.principal);
    }

    @Override
    public void revokeSession(SessionId sessionId) {
        PgTransactionGate.requireActiveTransaction("revokeSession");
        // 幂等：已撤销行为 0 行更新，不报错
        jdbcTemplate.update(REVOKE_ONE_SQL, Timestamp.from(clock.now()), sessionId.value());
    }

    @Override
    public int revokeAllSessions(String principalId) {
        PgTransactionGate.requireActiveTransaction("revokeAllSessions");
        return jdbcTemplate.update(REVOKE_ALL_SQL, Timestamp.from(clock.now()), principalId);
    }

    private SessionJoin mapJoin(ResultSet rs, int rowNum) throws SQLException {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new SessionJoin(
                rs.getLong("auth_epoch_at_issue"),
                rs.getInt("session_format_version"),
                rs.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                PrincipalRowMapper.map(rs, rowNum));
    }

    private record SessionJoin(long authEpochAtIssue, int sessionFormatVersion,
                               Instant expiresAt, Instant revokedAt, VerifiedPrincipal principal) {
    }
}
