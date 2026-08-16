package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.RetryDisposition;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §8 幂等契约三分支 + AAC-16 完整性绑定 + 并发竞态恰一赢家 + TM-26
 * 语义级（commandId 不参与幂等判定：不同 businessCommandRef 同键同摘要
 * 仍返回首次稳定结果）。
 */
class InMemoryDedupStoreTest {

    private static final DedupKey KEY = new DedupKey("acme", "bop-2026-0001");

    private static BusinessActionReceipt receipt(String effectDigest, String commandRef) {
        return new BusinessActionReceipt(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, KEY.tenantId(),
                KEY.businessOperationId(), "ar.createCrmFollowup", "1", effectDigest,
                "https://fastddd.target.example", "fastddd-ar-service",
                "binding-2026-08-16-001", null, ActionOutcomeCode.ALREADY_COMPLETED,
                commandRef, "arFollowup/FOLLOW-001", "8",
                OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC),
                RetryDisposition.DO_NOT_RETRY, "trace-77", null);
    }

    @Test
    void emptyKeyCheckIsAbsent() {
        DedupCheck check = new InMemoryDedupStore().check(KEY, "sha256:a");
        assertInstanceOf(DedupCheck.Absent.class, check);
    }

    @Test
    void sameKeySameDigestLoadsStableResultWithoutRewrite() {
        InMemoryDedupStore store = new InMemoryDedupStore();
        BusinessActionReceipt first = receipt("sha256:a", "cmd-1");
        store.recordCompletion(new DedupCompletion(KEY, "sha256:a", first));

        DedupCheck.Loaded loaded = assertInstanceOf(DedupCheck.Loaded.class,
                store.check(KEY, "sha256:a"));
        assertSame(first, loaded.receipt(), "同键同摘要返回既有稳定结果（§8.2）");

        // 同键同摘要、不同 businessCommandRef 重复写入 → no-op，首次稳定结果保持权威
        // （TM-26 语义级：commandId 仅调查，不参与幂等判定）
        BusinessActionReceipt secondAttempt = receipt("sha256:a", "cmd-OTHER");
        assertDoesNotThrow(() -> store.recordCompletion(new DedupCompletion(KEY, "sha256:a", secondAttempt)));
        DedupCheck reloaded = store.check(KEY, "sha256:a");
        assertSame(first, assertInstanceOf(DedupCheck.Loaded.class, reloaded).receipt());
        assertEquals(1, store.size());
    }

    @Test
    void sameKeyDifferentDigestConflicts() {
        InMemoryDedupStore store = new InMemoryDedupStore();
        store.recordCompletion(new DedupCompletion(KEY, "sha256:a", receipt("sha256:a", "cmd-1")));

        DedupCheck.Conflict conflict = assertInstanceOf(DedupCheck.Conflict.class,
                store.check(KEY, "sha256:B"));
        assertEquals("sha256:a", conflict.existingEffectDigest(),
                "冲突分支只报告既有摘要，不回放既有 Receipt（§8.3）");

        // 并发/迟到写入异摘要 → 唯一约束拒绝（§8.1），不得覆盖既有记录
        IdempotencyConflictException e = assertThrows(IdempotencyConflictException.class,
                () -> store.recordCompletion(new DedupCompletion(KEY, "sha256:B", receipt("sha256:B", "cmd-2"))));
        assertAll("冲突异常携带完整性事件输入",
                () -> assertEquals(KEY, e.key()),
                () -> assertEquals("sha256:a", e.existingEffectDigest()),
                () -> assertEquals("sha256:B", e.attemptedEffectDigest()));
        assertEquals("sha256:a",
                assertInstanceOf(DedupCheck.Conflict.class, store.check(KEY, "sha256:B")).existingEffectDigest());
    }

    @Test
    void completionRejectsMisboundAndNonCommittedReceipts() {
        List<Runnable> invalid = List.of(
                // 完整性绑定失败：receipt 租户与 key 错位
                () -> new DedupCompletion(new DedupKey("acme", "bop-2026-0001"), "sha256:a",
                        new BusinessActionReceipt(
                                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "OTHER-TENANT",
                                "bop-2026-0001", "ar.createCrmFollowup", "1", "sha256:a",
                                "https://fastddd.target.example", "fastddd-ar-service",
                                "binding-x", null, ActionOutcomeCode.ALREADY_COMPLETED,
                                "cmd-1", "arFollowup/FOLLOW-001", null,
                                OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC),
                                RetryDisposition.DO_NOT_RETRY, "trace-77", null)),
                // receipt effectDigest 与比对字段错位
                () -> new DedupCompletion(KEY, "sha256:a", receipt("sha256:DIFFERENT", "cmd-1")),
                // 拒绝类不得写成功去重记录（§9 尾注）
                () -> new DedupCompletion(KEY, "sha256:a",
                        new BusinessActionReceipt(
                                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, KEY.tenantId(),
                                KEY.businessOperationId(), "ar.createCrmFollowup", "1", "sha256:a",
                                "https://fastddd.target.example", "fastddd-ar-service",
                                "binding-x", null, ActionOutcomeCode.BUSINESS_RULE_VIOLATION,
                                null, null, null, null,
                                RetryDisposition.DO_NOT_RETRY, "trace-77", null)));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run, "非法去重写入必须紧致校验拒绝");
        }
    }

    @Test
    void concurrentCompletionHasExactlyOneWinner() throws Exception {
        InMemoryDedupStore store = new InMemoryDedupStore();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> futures = new CopyOnWriteArrayList<>();
            for (int i = 0; i < threads; i++) {
                String digest = i % 2 == 0 ? "sha256:even" : "sha256:odd";
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        store.recordCompletion(new DedupCompletion(KEY, digest, receipt(digest, "cmd-" + digest)));
                    } catch (IdempotencyConflictException expectedRace) {
                        // 并发竞态的合法收敛：唯一约束被先占
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, store.size(), "恰一赢家占位二元键（§8.1 唯一约束）");
        DedupCheck after = store.check(KEY, "sha256:even");
        assertTrue(after instanceof DedupCheck.Loaded || after instanceof DedupCheck.Conflict,
                "收敛后对任一摘要只能是 Loaded 或 Conflict，不会 Absent");
    }
}
