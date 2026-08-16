package com.gallop.fastddd.agentaccess.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已验证本地决定缓存（PDP 失效时的<b>唯一</b>允许降级路径）。
 *
 * <p>降级三要素缺一不可（{@link #isStillApplicable}）：</p>
 * <ol>
 *   <li>policyVersion 与当前策略版本一致（禁止跨版本复用缓存决定）；</li>
 *   <li>TTL 未过（禁止无限期缓存；TTL 在条目构造时强制必选正数）；</li>
 *   <li>EntitlementRevision 未漂移（预检基准仍在）。</li>
 * </ol>
 *
 * <p>任何要素不满足即按新决策失败处理（fail-closed），不得沿用缓存放行。
 * 缓存条目的写入应发生在 PDP 可用且决定经验证之后；本类不提供
 * 「失败后回填」路径。</p>
 */
public final class LocalDecisionCache {

    private final Map<String, CachedDecision> store = new ConcurrentHashMap<>();

    /** 已验证本地决定条目（三要素 + TTL）。 */
    public record CachedDecision(
            AuthorizationDecision decision,
            String policyVersion,
            String decisionDigest,
            EntitlementRevision revision,
            Instant cachedAt,
            Duration ttl) {

        public CachedDecision {
            if (decision == null) {
                throw new IllegalArgumentException("decision 必选");
            }
            if (policyVersion == null || policyVersion.isBlank()) {
                throw new IllegalArgumentException("policyVersion 必选且不得为空白");
            }
            if (decisionDigest == null || decisionDigest.isBlank()) {
                throw new IllegalArgumentException("decisionDigest 必选且不得为空白");
            }
            if (revision == null) {
                throw new IllegalArgumentException("revision 必选");
            }
            if (cachedAt == null) {
                throw new IllegalArgumentException("cachedAt 必选");
            }
            if (ttl == null) {
                throw new IllegalArgumentException("ttl 必选：禁止无限期缓存");
            }
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl 必须为正数");
            }
            // 决定自带版本/摘要与条目级字段必须一致：禁止「决定属于 pv-9、
            // 条目登记 pv-1」的错位缓存弱化跨版本禁用三要素
            if (decision.policyVersion() != null && !decision.policyVersion().equals(policyVersion)) {
                throw new IllegalArgumentException(
                        "decision.policyVersion 与条目 policyVersion 不一致：" + decision.policyVersion());
            }
            if (decision.decisionDigest() != null && !decision.decisionDigest().equals(decisionDigest)) {
                throw new IllegalArgumentException(
                        "decision.decisionDigest 与条目 decisionDigest 不一致");
            }
        }
    }

    /** 写入已验证决定（覆盖同 key 旧条目；key 由调用方以 tenant/action/resource 构造）。 */
    public void put(String key, CachedDecision cached) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        if (cached == null) {
            throw new IllegalArgumentException("cached 必选");
        }
        store.put(key, cached);
    }

    /** 读取缓存条目（存在即返回；可用性判定必须再经 {@link #isStillApplicable}）。 */
    public Optional<CachedDecision> get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        return Optional.ofNullable(store.get(key));
    }

    /**
     * 降级三要素校验：当前时刻在 TTL 窗口内、策略版本一致、修订未漂移。
     * 任一不满足返回 false（fail-closed：不可用即重新走实时决策，决策源
     * 失效时按 INDETERMINATE 拒绝，不沿用缓存）。
     */
    public static boolean isStillApplicable(CachedDecision cached, Instant now,
                                            String currentPolicyVersion,
                                            EntitlementRevision currentRevision) {
        if (cached == null) {
            throw new IllegalArgumentException("cached 必选");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 必选");
        }
        if (currentPolicyVersion == null || currentRevision == null) {
            return false;
        }
        if (now.isAfter(cached.cachedAt().plus(cached.ttl()))) {
            return false;
        }
        return cached.policyVersion().equals(currentPolicyVersion)
                && !cached.revision().driftedFrom(currentRevision);
    }
}
