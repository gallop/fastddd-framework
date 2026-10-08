package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 入站事件信封（W5 设计 §4.1/§5.1，W5-b 新增）：飞书事件订阅
 * （验签 → 解密 → 防重放通过后）的持久化载体行形态——先落 Inbox 后 ACK。
 *
 * <p>幂等键 = {@code eventId}（V4 主键）。消费状态机：
 * RECEIVED →（claim 置位租约 claimed_at/lease_expires_at）PROCESSING →
 * PROCESSED / FAILED；崩溃恢复 = 过期 PROCESSING 行复位 RECEIVED、attempts+1；
 * attempts ≥ 上限 → FAILED 终态（不再自动重试，经管理端显式重放入口）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class EventEnvelope {

    /** 消费状态（V4 state 列 CHECK 值域）。 */
    public enum State {
        RECEIVED,
        PROCESSING,
        PROCESSED,
        /** 终态：attempts 耗尽，仅管理端显式重放入口。 */
        FAILED
    }

    private final String eventId;
    private final DirectoryScope scope;
    private final String eventType;
    private final String payload;
    private final Instant receivedAt;
    private final State state;
    private final int attempts;
    private final String lastError;
    private final Instant processedAt;
    private final Instant claimedAt;
    private final Instant leaseExpiresAt;

    /** 全字段构造（存储实现读出行/测试用；写入侧走 {@link #received}）。 */
    public EventEnvelope(String eventId,
                         DirectoryScope scope,
                         String eventType,
                         String payload,
                         Instant receivedAt,
                         State state,
                         int attempts,
                         String lastError,
                         Instant processedAt,
                         Instant claimedAt,
                         Instant leaseExpiresAt) {
        this.eventId = requireNonBlank(eventId, "eventId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.state = Objects.requireNonNull(state, "state");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts 不能为负数（收到：" + attempts + "）");
        }
        this.attempts = attempts;
        this.lastError = lastError;
        this.processedAt = processedAt;
        this.claimedAt = claimedAt;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    /** 接收侧工厂：state=RECEIVED、attempts=0、无租约/处理时间戳。 */
    public static EventEnvelope received(String eventId,
                                         DirectoryScope scope,
                                         String eventType,
                                         String payload,
                                         Instant receivedAt) {
        return new EventEnvelope(eventId, scope, eventType, payload, receivedAt,
                State.RECEIVED, 0, null, null, null, null);
    }

    public String eventId() {
        return eventId;
    }

    public DirectoryScope scope() {
        return scope;
    }

    public String eventType() {
        return eventType;
    }

    /** 原始解密后事件体（JSON 文本；JSONB 列承载）。 */
    public String payload() {
        return payload;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public State state() {
        return state;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    public Instant processedAt() {
        return processedAt;
    }

    public Instant claimedAt() {
        return claimedAt;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EventEnvelope)) {
            return false;
        }
        EventEnvelope that = (EventEnvelope) o;
        return attempts == that.attempts
                && eventId.equals(that.eventId)
                && scope.equals(that.scope)
                && eventType.equals(that.eventType)
                && payload.equals(that.payload)
                && receivedAt.equals(that.receivedAt)
                && state == that.state
                && Objects.equals(lastError, that.lastError)
                && Objects.equals(processedAt, that.processedAt)
                && Objects.equals(claimedAt, that.claimedAt)
                && Objects.equals(leaseExpiresAt, that.leaseExpiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, scope, eventType, payload, receivedAt,
                state, attempts, lastError, processedAt, claimedAt, leaseExpiresAt);
    }

    @Override
    public String toString() {
        return "EventEnvelope[eventId=" + eventId + ", scope=" + scope
                + ", eventType=" + eventType + ", state=" + state
                + ", attempts=" + attempts + ", receivedAt=" + receivedAt + "]";
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EventEnvelope." + name + " 不能为空");
        }
        return value;
    }
}
