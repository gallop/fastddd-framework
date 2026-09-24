package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalStatus;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;
import com.gallop.fastddd.identity.core.port.IdentityBindingStore;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import com.gallop.fastddd.identity.pg.support.PrincipalRowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 绑定存储 PG 适配器（T1/T2 组成部分）：查找→创建主体→绑定；
 * 存在时 SELECT ... FOR UPDATE 锁定主体行，锁持有到最终提交。
 *
 * <p>并发首次登录：两个事务同时插入绑定时，后提交者撞五元组唯一约束，
 * 翻译为 {@link BindingConcurrentModificationException} 由用例层新事务回读赢家。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgIdentityBindingStore implements IdentityBindingStore {

    private static final String FIND_LOCKED_SQL = """
            SELECT %s FROM principal p
            JOIN external_binding b ON b.principal_id = p.principal_id
            WHERE b.provider = ? AND b.tenant_id = ? AND b.app_scope = ?
              AND b.external_id_type = ? AND b.external_id = ?
            FOR UPDATE OF p
            """.formatted(PrincipalRowMapper.PRINCIPAL_COLUMNS);

    private static final String FIND_BY_ID_SQL = "SELECT %s FROM principal p WHERE p.principal_id = ?"
            .formatted(PrincipalRowMapper.PRINCIPAL_COLUMNS);

    private static final String INSERT_PRINCIPAL_SQL = """
            INSERT INTO principal (principal_id, tenant_id, principal_type, status, auth_epoch, display_name, login_name)
            VALUES (?, ?, 'HUMAN', 'ACTIVE', 0, ?, ?)
            """;

    private static final String INSERT_BINDING_SQL = """
            INSERT INTO external_binding (provider, tenant_id, app_scope, external_id_type, external_id, principal_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgIdentityBindingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public VerifiedPrincipal findOrCreatePrincipalWithBinding(ExternalIdentity identity,
                                                              PrincipalCreationSpec creationSpec) {
        PgTransactionGate.requireActiveTransaction("findOrCreatePrincipalWithBinding");
        try {
            List<VerifiedPrincipal> existing = jdbcTemplate.query(FIND_LOCKED_SQL, PrincipalRowMapper::map,
                    identity.provider(), identity.tenantId(), identity.appScope(),
                    identity.externalIdType(), identity.externalId());
            if (!existing.isEmpty()) {
                return existing.get(0);
            }

            // T1（T2 子步骤）：创建主体（UUID 文本 36，一次性分配）+ 绑定
            String principalId = UUID.randomUUID().toString();
            jdbcTemplate.update(INSERT_PRINCIPAL_SQL, principalId, identity.tenantId(),
                    creationSpec.displayName(), creationSpec.loginName());
            jdbcTemplate.update(INSERT_BINDING_SQL, identity.provider(), identity.tenantId(), identity.appScope(),
                    identity.externalIdType(), identity.externalId(), principalId);
            return new VerifiedPrincipal(identity.tenantId(), PrincipalId.of(principalId),
                    creationSpec.loginName(), creationSpec.displayName(),
                    PrincipalType.HUMAN, PrincipalStatus.ACTIVE, 0L);
        } catch (DuplicateKeyException e) {
            throw PgTransactionGate.translateBindingConflict("findOrCreatePrincipalWithBinding", e);
        } catch (RuntimeException e) {
            throw PgTransactionGate.translateTransient("findOrCreatePrincipalWithBinding", e);
        }
    }

    @Override
    public Optional<VerifiedPrincipal> findPrincipal(String principalId) {
        // 读查询：不锁定、不要求事务（管理/复验读取）
        List<VerifiedPrincipal> found = jdbcTemplate.query(FIND_BY_ID_SQL, PrincipalRowMapper::map, principalId);
        return found.stream().findFirst();
    }

    /** 测试支撑：当前连接是否持有活动事务（不对外部承诺）。 */
    boolean transactionActiveOnThread() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
