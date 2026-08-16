package com.gallop.fastddd.agentaccess.spi;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalDecisionCache} 降级三要素负向测试（PDP 失效唯一允许降级路径：
 * policyVersion 一致 + TTL 未过 + revision 未漂移，缺一不可 fail-closed）。
 *
 * <p><b>AAC-15 验收层级如实标注</b>：本测试证明 revision 漂移使缓存决定失效的
 * <i>值语义</i>；「预检后撤 ACL → 事务内终检拒绝 + 漂移记录落到业务表」的完整
 * 验收需要 FDD-AA-12 事务编排接线，不由本测试代签。</p>
 */
class LocalDecisionCacheTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");

    private static LocalDecisionCache.CachedDecision cached(EntitlementRevision revision) {
        return new LocalDecisionCache.CachedDecision(
                new AuthorizationDecision(DecisionKind.PERMIT, "pv-1", "digest-1",
                        Set.of("ar.dunning.send"), List.of(), null),
                "pv-1",
                "digest-1",
                revision,
                NOW.minus(Duration.ofMinutes(1)),
                Duration.ofMinutes(5));
    }

    @Test
    void allThreeFactorsHoldingKeepsDecisionApplicable() {
        assertTrue(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW, "pv-1", new EntitlementRevision(7)));
    }

    @Test
    void policyVersionDriftInvalidatesCache() {
        assertFalse(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW, "pv-2", new EntitlementRevision(7)));
    }

    @Test
    void ttlExpiryInvalidatesCache() {
        // cachedAt + ttl = NOW + 4min；晚于该时刻一律失效
        assertFalse(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW.plus(Duration.ofMinutes(5)),
                "pv-1", new EntitlementRevision(7)));
    }

    @Test
    void revisionDriftInvalidatesCache() {
        // AAC-15 语义级：预检后授权事实修订（撤 ACL/改归属）→ 缓存决定不可再用
        assertFalse(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW, "pv-1", new EntitlementRevision(8)));
    }

    @Test
    void missingCurrentFactFailsClosed() {
        // 当前策略版本或修订号不可证明（null）→ 不可用，不得沿用缓存放行
        assertFalse(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW, null, new EntitlementRevision(7)));
        assertFalse(LocalDecisionCache.isStillApplicable(
                cached(new EntitlementRevision(7)), NOW, "pv-1", null));
    }

    @Test
    void cachedDecisionConstructionRequiresAllFactors() {
        LocalDecisionCache.CachedDecision valid = cached(new EntitlementRevision(7));
        assertAll("三要素 + TTL 必选：禁止无限期或无版本缓存",
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        null, "pv-1", "d", new EntitlementRevision(7), NOW, Duration.ofMinutes(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), " ", "d", new EntitlementRevision(7), NOW, Duration.ofMinutes(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", " ", new EntitlementRevision(7), NOW, Duration.ofMinutes(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", "d", null, NOW, Duration.ofMinutes(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", "d", new EntitlementRevision(7), null, Duration.ofMinutes(1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", "d", new EntitlementRevision(7), NOW, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", "d", new EntitlementRevision(7), NOW, Duration.ZERO)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        valid.decision(), "pv-1", "d", new EntitlementRevision(7), NOW,
                        Duration.ofMinutes(-1))));
    }

    @Test
    void entryVersionMustMatchEmbeddedDecisionVersion() {
        // 禁止「决定属于 pv-9、条目登记 pv-1」的错位缓存：三要素校验只比对条目级
        // 字段，错位登记会弱化跨版本禁用契约
        AuthorizationDecision foreignPolicyVersion = new AuthorizationDecision(
                DecisionKind.PERMIT, "pv-9", "digest-1", Set.of("ar.dunning.send"), List.of(), null);
        AuthorizationDecision foreignDigest = new AuthorizationDecision(
                DecisionKind.PERMIT, "pv-1", "digest-9", Set.of("ar.dunning.send"), List.of(), null);
        assertAll("决定自带版本/摘要与条目级字段不一致即拒绝",
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        foreignPolicyVersion, "pv-1", "digest-1", new EntitlementRevision(7),
                        NOW, Duration.ofMinutes(5))),
                () -> assertThrows(IllegalArgumentException.class, () -> new LocalDecisionCache.CachedDecision(
                        foreignDigest, "pv-1", "digest-1", new EntitlementRevision(7),
                        NOW, Duration.ofMinutes(5))));
    }

    @Test
    void putGetRoundtripAndMissingKey() {
        LocalDecisionCache cache = new LocalDecisionCache();
        LocalDecisionCache.CachedDecision entry = cached(new EntitlementRevision(7));
        cache.put("acme:ar.dunning.send:INV-001", entry);
        assertEquals(entry, cache.get("acme:ar.dunning.send:INV-001").orElseThrow());
        assertTrue(cache.get("acme:missing").isEmpty());
        assertAll("null key/entry 拒绝",
                () -> assertThrows(IllegalArgumentException.class, () -> cache.put(null, entry)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> cache.put("k", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> cache.get(null)));
    }

    @Test
    void isStillApplicableRejectsNullInputs() {
        assertAll("cached/now 必选：缺失按异常处理而非默认可用",
                () -> assertThrows(IllegalArgumentException.class, () -> LocalDecisionCache.isStillApplicable(
                        null, NOW, "pv-1", new EntitlementRevision(7))),
                () -> assertThrows(IllegalArgumentException.class, () -> LocalDecisionCache.isStillApplicable(
                        cached(new EntitlementRevision(7)), null, "pv-1", new EntitlementRevision(7))));
    }

    @Test
    void entitlementRevisionMustBeNonNegativeAndDriftIsInequality() {
        assertEquals(7, new EntitlementRevision(7).value());
        assertFalse(new EntitlementRevision(7).driftedFrom(new EntitlementRevision(7)));
        assertTrue(new EntitlementRevision(7).driftedFrom(new EntitlementRevision(8)));
        assertThrows(IllegalArgumentException.class, () -> new EntitlementRevision(-1));
    }
}
