package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.LoginStateEntry;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 登录授权上下文（state）PG 适配器（W2-2，v0.2 §7.3）：授权发起时保存
 * state + PKCE verifier + 浏览器绑定 + 回跳地址；回调时以条件更新<b>原子一次性消费</b>。
 *
 * <p><b>原子性</b>：consume 为单条
 * {@code UPDATE … WHERE consumed_at IS NULL AND expires_at > now RETURNING …}——
 * 未过期且未消费才置 consumed_at 并返回上下文；并发重复回调恰有一个成功
 * （行级写锁裁决），其余返回 empty，不存在"先读后写"的检查窗口。save 的同 state
 * 重复插入由主键拒绝。</p>
 *
 * <p><b>事务责任</b>：MANDATORY（门禁同其他写端口）——save/consume 经 runner 以
 * 独立短事务执行（T-state，先于 T2 签发事务；两事务不合并：T2 回滚不复活已消费
 * 的 state）。expires_at 判定用适配器时钟参数（非 DB now()），测试可确定性推进。</p>
 *
 * <p><b>过期回收（W22-RV-01）</b>：授权发起端点是匿名 permitAll 入口，每次请求插一行，
 * 必须有界回收。save 在同一 T-state 事务内先执行惰性清理
 * {@code DELETE FROM login_state WHERE expires_at < now - retention}（V2 建
 * {@code idx_login_state_expires_at} 索引，清理走索引且不加表级锁）；retention 是
 * 过期后的物理保留窗口（审计/排障可查），缺省 1 小时，经
 * {@code fastddd.identity.pg.login-state-retention-seconds} 调整。清理不区分
 * consumed_at（已消费行在语义上等价于不存在——重放按 state-missing 同样 401），
 * 删除已消费行不改变任何裁决结果。</p>
 *
 * <p>V1 的 {@code redirect_uri} 列不承载 per-state 数据（授权登记地址是应用级配置
 * {@code fastddd.identity.feishu.redirect-uri}）；W2-2 新列见
 * {@code V2__login_state_browser_binding.sql}。code_verifier/browser_binding
 * 不写入任何日志。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgLoginStateStore implements LoginStateStore {

    /** 过期行物理保留窗口缺省（复评 RV-01：无界保留是 permitAll 匿名写入口的成规模攻击面）。 */
    public static final Duration DEFAULT_RETENTION = Duration.ofHours(1);

    private static final String INSERT_SQL = """
            INSERT INTO login_state (state, code_verifier, browser_binding, post_login_redirect,
                                     expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String PURGE_SQL = """
            DELETE FROM login_state WHERE expires_at < ?
            """;

    private static final String CONSUME_SQL = """
            UPDATE login_state
               SET consumed_at = ?
             WHERE state = ?
               AND consumed_at IS NULL
               AND expires_at > ?
            RETURNING code_verifier, browser_binding, post_login_redirect, expires_at
            """;

    private final JdbcTemplate jdbcTemplate;

    private final PgLocalSessionStore.Clock clock;

    /** 过期行的物理保留窗口（expires_at + retention 之前不删）。 */
    private final Duration retention;

    /** 门禁绑定本适配器数据源（与其他写端口同判据）。 */
    private final PgTransactionGate gate;

    public PgLoginStateStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Instant::now, DEFAULT_RETENTION);
    }

    public PgLoginStateStore(JdbcTemplate jdbcTemplate, PgLocalSessionStore.Clock clock) {
        this(jdbcTemplate, clock, DEFAULT_RETENTION);
    }

    public PgLoginStateStore(JdbcTemplate jdbcTemplate, PgLocalSessionStore.Clock clock, Duration retention) {
        if (retention == null || retention.isNegative()) {
            throw new IllegalArgumentException("login_state retention 必须为非负时长（当前：" + retention + "）");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.retention = retention;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
    }

    @Override
    public void save(LoginStateEntry entry) {
        gate.requireActiveTransaction("save(login_state)");
        // 惰性回收（W22-RV-01）：同一 T-state 事务内清理"过期且越过保留窗口"的行
        //（含已消费行——删除后重放按 state-missing 拒绝，语义不变），随本次 INSERT 提交
        jdbcTemplate.update(PURGE_SQL, Timestamp.from(clock.now().minus(retention)));
        jdbcTemplate.update(INSERT_SQL, entry.state(), entry.codeVerifier(), entry.browserBinding(),
                entry.postLoginRedirect(), Timestamp.from(entry.expiresAt()), Timestamp.from(clock.now()));
    }

    @Override
    public Optional<LoginStateEntry> consume(String state) {
        gate.requireActiveTransaction("consume(login_state)");
        Instant now = clock.now();
        List<LoginStateEntry> consumed = jdbcTemplate.query(CONSUME_SQL, (rs, rowNum) -> new LoginStateEntry(
                state,
                rs.getString("code_verifier"),
                rs.getString("browser_binding"),
                rs.getString("post_login_redirect"),
                rs.getTimestamp("expires_at").toInstant()),
                Timestamp.from(now), state, Timestamp.from(now));
        // 条件更新保证至多一行（state 主键）；防御式取首个
        return consumed.isEmpty() ? Optional.empty() : Optional.of(consumed.get(0));
    }
}
