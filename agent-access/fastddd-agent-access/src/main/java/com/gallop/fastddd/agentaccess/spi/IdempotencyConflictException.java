package com.gallop.fastddd.agentaccess.spi;

/**
 * 同键异摘要冲突（Contract §8.3）：二元键已被不同 {@code effectDigest}
 * 的稳定结果先占——唯一约束拒绝，不得覆盖既有记录。
 *
 * <p>调用方映射 {@code IDEMPOTENCY_CONFLICT} 外部结果码并产生完整性事件
 * （完整性告警/人工调查，§9 上游动作）；禁止改键重试或降级覆盖。</p>
 */
public class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DedupKey key;
    private final String existingEffectDigest;
    private final String attemptedEffectDigest;

    public IdempotencyConflictException(DedupKey key, String existingEffectDigest, String attemptedEffectDigest) {
        super(message(requireKey(key), requireDigest("existingEffectDigest", existingEffectDigest),
                requireDigest("attemptedEffectDigest", attemptedEffectDigest)));
        this.key = key;
        this.existingEffectDigest = existingEffectDigest;
        this.attemptedEffectDigest = attemptedEffectDigest;
    }

    private static DedupKey requireKey(DedupKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        return key;
    }

    private static String requireDigest(String field, String digest) {
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
        return digest;
    }

    private static String message(DedupKey key, String existingEffectDigest, String attemptedEffectDigest) {
        return "幂等键已绑定不同 effectDigest：key=" + key + "，既有=" + existingEffectDigest
                + "，尝试=" + attemptedEffectDigest;
    }

    public DedupKey key() {
        return key;
    }

    /** 既有稳定结果的 effectDigest（完整性事件与审计输入，不回放既有 Receipt）。 */
    public String existingEffectDigest() {
        return existingEffectDigest;
    }

    public String attemptedEffectDigest() {
        return attemptedEffectDigest;
    }
}
