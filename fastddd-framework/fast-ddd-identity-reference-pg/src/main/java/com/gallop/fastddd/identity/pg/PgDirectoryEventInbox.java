package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 入站事件收件箱 PG 参考适配器（W5 设计 §4.1/§5.1，W5-c 交付）。
 *
 * <p><b>事务模型</b>：每个方法恰一条原子语句（INSERT ... ON CONFLICT / 带子查询
 * 锁定的单 UPDATE ... RETURNING），不依赖调用方事务——单语句自动提交即
 * "单短事务"（§4.1 :176）；若调用方事务在场则自然汇入，无双重开事务。</p>
 *
 * <p><b>claim 形态</b>：{@code UPDATE ... WHERE event_id IN (SELECT ... FOR UPDATE
 * SKIP LOCKED ORDER BY received_at, event_id LIMIT n)} 单语句原子完成置位——
 * 多实例并发 claim 互不取同行（SKIP LOCKED）；排序带 {@code event_id} 决胜
 * （received_at 并列时取序确定，多实例消费总序可复现——W5-c D11 移交项，
 * W5-d 兑现）；租约到期由 PG {@code now()} 判定，时钟单源。</p>
 *
 * <p><b>attempts 上限</b>：构造器注入（缺省 5，W5 设计 §4.1 上限属实现配置——
 * starter 装配面 W5-d 接配置键）；达上限行置 FAILED 终态（管理端显式重放入口，
 * 不自动重试）。last_error 截断至 255（V4 列宽）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryEventInbox implements DirectoryEventInbox {

    private static final String INSERT_SQL = """
            INSERT INTO directory_event_inbox
                (event_id, tenant_id, app_scope, event_type, payload, received_at, state, attempts)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, 'RECEIVED', 0)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final String CLAIM_SQL = """
            UPDATE directory_event_inbox SET state = 'PROCESSING',
                    claimed_at = now(), lease_expires_at = now() + make_interval(secs => ?)
            WHERE event_id IN (
                SELECT event_id FROM directory_event_inbox
                WHERE state = 'RECEIVED'
                ORDER BY received_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            RETURNING event_id, tenant_id, app_scope, event_type, payload::text, received_at,
                      state, attempts, last_error, processed_at, claimed_at, lease_expires_at
            """;

    private static final String RECLAIM_SQL = """
            UPDATE directory_event_inbox SET
                    attempts = attempts + 1,
                    state = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'RECEIVED' END,
                    claimed_at = NULL, lease_expires_at = NULL,
                    last_error = COALESCE(last_error, 'lease-expired')
            WHERE state = 'PROCESSING' AND lease_expires_at < now()
            RETURNING state
            """;

    private static final String MARK_PROCESSED_SQL = """
            UPDATE directory_event_inbox SET state = 'PROCESSED', processed_at = now(),
                    claimed_at = NULL, lease_expires_at = NULL
            WHERE event_id = ? AND state = 'PROCESSING'
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE directory_event_inbox SET
                    last_error = ?, attempts = attempts + 1,
                    state = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'RECEIVED' END,
                    claimed_at = NULL, lease_expires_at = NULL
            WHERE event_id = ? AND state = 'PROCESSING'
            """;

    /** attempts 缺省上限（§4.1 上限属实现配置；starter 装配面注入覆盖）。 */
    static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final int maxAttempts;

    public PgDirectoryEventInbox(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_MAX_ATTEMPTS);
    }

    public PgDirectoryEventInbox(JdbcTemplate jdbcTemplate, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 ≥ 1（收到：" + maxAttempts + "）");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean persist(EventEnvelope envelope) {
        return jdbcTemplate.update(INSERT_SQL,
                envelope.eventId(),
                envelope.scope().tenantKey(),
                envelope.scope().appScope(),
                envelope.eventType(),
                envelope.payload(),
                Timestamp.from(envelope.receivedAt())) > 0;
    }

    @Override
    public List<EventEnvelope> claim(int limit, Duration lease) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须 ≥ 1（收到：" + limit + "）");
        }
        long leaseSeconds;
        try {
            leaseSeconds = Math.max(0, lease.toSeconds());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("lease 时长溢出：" + lease, e);
        }
        return jdbcTemplate.query(con -> {
            PreparedStatement claim = con.prepareStatement(CLAIM_SQL);
            claim.setLong(1, leaseSeconds);
            claim.setInt(2, limit);
            return claim;
        }, (rs, i) -> mapRow(rs));
    }

    @Override
    public int reclaimExpired() {
        List<String> states = jdbcTemplate.query(con -> {
            PreparedStatement reclaim = con.prepareStatement(RECLAIM_SQL);
            reclaim.setInt(1, maxAttempts);
            return reclaim;
        }, (rs, i) -> rs.getString("state"));
        return (int) states.stream().filter("RECEIVED"::equals).count();
    }

    @Override
    public void markProcessed(String eventId) {
        // 0 行 = 行已终态（幂等空操作）或租约已被回收复位（at-least-once 再投递），
        // 均为正常路径——无异常、无副作用
        jdbcTemplate.update(MARK_PROCESSED_SQL, eventId);
    }

    @Override
    public void markFailed(String eventId, String reason) {
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(MARK_FAILED_SQL);
            ps.setString(1, truncate(reason));
            ps.setInt(2, maxAttempts);
            ps.setString(3, eventId);
            return ps;
        });
    }

    private static EventEnvelope mapRow(ResultSet rs) throws SQLException {
        return new EventEnvelope(
                rs.getString("event_id"),
                new DirectoryScope(rs.getString("tenant_id"), rs.getString("app_scope")),
                rs.getString("event_type"),
                rs.getString("payload"),
                toInstant(rs.getTimestamp("received_at")),
                EventEnvelope.State.valueOf(rs.getString("state")),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                toInstant(rs.getTimestamp("processed_at")),
                toInstant(rs.getTimestamp("claimed_at")),
                toInstant(rs.getTimestamp("lease_expires_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
