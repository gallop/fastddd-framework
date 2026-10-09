package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-c 入站事件收件箱 PG 验收测试（W5 设计 §4.1/§5.1；每方法单语句原子，
 * at-least-once + attempts 上限 + 租约回收）。
 */
class PgDirectoryEventInboxTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-inbox", "app");

    private DataSource dataSource;
    private PgDirectoryEventInbox inbox;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        inbox = new PgDirectoryEventInbox(new JdbcTemplate(dataSource));
        jdbc = new JdbcTemplate(dataSource);
    }

    private EventEnvelope envelope(String eventId, String eventType, Instant receivedAt) {
        return EventEnvelope.received(eventId, SCOPE, eventType,
                "{\"v\":1}", receivedAt);
    }

    private EventEnvelope envelope(String eventId, String eventType) {
        return envelope(eventId, eventType, Instant.parse("2026-10-09T08:00:00Z"));
    }

    @Test
    void persistIsIdempotentOnEventIdPrimaryKey() {
        assertThat(inbox.persist(envelope("evt-1", "contact.user.created_v3"))).isTrue();
        assertThat(inbox.persist(envelope("evt-1", "contact.user.created_v3"))).isFalse();
        assertThat(Support.count(dataSource, "directory_event_inbox")).isEqualTo(1);
    }

    @Test
    void claimSetsProcessingWithLeaseAndRespectsLimitByReceivedOrder() {
        inbox.persist(envelope("evt-1", "t1", Instant.parse("2026-10-09T08:00:00Z")));
        inbox.persist(envelope("evt-2", "t2", Instant.parse("2026-10-09T08:00:01Z")));
        inbox.persist(envelope("evt-3", "t3", Instant.parse("2026-10-09T08:00:02Z")));

        List<EventEnvelope> claimed = inbox.claim(2, Duration.ofSeconds(60));
        // RETURNING 行序非契约面（择取集合才是）：同刻并列已由递增 receivedAt 消除
        assertThat(claimed).extracting(EventEnvelope::eventId)
                .containsExactlyInAnyOrder("evt-1", "evt-2");
        assertThat(claimed).allMatch(e -> e.state() == EventEnvelope.State.PROCESSING);
        assertThat(claimed).allMatch(e -> e.claimedAt() != null && e.leaseExpiresAt() != null);

        // RECEIVED 剩 evt-3；PROCESSING 行不被二次认领
        assertThat(inbox.claim(10, Duration.ofSeconds(60)))
                .extracting(EventEnvelope::eventId).containsExactly("evt-3");
        assertThat(inbox.claim(10, Duration.ofSeconds(60))).isEmpty();
    }

    @Test
    void markProcessedClearsLeaseAndIsIdempotentAfterTerminal() {
        inbox.persist(envelope("evt-1", "t1"));
        inbox.claim(1, Duration.ofSeconds(60));
        inbox.markProcessed("evt-1");
        var row = jdbc.queryForMap("SELECT state, processed_at, claimed_at, lease_expires_at FROM directory_event_inbox WHERE event_id = 'evt-1'");
        assertThat(row.get("state")).isEqualTo("PROCESSED");
        assertThat(row.get("processed_at")).isNotNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("lease_expires_at")).isNull();

        inbox.markProcessed("evt-1"); // 终态幂等空操作
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_event_inbox WHERE event_id='evt-1' AND state='PROCESSED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void markFailedIncrementsAttemptsAndFlipsToFailedAtLimit() {
        inbox.persist(envelope("evt-1", "t1"));
        PgDirectoryEventInbox limited = new PgDirectoryEventInbox(new JdbcTemplate(dataSource), 2);
        limited.claim(1, Duration.ofSeconds(60));
        limited.markFailed("evt-1", "首次失败"); // attempts 0→1 → RECEIVED 重试
        var afterFirst = jdbc.queryForMap("SELECT state, attempts, last_error FROM directory_event_inbox WHERE event_id='evt-1'");
        assertThat(afterFirst.get("state")).isEqualTo("RECEIVED");
        assertThat(afterFirst.get("attempts")).isEqualTo(1);
        assertThat(String.valueOf(afterFirst.get("last_error"))).isEqualTo("首次失败");

        limited.claim(1, Duration.ofSeconds(60));
        limited.markFailed("evt-1", "x".repeat(300)); // attempts 1→2 → FAILED 终态 + 截断 255
        var afterSecond = jdbc.queryForMap("SELECT state, attempts, last_error FROM directory_event_inbox WHERE event_id='evt-1'");
        assertThat(afterSecond.get("state")).isEqualTo("FAILED");
        assertThat(afterSecond.get("attempts")).isEqualTo(2);
        assertThat(String.valueOf(afterSecond.get("last_error"))).hasSize(255);
    }

    @Test
    void reclaimExpiredResetsLeaseLostRowsAndFailsAtLimit() {
        inbox.persist(envelope("evt-1", "t1"));
        inbox.persist(envelope("evt-2", "t2"));
        inbox.claim(2, Duration.ofSeconds(60));
        // 直接回拨租约到期时间，模拟处理器崩溃后的回收时点（时钟单源 PG now()）
        jdbc.update("UPDATE directory_event_inbox SET lease_expires_at = now() - interval '1 second'");

        assertThat(inbox.reclaimExpired()).isEqualTo(2); // 两行复位 RECEIVED
        var rows = jdbc.queryForList(
                "SELECT event_id, state, attempts, last_error, claimed_at FROM directory_event_inbox ORDER BY event_id");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("state")).isEqualTo("RECEIVED");
            assertThat(row.get("attempts")).isEqualTo(1);
            assertThat(String.valueOf(row.get("last_error"))).isEqualTo("lease-expired");
            assertThat(row.get("claimed_at")).isNull();
        });

        // 达上限：maxAttempts=2 时再回收一轮 → FAILED
        PgDirectoryEventInbox limited = new PgDirectoryEventInbox(new JdbcTemplate(dataSource), 2);
        limited.claim(2, Duration.ofSeconds(60));
        jdbc.update("UPDATE directory_event_inbox SET lease_expires_at = now() - interval '1 second'");
        assertThat(limited.reclaimExpired()).isEqualTo(0); // 复位为 FAILED，非 RECEIVED
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_event_inbox WHERE state='FAILED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void claimRejectsInvalidArguments() {
        assertThatThrownBy(() -> inbox.claim(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PgDirectoryEventInbox(new JdbcTemplate(dataSource), 0)) // maxAttempts ≥ 1
                .isInstanceOf(IllegalArgumentException.class);
    }
}
