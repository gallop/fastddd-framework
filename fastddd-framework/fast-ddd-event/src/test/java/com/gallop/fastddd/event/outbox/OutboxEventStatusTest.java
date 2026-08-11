package com.gallop.fastddd.event.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox 事件状态枚举单测（EVT-002 步骤3：状态枚举统一）。
 *
 * <p>验证 DEAD_LETTER 终态加入后的 {@link OutboxEventStatus#isFinalState()} /
 * {@link OutboxEventStatus#canRetry()} 语义。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
class OutboxEventStatusTest {

    @Test
    void deadLetter_isFinalState() {
        assertTrue(OutboxEventStatus.DEAD_LETTER.isFinalState(),
                "DEAD_LETTER 应为终态");
    }

    @Test
    void deadLetter_cannotRetry() {
        assertFalse(OutboxEventStatus.DEAD_LETTER.canRetry(),
                "DEAD_LETTER 不应允许重试");
    }

    @Test
    void allTerminalStates_areFinal() {
        assertTrue(OutboxEventStatus.PUBLISHED.isFinalState());
        assertTrue(OutboxEventStatus.CANCELLED.isFinalState());
        assertTrue(OutboxEventStatus.DEAD_LETTER.isFinalState());
    }

    @Test
    void nonTerminalStates_areRetriableOrTransient() {
        // PENDING / FAILED 可重试；PROCESSING 为瞬态（非终态、非可重试）
        assertTrue(OutboxEventStatus.PENDING.canRetry());
        assertTrue(OutboxEventStatus.FAILED.canRetry());
        assertFalse(OutboxEventStatus.PROCESSING.isFinalState());
        assertFalse(OutboxEventStatus.PROCESSING.canRetry());
    }

    @Test
    void sixStatusValues_present() {
        assertEquals(6, OutboxEventStatus.values().length,
                "应有 6 个状态值（含 DEAD_LETTER）: "
                        + java.util.Arrays.toString(OutboxEventStatus.values()));
    }
}
