package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PersistentDedupPort} 进程内参考实现（测试与 conformance 基底；
 * 与 {@code StaticDescriptorRegistry}/{@code LocalDecisionCache} 同定位）。
 *
 * <p><b>如实标注</b>：进程内 Map 不满足 §8.1 的持久唯一约束语义，也不满足
 * TM-14 同 DataSource/TransactionManager 要求——kill/restart 后不可恢复，
 * 不能当作 durable store。真实实现由宿主对自有表提供（V04-09 真表同名同义
 * 对齐），kit conformance fixture 断言其语义。</p>
 *
 * <p>{@code recordCompletion} 以二进制键槽位 {@code compute} 原子执行
 * 「同摘要 no-op / 异摘要冲突」，并发竞态下恰一赢家。</p>
 */
public final class InMemoryDedupStore implements PersistentDedupPort {

    private final Map<DedupKey, BusinessActionReceipt> stableResults = new ConcurrentHashMap<>();

    @Override
    public DedupCheck check(DedupKey key, String effectDigest) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        if (effectDigest == null || effectDigest.isBlank()) {
            throw new IllegalArgumentException("effectDigest 必选且不得为空白");
        }
        BusinessActionReceipt existing = stableResults.get(key);
        if (existing == null) {
            return new DedupCheck.Absent();
        }
        if (existing.effectDigest().equals(effectDigest)) {
            return new DedupCheck.Loaded(existing);
        }
        return new DedupCheck.Conflict(existing.effectDigest());
    }

    @Override
    public void recordCompletion(DedupCompletion completion) {
        if (completion == null) {
            throw new IllegalArgumentException("completion 必选");
        }
        // compute 原子占位：同摘要 no-op（首次稳定结果保持权威）/ 异摘要冲突
        stableResults.compute(completion.key(), (key, existing) -> {
            if (existing != null && !existing.effectDigest().equals(completion.effectDigest())) {
                throw new IdempotencyConflictException(key, existing.effectDigest(),
                        completion.effectDigest());
            }
            return existing != null ? existing : completion.receipt();
        });
    }

    /** 当前稳定结果数（测试/观测用）。 */
    public int size() {
        return stableResults.size();
    }
}
