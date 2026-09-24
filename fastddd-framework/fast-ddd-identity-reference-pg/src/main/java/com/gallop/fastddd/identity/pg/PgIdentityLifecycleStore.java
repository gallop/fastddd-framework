package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.PrincipalNotFoundException;
import com.gallop.fastddd.identity.core.port.IdentityLifecyclePort;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import com.gallop.fastddd.identity.pg.support.PrincipalRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 主体生命周期 PG 适配器（T4）：锁主体 → 条件 UPDATE（status/epoch+1）→ 撤销全部会话，
 * 同一事务；已 DISABLED 幂等；不存在 NOT_FOUND；重新启用 epoch 再递增、旧会话不复活。
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgIdentityLifecycleStore implements IdentityLifecyclePort {

    private static final String LOCK_SQL = "SELECT %s FROM principal p WHERE p.principal_id = ? FOR UPDATE"
            .formatted(PrincipalRowMapper.PRINCIPAL_COLUMNS);

    private static final String UPDATE_STATUS_SQL = """
            UPDATE principal SET status = ?, auth_epoch = auth_epoch + 1, updated_at = ? WHERE principal_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PgLocalSessionStore sessionStore;

    public PgIdentityLifecycleStore(JdbcTemplate jdbcTemplate, PgLocalSessionStore sessionStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionStore = sessionStore;
    }

    @Override
    public VerifiedPrincipal deactivatePrincipal(String principalId) {
        PgTransactionGate.requireActiveTransaction("deactivatePrincipal");
        VerifiedPrincipal locked = lockPrincipal(principalId);
        if (locked.status() == com.gallop.fastddd.identity.core.domain.PrincipalStatus.DISABLED) {
            // 幂等：已停用直接成功（现状返回），不重复递增 epoch
            return locked;
        }
        return transition(locked, "DISABLED");
    }

    @Override
    public VerifiedPrincipal reactivatePrincipal(String principalId) {
        PgTransactionGate.requireActiveTransaction("reactivatePrincipal");
        VerifiedPrincipal locked = lockPrincipal(principalId);
        if (locked.status() == com.gallop.fastddd.identity.core.domain.PrincipalStatus.ACTIVE) {
            return locked;
        }
        // epoch 再递增：停用时签发的任何快照永不再匹配（旧会话不复活）
        return transition(locked, "ACTIVE");
    }

    private VerifiedPrincipal transition(VerifiedPrincipal locked, String targetStatus) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(UPDATE_STATUS_SQL, targetStatus, now, locked.principalId().value());
        if ("DISABLED".equals(targetStatus)) {
            // T4 组成部分：撤销全部会话与停用同事务
            sessionStore.revokeAllSessions(locked.principalId().value());
        }
        return new VerifiedPrincipal(locked.tenantId(), locked.principalId(), locked.loginName(),
                locked.displayName(), locked.principalType(),
                com.gallop.fastddd.identity.core.domain.PrincipalStatus.valueOf(targetStatus),
                locked.authEpoch() + 1);
    }

    private VerifiedPrincipal lockPrincipal(String principalId) {
        List<VerifiedPrincipal> found = jdbcTemplate.query(LOCK_SQL, PrincipalRowMapper::map, principalId);
        if (found.isEmpty()) {
            // 严格区分：不存在 NOT_FOUND，不冒充停用成功（FI-03）
            throw new PrincipalNotFoundException("主体不存在：" + principalId);
        }
        return found.get(0);
    }
}
