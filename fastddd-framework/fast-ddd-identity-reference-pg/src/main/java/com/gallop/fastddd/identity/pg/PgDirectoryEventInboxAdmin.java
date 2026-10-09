package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.port.DirectoryEventInboxAdmin;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 事件收件箱管理 PG 参考适配器（W5 设计 §5.1 管理面，W5-d）：FAILED 终态
 * 显式重放——复位 RECEIVED、attempts 归零、租约列与 last_error 清空。
 *
 * <p><b>事务模型</b>：单条原子 UPDATE（态前置 {@code state = 'FAILED'} 限定，
 * rowcount 即裁决）——不依赖调用方事务；授权/审计归宿主管理端
 * （{@code IdentityAdminOperation.INBOX_REPLAY}，starter 层），本适配器
 * 只做存储面复位。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryEventInboxAdmin implements DirectoryEventInboxAdmin {

    private static final String REPLAY_SQL = """
            UPDATE directory_event_inbox
               SET state = 'RECEIVED', attempts = 0, last_error = NULL,
                   claimed_at = NULL, lease_expires_at = NULL
             WHERE event_id = ? AND state = 'FAILED'
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgDirectoryEventInboxAdmin(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean replay(String eventId) {
        return jdbcTemplate.update(REPLAY_SQL, eventId) > 0;
    }
}
