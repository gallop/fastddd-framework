package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5-d 收件箱管理端口 PG 验收测试（W5 设计 §5.1 管理面）：FAILED 显式重放 =
 * 原子 UPDATE 复位（RECEIVED / attempts=0 / 错误与租约清空）；不存在与非
 * FAILED 态（RECEIVED/PROCESSING/PROCESSED）幂等拒绝（false——"无可重放"
 * 终局语义，控制器按 404 处置）；重放后事件回到可认领队列。
 */
class PgDirectoryEventInboxAdminTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-replay", "app");

    private DataSource dataSource;
    private PgDirectoryEventInbox inbox;
    private PgDirectoryEventInboxAdmin admin;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        inbox = new PgDirectoryEventInbox(new JdbcTemplate(dataSource));
        admin = new PgDirectoryEventInboxAdmin(new JdbcTemplate(dataSource));
        jdbc = new JdbcTemplate(dataSource);
    }

    private void persist(String eventId) {
        assertThat(inbox.persist(EventEnvelope.received(eventId, SCOPE, "contact.user.updated_v3",
                "{\"v\":1}", Instant.parse("2026-10-09T08:00:00Z")))).isTrue();
    }

    /** 经 API 制造 FAILED 终态：maxAttempts=1 的收件箱认领后失败一次即翻终态。 */
    private void failEvent(String eventId) {
        PgDirectoryEventInbox limited = new PgDirectoryEventInbox(new JdbcTemplate(dataSource), 1);
        limited.claim(1, Duration.ofSeconds(60));
        limited.markFailed(eventId, "recheck-unavailable:timeout");
    }

    @Test
    void replayNonexistentEventReturnsFalse() {
        assertThat(admin.replay("evt-no-such")).isFalse();
    }

    @Test
    void replayNonFailedStatesAreIdempotentlyRejected() {
        // 同刻入箱：claim 按 (received_at, event_id) 序认领前两行
        // （evt-processed < evt-processing < evt-received），evt-received 留在 RECEIVED
        persist("evt-received");
        persist("evt-processing");
        persist("evt-processed");
        inbox.claim(2, Duration.ofSeconds(60));
        inbox.markProcessed("evt-processed");

        assertThat(admin.replay("evt-received")).isFalse();
        assertThat(admin.replay("evt-processing")).isFalse();
        assertThat(admin.replay("evt-processed")).isFalse();
        // 零状态扰动：三行状态保持原样
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_event_inbox WHERE event_id='evt-received' AND state='RECEIVED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void replayFailedResetsToReceivedWithZeroAttemptsAndClearedLease() {
        persist("evt-failed");
        failEvent("evt-failed");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM directory_event_inbox WHERE event_id='evt-failed'",
                String.class)).isEqualTo("FAILED");

        assertThat(admin.replay("evt-failed")).isTrue();

        var row = jdbc.queryForMap("SELECT state, attempts, last_error, claimed_at, lease_expires_at "
                + "FROM directory_event_inbox WHERE event_id='evt-failed'");
        assertThat(row.get("state")).isEqualTo("RECEIVED");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("last_error")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("lease_expires_at")).isNull();

        // 重放后回到可认领队列；且从 RECEIVED 再 replay = 幂等拒绝（仅 FAILED 态可重放）
        assertThat(inbox.claim(10, Duration.ofSeconds(60)))
                .extracting(EventEnvelope::eventId).containsExactly("evt-failed");
        assertThat(admin.replay("evt-failed")).isFalse();
    }
}
