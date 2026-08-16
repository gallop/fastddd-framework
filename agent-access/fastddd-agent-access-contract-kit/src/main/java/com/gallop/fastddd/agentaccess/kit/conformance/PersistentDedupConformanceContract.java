package com.gallop.fastddd.agentaccess.kit.conformance;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.RetryDisposition;
import com.gallop.fastddd.agentaccess.spi.DedupCheck;
import com.gallop.fastddd.agentaccess.spi.DedupCompletion;
import com.gallop.fastddd.agentaccess.spi.DedupKey;
import com.gallop.fastddd.agentaccess.spi.IdempotencyConflictException;
import com.gallop.fastddd.agentaccess.spi.PersistentDedupPort;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §8 持久幂等 conformance 抽象基类（FDD-AA-15；宿主在自身 test scope 继承，
 * 对自有真表运行——不是内存模拟）。
 *
 * <p><b>宿主必须实现</b>：{@link #inBusinessTransaction(TransactionalWork)}——
 * 在宿主真实业务事务边界内执行 work（聚合、去重记录与业务 Outbox 使用的
 * <b>同一</b> DataSource/TransactionManager，TM-14/§8.4）。该方法是本契约
 * 的核心：唯一约束、同事务可见性与回滚原子性只有在真实事务里才能证明。</p>
 *
 * <p><b>断言面</b>（对应 Contract §8.1-8.3 + TM-14 + TM-26 + AAC-16）：</p>
 * <ul>
 *   <li>同键同 {@code effectDigest} 返回既有稳定结果（§8.2）；</li>
 *   <li>同键异摘要：查询 {@code Conflict}、写入 {@link IdempotencyConflictException}
 *       且既有记录不被覆盖（§8.1/§8.3）；</li>
 *   <li>{@code commandId}/{@code businessCommandRef} 不参与幂等判定（TM-26）；</li>
 *   <li>同事务内写入对去重查询可见，回滚后聚合/去重/Outbox/审计全部无残留
 *       （TM-14 同事务原子性）；</li>
 *   <li>并发竞态恰一赢家（§8.1 持久唯一约束的真实性）；</li>
 *   <li>Receipt 完整性绑定负向（AAC-16：错位与拒绝类不得写成功去重）。</li>
 * </ul>
 *
 * <p><b>占位表说明</b>：本基类不感知表结构；参考实现使用
 * {@code fddaa_conformance_*} 显式占位表名。V04-09 冻结真表名后，宿主实现
 * 与参考执行须同名同义对齐（届时以勘误更新本注记，不改契约语义）。</p>
 */
public abstract class PersistentDedupConformanceContract {

    private static final String TENANT = "acme";
    private static final String BOP_ID = "bop-conformance-0001";

    /** 触发宿主事务回滚的标记异常（TM-14 回滚原子性测试用）。 */
    protected static final class Rollback extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** 在宿主业务事务内执行的工作单元（聚合/去重/Outbox 同一事务，TM-14）。 */
    @FunctionalInterface
    protected interface TransactionalWork {
        void run(PersistentDedupPort port) throws Exception;
    }

    /**
     * 在宿主真实业务事务边界内执行 work：work 正常返回即提交；抛出
     * {@link Rollback} 即回滚且视为预期路径；其他异常回滚后向上传播。
     */
    protected abstract void inBusinessTransaction(TransactionalWork work) throws Exception;

    /**
     * 同事务聚合伴写（缺省 no-op；有真实聚合表的宿主覆盖以参与回滚原子性断言）。
     * 调用上下文在 {@link #inBusinessTransaction} 事务内。
     */
    protected void writeAggregateSideEffect(PersistentDedupPort sameTxPort, DedupKey key) throws Exception {
    }

    /**
     * 同事务 Outbox 伴写（缺省 no-op；同 {@link #writeAggregateSideEffect}）。
     */
    protected void writeOutboxSideEffect(PersistentDedupPort sameTxPort, DedupKey key) throws Exception {
    }

    /** 回滚后聚合伴写应缺席（缺省 false=未实现该面；有聚合面的宿主覆盖为真实查询）。 */
    protected boolean aggregateSideEffectPresent(DedupKey key) throws Exception {
        return false;
    }

    /** 回滚后 Outbox 伴写应缺席（缺省 false=未实现该面）。 */
    protected boolean outboxSideEffectPresent(DedupKey key) throws Exception {
        return false;
    }

    /** 回滚后审计行应缺席（缺省 false=未实现该面；TM-14 断言审计不残留）。 */
    protected boolean auditSideEffectPresent(DedupKey key) throws Exception {
        return false;
    }

    /** 唯一化测试键（并发/隔离场景避免串扰）。 */
    protected DedupKey freshKey(String suffix) {
        return new DedupKey(TENANT, BOP_ID + "-" + suffix);
    }

    /** 已提交稳定结果 fixture（businessCommandRef 可变——TM-26 断言输入）。 */
    protected BusinessActionReceipt committedReceipt(DedupKey key, String effectDigest, String commandRef) {
        return new BusinessActionReceipt(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, key.tenantId(),
                key.businessOperationId(), "ar.createCrmFollowup", "1", effectDigest,
                "https://fastddd.target.example", "fastddd-ar-service",
                "binding-conformance-001", null, ActionOutcomeCode.ALREADY_COMPLETED,
                commandRef, "arFollowup/FOLLOW-001", "8",
                OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC),
                RetryDisposition.DO_NOT_RETRY, "trace-conformance", null);
    }

    @Test
    void sameKeySameDigestReturnsStableResult() throws Exception {
        DedupKey key = freshKey("stable");
        BusinessActionReceipt first = committedReceipt(key, "sha256:a", "cmd-1");
        inBusinessTransaction(port -> port.recordCompletion(new DedupCompletion(key, "sha256:a", first)));
        inBusinessTransaction(port -> {
            DedupCheck.Loaded loaded = assertInstanceOf(DedupCheck.Loaded.class, port.check(key, "sha256:a"));
            assertTrue(loaded.receipt().businessCommandRef().equals("cmd-1"),
                    "同键同摘要返回既有稳定结果（§8.2），不重复修改");
        });
    }

    @Test
    void sameKeyDifferentDigestConflictsAndNeverOverwrites() throws Exception {
        DedupKey key = freshKey("conflict");
        inBusinessTransaction(port ->
                port.recordCompletion(new DedupCompletion(key, "sha256:a", committedReceipt(key, "sha256:a", "cmd-1"))));
        // 断言在事务内捕获 ICE：宿主实现须用 savepoint（或等价机制）隔离唯一约束
        // 冲突，不得让失败语句毒化整个事务（PostgreSQL 语句级中止语义）
        inBusinessTransaction(port -> {
            DedupCheck.Conflict conflict = assertInstanceOf(DedupCheck.Conflict.class, port.check(key, "sha256:B"));
            assertTrue("sha256:a".equals(conflict.existingEffectDigest()),
                    "冲突分支只报告既有摘要，不回放既有 Receipt（§8.3）");
            IdempotencyConflictException rejected = assertThrows(IdempotencyConflictException.class,
                    () -> port.recordCompletion(new DedupCompletion(key, "sha256:B",
                            committedReceipt(key, "sha256:B", "cmd-2"))));
            assertTrue(rejected.existingEffectDigest().equals("sha256:a")
                    && rejected.attemptedEffectDigest().equals("sha256:B"),
                    "冲突异常携带完整性事件输入（§8.3）");
            assertInstanceOf(DedupCheck.Conflict.class, port.check(key, "sha256:B"),
                    "既有记录不被覆盖（§8.1 唯一约束拒绝）");
        });
    }

    @Test
    void commandIdIsNotPartOfIdempotencyKey() throws Exception {
        // TM-26 语义级：commandId 仅调查，不参与幂等判定——同键同摘要、不同
        // businessCommandRef 的重复写入是 no-op，首次稳定结果保持权威
        DedupKey key = freshKey("tm26");
        BusinessActionReceipt first = committedReceipt(key, "sha256:same", "cmd-FIRST");
        inBusinessTransaction(port -> port.recordCompletion(new DedupCompletion(key, "sha256:same", first)));
        inBusinessTransaction(port -> {
            assertDoesNotThrow(() -> port.recordCompletion(new DedupCompletion(key, "sha256:same",
                    committedReceipt(key, "sha256:same", "cmd-OTHER"))));
            DedupCheck.Loaded loaded = assertInstanceOf(DedupCheck.Loaded.class, port.check(key, "sha256:same"));
            assertTrue("cmd-FIRST".equals(loaded.receipt().businessCommandRef()),
                    "首次稳定结果保持权威，后到 commandRef 不覆盖");
        });
    }

    @Test
    void sameTransactionWriteIsVisibleToDedupQuery() throws Exception {
        // TM-14：同事务内（未提交）写入对去重查询可见——若去重走了独立存储/事务，
        // 该查询只能看到 Absent，本断言即失败
        DedupKey key = freshKey("tm14-visible");
        inBusinessTransaction(port -> {
            port.recordCompletion(new DedupCompletion(key, "sha256:tm14",
                    committedReceipt(key, "sha256:tm14", "cmd-tm14")));
            DedupCheck inFlight = port.check(key, "sha256:tm14");
            assertTrue(inFlight instanceof DedupCheck.Loaded,
                    "同事务内写入必须对本事务查询可见（TM-14 同 DataSource/TransactionManager）");
        });
    }

    @Test
    void rolledBackTransactionLeavesNoSideEffects() throws Exception {
        // TM-14 原子性：聚合/去重/Outbox/审计同事务——回滚后全部无残留
        DedupKey key = freshKey("tm14-rollback");
        inBusinessTransaction(port -> {
            port.recordCompletion(new DedupCompletion(key, "sha256:rb",
                    committedReceipt(key, "sha256:rb", "cmd-rb")));
            writeAggregateSideEffect(port, key);
            writeOutboxSideEffect(port, key);
            throw new Rollback();
        });
        inBusinessTransaction(port ->
                assertInstanceOf(DedupCheck.Absent.class, port.check(key, "sha256:rb"),
                        "回滚后去重记录不得残留"));
        assertTrue(!aggregateSideEffectPresent(key), "回滚后聚合伴写不得残留");
        assertTrue(!outboxSideEffectPresent(key), "回滚后 Outbox 伴写不得残留");
        assertTrue(!auditSideEffectPresent(key), "回滚后审计行不得残留（审计与业务同事务回滚）");
    }

    @Test
    void concurrentCompletionHasExactlyOneWinner() throws Exception {
        DedupKey key = freshKey("race");
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> futures = new CopyOnWriteArrayList<>();
            for (int i = 0; i < threads; i++) {
                String digest = i % 2 == 0 ? "sha256:even" : "sha256:odd";
                String commandRef = "cmd-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        inBusinessTransaction(port -> port.recordCompletion(
                                new DedupCompletion(key, digest, committedReceipt(key, digest, commandRef))));
                    } catch (IdempotencyConflictException expectedRace) {
                        // 并发竞态的合法收敛：持久唯一约束被先占
                    } catch (Exception propagate) {
                        throw new RuntimeException(propagate);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        inBusinessTransaction(port -> {
            DedupCheck even = port.check(key, "sha256:even");
            DedupCheck odd = port.check(key, "sha256:odd");
            boolean exactlyOneLoaded = (even instanceof DedupCheck.Loaded) ^ (odd instanceof DedupCheck.Loaded);
            assertTrue(exactlyOneLoaded, "并发恰一赢家（§8.1 持久唯一约束）；实际 even=" + even + " odd=" + odd);
        });
    }

    @Test
    void misboundCompletionIsRejectedAtConstruction() {
        // AAC-16 完整性绑定负向：类型层已保证（DedupCompletion 紧致校验），
        // 宿主实现不得绕开该类型自建写入路径
        DedupKey key = freshKey("aac16");
        assertThrows(IllegalArgumentException.class, () -> new DedupCompletion(key, "sha256:a",
                committedReceipt(new DedupKey("OTHER-TENANT", BOP_ID), "sha256:a", "cmd-1")));
        assertThrows(IllegalArgumentException.class, () -> new DedupCompletion(key, "sha256:a",
                committedReceipt(key, "sha256:MISMATCH", "cmd-1")));
        assertThrows(IllegalArgumentException.class, () -> new DedupCompletion(key, "sha256:a",
                new BusinessActionReceipt(
                        AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, key.tenantId(),
                        key.businessOperationId(), "ar.createCrmFollowup", "1", "sha256:a",
                        "https://fastddd.target.example", "fastddd-ar-service",
                        "binding-conformance-001", null, ActionOutcomeCode.BUSINESS_RULE_VIOLATION,
                        null, null, null, null,
                        RetryDisposition.DO_NOT_RETRY, "trace-conformance", null)),
                "拒绝类结果不得写成功去重记录（§9 尾注）");
    }
}
