package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.RejectionRecord;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * external_rejection 门禁 PG 参考适配器（W5 设计 §7.5，W5-c 交付）。
 *
 * <p><b>advisory 契约承载</b>（端口 javadoc 冻结契约的落地形态，R3-O-2 可调用性
 * 由 {@code PgExternalRejectionGateTest} 在 zonky 真 PG 上实测确认）：所有写入路径
 * （{@code lockActive}/{@code record}/{@code clearControlled}/
 * {@code clearByAuthoritativeRestoration}）第一把锁 =
 * {@code pg_advisory_xact_lock(ADVISORY_LOCK_CLASS, hashtext(normalizedLockKey(identity)))}
 * ——双参 {@code _xact_} 事务作用域（非 session 级）键级序列化；行不存在亦持锁至
 * 事务结束、无行不插空行；lockActive 锁内 {@code FOR UPDATE} 仅为读改写竞态防护。
 * 锁序 = ⓪ advisory → 拒绝行 →（调用方）binding/principal → session，全域无环；
 * 本类不触 binding/principal（T2/T4 行为集成归 W5-e）。</p>
 *
 * <p><b>事务模型</b>：与 {@link PgDirectoryMirrorStore} 同——不开事务，写入路径
 * 与 lockActive 首行经 {@link PgTransactionGate} 拒绝无事务调用（advisory
 * {@code _xact_} 锁必须活在事务内）；{@code findActive} 为无锁普通读、无事务
 * 要求（亦可在事务内复用——非门禁读取面）。</p>
 *
 * <p><b>record 幂等语义（W5-e D4 复活补位后）</b>：{@code INSERT ... ON CONFLICT
 * DO UPDATE SET cleared_at = NULL, reason = EXCLUDED.reason RETURNING}——幂等
 * upsert：<b>已清除行再度确证停用时复活</b>（cleared_at 置 NULL）且 reason 更新
 * 为本次确证理由（三写入支均为「确证停用」语义，理由 = 最新事实）；活跃行重复
 * record 仅刷新 reason。原 W5-c「DO NOTHING 不复活不覆盖」字面契约经 W5-c 执行
 * 记录 D4 + W5-d D14（升格「必修」）授权补位——不复活形态下「权威恢复清除后再
 * 度确证停用」拒绝永不生效，门禁穿透，故复活为必要语义。</p>
 *
 * <p><b>时间戳</b>：created_at/cleared_at 均由 PG 服务端 {@code now()} 定值
 * （DEFAULT 列 + UPDATE 语句内联），Java 侧只读回填 {@link RejectionRecord}。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgExternalRejectionGate implements ExternalRejectionGate {

    /** 双参事务作用域形态：实测签名 (int, int)——类常量 int + hashtext 返回 int，零 cast 直配。 */
    private static final String ACQUIRE_KEY_LOCK_SQL = """
            SELECT pg_advisory_xact_lock(?, hashtext(?))
            """;

    private static final String SELECT_ACTIVE_SQL = """
            SELECT reason, created_at, cleared_at
            FROM external_rejection
            WHERE provider = ? AND tenant_id = ? AND app_scope = ?
              AND external_id_type = ? AND external_id = ? AND cleared_at IS NULL
            """;

    private static final String LOCK_ACTIVE_SQL = """
            SELECT reason, created_at, cleared_at
            FROM external_rejection
            WHERE provider = ? AND tenant_id = ? AND app_scope = ?
              AND external_id_type = ? AND external_id = ? AND cleared_at IS NULL
            FOR UPDATE
            """;

    /** D4 复活补位（W5-e）：冲突行复活 + reason 刷新（幂等 upsert，类文档 record 段）。 */
    private static final String INSERT_REJECTION_SQL = """
            INSERT INTO external_rejection (provider, tenant_id, app_scope, external_id_type, external_id, reason)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (provider, tenant_id, app_scope, external_id_type, external_id)
            DO UPDATE SET cleared_at = NULL, reason = EXCLUDED.reason
            RETURNING created_at
            """;

    private static final String SELECT_BY_KEY_SQL = """
            SELECT reason, created_at, cleared_at
            FROM external_rejection
            WHERE provider = ? AND tenant_id = ? AND app_scope = ?
              AND external_id_type = ? AND external_id = ?
            """;

    private static final String CLEAR_SQL = """
            UPDATE external_rejection
            SET cleared_at = now()
            WHERE provider = ? AND tenant_id = ? AND app_scope = ?
              AND external_id_type = ? AND external_id = ? AND cleared_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PgTransactionGate gate;

    public PgExternalRejectionGate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
    }

    @Override
    public Optional<RejectionRecord> findActive(ExternalIdentity identity) {
        return queryOptional(SELECT_ACTIVE_SQL, identity);
    }

    @Override
    public Optional<RejectionRecord> lockActive(ExternalIdentity identity) {
        gate.requireActiveTransaction("external-rejection:lockActive");
        acquireKeyLock(identity);
        return queryOptional(LOCK_ACTIVE_SQL, identity);
    }

    @Override
    public RejectionRecord record(ExternalIdentity identity, String reason) {
        gate.requireActiveTransaction("external-rejection:record");
        acquireKeyLock(identity);
        // D4 复活补位（W5-e）：幂等 upsert——冲突行 DO UPDATE 复活（cleared_at
        // 置 NULL）+ reason 刷新为本次确证理由；RETURNING 两分支同构
        // （reason = 入参、cleared = null），无回落读必要
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_REJECTION_SQL);
            bindKey(ps, identity, 0);
            ps.setString(6, reason);
            return ps;
        }, rs -> rs.next()
                ? new RejectionRecord(identity, reason, rs.getTimestamp("created_at").toInstant(), null)
                : null);
    }

    @Override
    public int clearControlled(ExternalIdentity identity, String operator, String reason) {
        // operator/reason 入审计日志不入表（V1 无对应列）——W5-d/e 接线时由调用方
        // 落审计；本适配器仅按端口契约清除行
        return clear(identity);
    }

    @Override
    public int clearByAuthoritativeRestoration(ExternalIdentity identity, String evidenceRef) {
        // evidenceRef 指向 RECHECK_COMMITTED 变更日志条目（§7.5）；V1 表无载体列，
        // 证据链经 change_log 可追溯，此处不落表
        return clear(identity);
    }

    private int clear(ExternalIdentity identity) {
        gate.requireActiveTransaction("external-rejection:clear");
        acquireKeyLock(identity);
        return jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(CLEAR_SQL);
            bindKey(ps, identity, 0);
            return ps;
        });
    }

    /** ⓪ 键级事务作用域序锁（{@code pg_advisory_xact_lock} 返回 void——单行结果以 extractor 消费）。 */
    private void acquireKeyLock(ExternalIdentity identity) {
        jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(ACQUIRE_KEY_LOCK_SQL);
            ps.setInt(1, ADVISORY_LOCK_CLASS);
            ps.setString(2, ExternalRejectionGate.normalizedLockKey(identity));
            return ps;
        }, rs -> null);
    }

    private Optional<RejectionRecord> queryOptional(String sql, ExternalIdentity identity) {
        return Optional.ofNullable(jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            bindKey(ps, identity, 0);
            return ps;
        }, (ResultSetExtractor<RejectionRecord>) rs -> rs.next() ? mapRow(rs, identity) : null));
    }

    private static RejectionRecord mapRow(ResultSet rs, ExternalIdentity identity) throws SQLException {
        Timestamp cleared = rs.getTimestamp("cleared_at");
        return new RejectionRecord(identity, rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant(),
                cleared == null ? null : cleared.toInstant());
    }

    private static void bindKey(PreparedStatement ps, ExternalIdentity identity, int offset) throws SQLException {
        ps.setString(offset + 1, identity.provider());
        ps.setString(offset + 2, identity.tenantId());
        ps.setString(offset + 3, identity.appScope());
        ps.setString(offset + 4, identity.externalIdType());
        ps.setString(offset + 5, identity.externalId());
    }
}
