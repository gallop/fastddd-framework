package com.gallop.fastddd.agentaccess.kit.conformance;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.RetryDisposition;
import com.gallop.fastddd.agentaccess.spi.DedupCheck;
import com.gallop.fastddd.agentaccess.spi.DedupCompletion;
import com.gallop.fastddd.agentaccess.spi.DedupKey;
import com.gallop.fastddd.agentaccess.spi.IdempotencyConflictException;
import com.gallop.fastddd.agentaccess.spi.PersistentDedupPort;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

/**
 * {@link PersistentDedupConformanceContract} 的 PostgreSQL 参考执行
 * （FDD-AA-15 子任务 11.5）。
 *
 * <p><b>守卫</b>：{@code @Testcontainers(disabledWithoutDocker = true)}——
 * 无 Docker 环境整类 skip（JUnit 报告如实记 skipped，不冒充通过）。</p>
 *
 * <p><b>占位表如实标注</b>：{@code fddaa_conformance_agg/dedup/outbox/audit}
 * 是 conformance 显式占位表名，<b>不是</b> V04-09 冻结的生产表名；V04-09
 * 冻结后宿主实现与参考执行须同名同义对齐。占位 Outbox/audit 列集只覆盖
 * 本契约断言所需最小字段。</p>
 *
 * <p><b>纯 java.sql JDBC</b>：无 ORM/事务管理器——同事务语义由
 * {@code setAutoCommit(false)} + 单连接显式 commit/rollback 表达，与
 * TM-14「宿主以自有 DataSource/TransactionManager 保证」的声明面一致
 * （本参考实现只证明 JDBC 层可行性，不构成生产实现）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlDedupConformanceTest extends PersistentDedupConformanceContract {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static String url() {
        return POSTGRES.getJdbcUrl();
    }

    @BeforeAll
    static void createPlaceholderSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(url(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement ddl = c.createStatement()) {
            // 占位表：fddaa_conformance_*（V04-09 冻结真表名后同名同义对齐）
            ddl.execute("""
                    CREATE TABLE fddaa_conformance_dedup (
                      tenant_id              TEXT NOT NULL,
                      business_operation_id  TEXT NOT NULL,
                      schema_version         TEXT NOT NULL,
                      action_id              TEXT NOT NULL,
                      action_version         TEXT NOT NULL,
                      effect_digest          TEXT NOT NULL,
                      issuer                 TEXT NOT NULL,
                      audience               TEXT NOT NULL,
                      binding_ref            TEXT NOT NULL,
                      outcome_code           TEXT NOT NULL,
                      business_command_ref   TEXT,
                      aggregate_root_ref     TEXT,
                      resource_version_after TEXT,
                      committed_at           TIMESTAMPTZ,
                      retry_disposition      TEXT NOT NULL,
                      trace_id               TEXT NOT NULL,
                      PRIMARY KEY (tenant_id, business_operation_id)
                    )""");
            ddl.execute("""
                    CREATE TABLE fddaa_conformance_agg (
                      tenant_id             TEXT NOT NULL,
                      business_operation_id TEXT NOT NULL,
                      aggregate_kind        TEXT NOT NULL,
                      PRIMARY KEY (tenant_id, business_operation_id)
                    )""");
            ddl.execute("""
                    CREATE TABLE fddaa_conformance_outbox (
                      id                   BIGSERIAL PRIMARY KEY,
                      tenant_id            TEXT NOT NULL,
                      business_operation_id TEXT NOT NULL,
                      event_kind           TEXT NOT NULL
                    )""");
            ddl.execute("""
                    CREATE TABLE fddaa_conformance_audit (
                      id                   BIGSERIAL PRIMARY KEY,
                      tenant_id            TEXT NOT NULL,
                      business_operation_id TEXT NOT NULL,
                      outcome_code         TEXT NOT NULL
                    )""");
        }
    }

    @Override
    protected void inBusinessTransaction(TransactionalWork work) throws Exception {
        try (Connection c = DriverManager.getConnection(url(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            c.setAutoCommit(false);
            try {
                work.run(new JdbcDedupPort(c));
                c.commit();
            } catch (Rollback expected) {
                c.rollback();
            } catch (Exception failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    @Override
    protected void writeAggregateSideEffect(PersistentDedupPort sameTxPort, DedupKey key) throws Exception {
        jdbc(sameTxPort).insertAggregate(key);
    }

    @Override
    protected void writeOutboxSideEffect(PersistentDedupPort sameTxPort, DedupKey key) throws Exception {
        jdbc(sameTxPort).insertOutbox(key);
    }

    @Override
    protected boolean aggregateSideEffectPresent(DedupKey key) throws Exception {
        return sideEffectPresent(key, "fddaa_conformance_agg");
    }

    @Override
    protected boolean outboxSideEffectPresent(DedupKey key) throws Exception {
        return sideEffectPresent(key, "fddaa_conformance_outbox");
    }

    @Override
    protected boolean auditSideEffectPresent(DedupKey key) throws Exception {
        return sideEffectPresent(key, "fddaa_conformance_audit");
    }

    private static JdbcDedupPort jdbc(PersistentDedupPort port) {
        if (!(port instanceof JdbcDedupPort jdbcPort)) {
            throw new IllegalArgumentException("伴写钩子需要本参考实现的事务内端口（JdbcDedupPort）");
        }
        return jdbcPort;
    }

    private boolean sideEffectPresent(DedupKey key, String table) throws Exception {
        try (Connection c = DriverManager.getConnection(url(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND business_operation_id = ?")) {
            ps.setString(1, key.tenantId());
            ps.setString(2, key.businessOperationId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    /**
     * 占位表之上的 {@link PersistentDedupPort} JDBC 参考实现。
     *
     * <p>唯一约束冲突（SQLState 23505）用 SAVEPOINT 隔离：回滚到保存点后
     * 事务仍可用——同键同摘要 no-op / 同键异摘要 {@link IdempotencyConflictException}。
     * 首次成功写入同时写占位 audit 行（与去重同事务，回滚一起消失）。</p>
     */
    private static final class JdbcDedupPort implements PersistentDedupPort {

        private final Connection connection;

        JdbcDedupPort(Connection connection) {
            this.connection = connection;
        }

        @Override
        public DedupCheck check(DedupKey key, String effectDigest) {
            if (key == null) {
                throw new IllegalArgumentException("key 必选");
            }
            if (effectDigest == null || effectDigest.isBlank()) {
                throw new IllegalArgumentException("effectDigest 必选且不得为空白");
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                            SELECT effect_digest, schema_version, action_id, action_version, issuer, audience,
                                   binding_ref, outcome_code, business_command_ref, aggregate_root_ref,
                                   resource_version_after, committed_at, retry_disposition, trace_id
                            FROM fddaa_conformance_dedup
                            WHERE tenant_id = ? AND business_operation_id = ?""")) {
                ps.setString(1, key.tenantId());
                ps.setString(2, key.businessOperationId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new DedupCheck.Absent();
                    }
                    String existing = rs.getString("effect_digest");
                    if (!existing.equals(effectDigest)) {
                        return new DedupCheck.Conflict(existing);
                    }
                    return new DedupCheck.Loaded(new BusinessActionReceipt(
                            rs.getString("schema_version"),
                            key.tenantId(),
                            key.businessOperationId(),
                            rs.getString("action_id"),
                            rs.getString("action_version"),
                            existing,
                            rs.getString("issuer"),
                            rs.getString("audience"),
                            rs.getString("binding_ref"),
                            null,
                            ActionOutcomeCode.valueOf(rs.getString("outcome_code")),
                            rs.getString("business_command_ref"),
                            rs.getString("aggregate_root_ref"),
                            rs.getString("resource_version_after"),
                            rs.getObject("committed_at", java.time.OffsetDateTime.class),
                            RetryDisposition.valueOf(rs.getString("retry_disposition")),
                            rs.getString("trace_id"),
                            null));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("去重查询失败（占位表 conformance）", e);
            }
        }

        @Override
        public void recordCompletion(DedupCompletion completion) {
            if (completion == null) {
                throw new IllegalArgumentException("completion 必选");
            }
            BusinessActionReceipt receipt = completion.receipt();
            Savepoint savepoint = null;
            try {
                savepoint = connection.setSavepoint();
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO fddaa_conformance_dedup
                          (tenant_id, business_operation_id, schema_version, action_id, action_version,
                           effect_digest, issuer, audience, binding_ref, outcome_code, business_command_ref,
                           aggregate_root_ref, resource_version_after, committed_at, retry_disposition, trace_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                    ps.setString(1, completion.key().tenantId());
                    ps.setString(2, completion.key().businessOperationId());
                    ps.setString(3, receipt.schemaVersion());
                    ps.setString(4, receipt.actionId());
                    ps.setString(5, receipt.actionVersion());
                    ps.setString(6, completion.effectDigest());
                    ps.setString(7, receipt.issuer());
                    ps.setString(8, receipt.audience());
                    ps.setString(9, receipt.bindingRef());
                    ps.setString(10, receipt.outcomeCode().name());
                    ps.setString(11, receipt.businessCommandRef());
                    ps.setString(12, receipt.aggregateRootRef());
                    ps.setString(13, receipt.resourceVersionAfter());
                    ps.setObject(14, receipt.committedAt());
                    ps.setString(15, receipt.retryDisposition().name());
                    ps.setString(16, receipt.traceId());
                    ps.executeUpdate();
                }
                insertAudit(completion);
                connection.releaseSavepoint(savepoint);
            } catch (SQLException insertRejected) {
                if (!"23505".equals(insertRejected.getSQLState())) {
                    throw new IllegalStateException("去重写入失败（占位表 conformance）", insertRejected);
                }
                // 唯一约束被先占：回滚到保存点（防语句中止毒化事务），再比对摘要
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackFailure) {
                    throw new IllegalStateException("保存点回滚失败", rollbackFailure);
                }
                DedupCheck existing = check(completion.key(), completion.effectDigest());
                if (existing instanceof DedupCheck.Loaded) {
                    return; // 同键同摘要 no-op（§8.2：首次稳定结果保持权威）
                }
                DedupCheck.Conflict conflict = (DedupCheck.Conflict) existing;
                throw new IdempotencyConflictException(completion.key(),
                        conflict.existingEffectDigest(), completion.effectDigest());
            }
        }

        private void insertAudit(DedupCompletion completion) throws SQLException {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO fddaa_conformance_audit (tenant_id, business_operation_id, outcome_code)
                    VALUES (?, ?, ?)""")) {
                ps.setString(1, completion.key().tenantId());
                ps.setString(2, completion.key().businessOperationId());
                ps.setString(3, completion.receipt().outcomeCode().name());
                ps.executeUpdate();
            }
        }

        void insertAggregate(DedupKey key) throws SQLException {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO fddaa_conformance_agg (tenant_id, business_operation_id, aggregate_kind)
                    VALUES (?, ?, ?)""")) {
                ps.setString(1, key.tenantId());
                ps.setString(2, key.businessOperationId());
                ps.setString(3, "arFollowup");
                ps.executeUpdate();
            }
        }

        void insertOutbox(DedupKey key) throws SQLException {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO fddaa_conformance_outbox (tenant_id, business_operation_id, event_kind)
                    VALUES (?, ?, ?)""")) {
                ps.setString(1, key.tenantId());
                ps.setString(2, key.businessOperationId());
                ps.setString(3, "followup.recorded");
                ps.executeUpdate();
            }
        }
    }
}
