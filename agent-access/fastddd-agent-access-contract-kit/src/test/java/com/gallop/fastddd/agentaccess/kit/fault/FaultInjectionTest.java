package com.gallop.fastddd.agentaccess.kit.fault;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AST-01 故障注入工具：alwaysFails/failsAfter 计数语义与编程错误紧致拒绝。
 */
class FaultInjectionTest {

    @Test
    void alwaysFailsThrowsGivenFailureEveryCall() {
        IllegalStateException timeout = new IllegalStateException("simulated timeout");
        Supplier<String> failing = FaultInjection.alwaysFails(timeout);
        for (int i = 0; i < 3; i++) {
            assertSame(timeout, assertThrows(IllegalStateException.class, failing::get));
        }
    }

    @Test
    void failsAfterDelegatesThenFailsAndNeverRecovers() {
        RuntimeException dbGone = new IllegalStateException("db gone");
        Supplier<Integer> flaky = FaultInjection.failsAfter(2, () -> 42, dbGone);
        assertEquals(42, flaky.get());
        assertEquals(42, flaky.get());
        assertSame(dbGone, assertThrows(IllegalStateException.class, flaky::get));
        // 断开后不自动恢复（fail-closed 场景要求故障持续到编排收敛）
        assertSame(dbGone, assertThrows(IllegalStateException.class, flaky::get));
    }

    @Test
    void failsAfterZeroFailsImmediately() {
        Supplier<Integer> immediate = FaultInjection.failsAfter(0, () -> 42,
                new IllegalStateException("already down"));
        assertThrows(IllegalStateException.class, immediate::get);
    }

    @Test
    void nullFailureOrNegativeCountOrMissingDelegateAreProgrammingErrors() {
        assertThrows(IllegalArgumentException.class, () -> FaultInjection.alwaysFails(null));
        assertThrows(IllegalArgumentException.class,
                () -> FaultInjection.failsAfter(1, () -> 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> FaultInjection.failsAfter(-1, () -> 1, new IllegalStateException("x")));
        assertThrows(IllegalArgumentException.class,
                () -> FaultInjection.failsAfter(1, null, new IllegalStateException("x")));
        assertDoesNotThrow(() -> FaultInjection.failsAfter(0, () -> 1,
                new IllegalStateException("x")));
    }
}
