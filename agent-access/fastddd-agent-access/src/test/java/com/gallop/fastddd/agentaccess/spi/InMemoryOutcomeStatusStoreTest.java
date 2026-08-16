package com.gallop.fastddd.agentaccess.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 进程内记录参考实现：缺省 Absent、put/find 往返、键作用域、编程错误紧致拒绝。
 */
class InMemoryOutcomeStatusStoreTest {

    private static final DedupKey KEY = new DedupKey("acme", "bop-2026-0001");

    @Test
    void unknownKeyDefaultsToAbsent() {
        assertInstanceOf(OutcomeRecordView.Absent.class, new InMemoryOutcomeStatusStore().find(KEY));
    }

    @Test
    void putFindRoundTripsLatestView() {
        InMemoryOutcomeStatusStore store = new InMemoryOutcomeStatusStore();
        OutcomeRecordView.InFlight inFlight = new OutcomeRecordView.InFlight();
        store.put(KEY, inFlight);
        assertSame(inFlight, store.find(KEY));
        // 覆盖式写入：测试组装状态演进（类注已如实标注非追加写语义）
        OutcomeRecordView.Absent absent = new OutcomeRecordView.Absent();
        store.put(KEY, absent);
        assertSame(absent, store.find(KEY));
        // 键作用域：其他租户/其他操作不可达
        assertInstanceOf(OutcomeRecordView.Absent.class, store.find(new DedupKey("acme", "bop-OTHER")));
        assertInstanceOf(OutcomeRecordView.Absent.class, store.find(new DedupKey("OTHER", KEY.businessOperationId())));
    }

    @Test
    void nullArgumentsAreProgrammingErrors() {
        InMemoryOutcomeStatusStore store = new InMemoryOutcomeStatusStore();
        assertThrows(IllegalArgumentException.class, () -> store.find(null));
        assertThrows(IllegalArgumentException.class, () -> store.put(null, new OutcomeRecordView.InFlight()));
        assertThrows(IllegalArgumentException.class, () -> store.put(KEY, null));
    }
}
