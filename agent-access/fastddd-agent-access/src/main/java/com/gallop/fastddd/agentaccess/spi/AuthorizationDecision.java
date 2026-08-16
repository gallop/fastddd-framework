package com.gallop.fastddd.agentaccess.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * typed 授权决定（deny-overrides + INDETERMINATE 拒绝 + 义务合成）。
 *
 * <p>双端 PEP 语义（AAC-01/18）：上游 FastAgent ALLOW 不能替代本端判定——
 * 两端 PERMIT 才 PERMIT；任一端 DENY 即 DENY；任一端无法判定即按拒绝处理。</p>
 *
 * <p>合成规则（{@link #combine}，仅对 PERMIT×PERMIT 的义务与允许集合作合；
 * DENY/INDETERMINATE 结果按 deny-overrides 短路，义务取两侧必做审计并集）：</p>
 * <ul>
 *   <li>允许集合：{@code permittedActions} 求交（空交集 → DENY）；</li>
 *   <li>{@link RequiredAuditObligation}：求并；</li>
 *   <li>{@link DataScopeObligation}：求交（空交集 → DENY）；</li>
 *   <li>{@link NumericCapObligation}：同名取 min（更严格值）；</li>
 *   <li>义务类型封闭（sealed）：新类型必须扩层级并补规则，禁止 runtime
 *       字符串分派兼容未知类型（未知即 fail-closed）。</li>
 * </ul>
 *
 * @param kind             决定类型
 * @param policyVersion    产生本决定的策略版本（缓存三要素之一）
 * @param decisionDigest   决定内容摘要（缓存三要素之一；骨架期为调用方提供的稳定串）
 * @param permittedActions 允许集合（action/scope 标识）
 * @param obligations      必做义务
 * @param reason           DENY/INDETERMINATE 时的归因说明（脱敏，不含 payload）
 */
public record AuthorizationDecision(
        DecisionKind kind,
        String policyVersion,
        String decisionDigest,
        Set<String> permittedActions,
        List<Obligation> obligations,
        String reason) {

    public AuthorizationDecision {
        if (kind == null) {
            throw new IllegalArgumentException("kind 必选");
        }
        if (kind == DecisionKind.PERMIT && (permittedActions == null || permittedActions.isEmpty())) {
            // 与 combine()「允许集合交集为空 → DENY」同一 fail-closed 语义：
            // PERMIT 必须携带非空允许集合，否则等于无范围放行
            throw new IllegalArgumentException("PERMIT 决定的 permittedActions 必选且不得为空");
        }
        permittedActions = permittedActions == null ? Set.of() : Set.copyOf(permittedActions);
        obligations = obligations == null ? List.of() : List.copyOf(obligations);
    }

    /** 是否允许放行（唯一放行判定：仅 PERMIT）。 */
    public boolean isPermitted() {
        return kind == DecisionKind.PERMIT;
    }

    /**
     * deny-overrides 合成：任一 DENY → DENY；否则任一 INDETERMINATE →
     * INDETERMINATE；双 PERMIT → PERMIT 且求交/合成。
     */
    public static AuthorizationDecision combine(AuthorizationDecision left, AuthorizationDecision right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("合成双方必选：缺失即按无法合成处理，不做默认放行");
        }
        if (left.kind == DecisionKind.DENY || right.kind == DecisionKind.DENY) {
            return new AuthorizationDecision(DecisionKind.DENY, mergePolicyVersion(left, right), null,
                    Set.of(), unionAuditObligations(left, right), "deny-overrides：存在明确拒绝端");
        }
        if (left.kind == DecisionKind.INDETERMINATE || right.kind == DecisionKind.INDETERMINATE) {
            return new AuthorizationDecision(DecisionKind.INDETERMINATE, mergePolicyVersion(left, right),
                    null, Set.of(), unionAuditObligations(left, right),
                    "存在无法判定端：INDETERMINATE 按拒绝处理");
        }
        return combinePermits(left, right);
    }

    private static AuthorizationDecision combinePermits(AuthorizationDecision left, AuthorizationDecision right) {
        Set<String> actions = new TreeSet<>(left.permittedActions);
        actions.retainAll(right.permittedActions);
        if (actions.isEmpty()) {
            return new AuthorizationDecision(DecisionKind.DENY, mergePolicyVersion(left, right), null,
                    Set.of(), unionAuditObligations(left, right), "允许集合交集为空：按 fail-closed 拒绝");
        }

        Set<String> auditTopics = new TreeSet<>();
        Set<String> dataScopes = null;
        Map<String, Long> caps = new LinkedHashMap<>();
        for (Obligation obligation : concatObligations(left, right)) {
            switch (obligation) {
                case RequiredAuditObligation audit -> auditTopics.add(audit.auditTopic());
                case DataScopeObligation scope -> dataScopes = intersectScopes(dataScopes, scope.allowedDataScopes());
                case NumericCapObligation cap -> caps.merge(cap.capName(), cap.maxValue(), Math::min);
            }
        }
        if (dataScopes != null && dataScopes.isEmpty()) {
            return new AuthorizationDecision(DecisionKind.DENY, mergePolicyVersion(left, right), null,
                    actions, List.copyOf(buildObligationList(auditTopics, dataScopes, caps)),
                    "数据范围交集为空：按 fail-closed 拒绝");
        }
        return new AuthorizationDecision(DecisionKind.PERMIT, mergePolicyVersion(left, right), null,
                actions, buildObligationList(auditTopics, dataScopes, caps), null);
    }

    private static Set<String> intersectScopes(Set<String> accumulated, Set<String> next) {
        if (accumulated == null) {
            return new TreeSet<>(next);
        }
        accumulated.retainAll(next);
        return accumulated;
    }

    private static List<Obligation> buildObligationList(Set<String> auditTopics, Set<String> dataScopes,
                                                        Map<String, Long> caps) {
        List<Obligation> merged = new ArrayList<>();
        auditTopics.forEach(topic -> merged.add(new RequiredAuditObligation(topic)));
        // 交空即 DENY（拒绝原因走 reason）；不得构造空集合 DataScopeObligation——
        // 那是结构非法值，会被紧致校验拒绝
        if (dataScopes != null && !dataScopes.isEmpty()) {
            merged.add(new DataScopeObligation(dataScopes));
        }
        caps.forEach((name, max) -> merged.add(new NumericCapObligation(name, max)));
        return merged;
    }

    private static List<Obligation> concatObligations(AuthorizationDecision left, AuthorizationDecision right) {
        List<Obligation> all = new ArrayList<>(left.obligations);
        all.addAll(right.obligations);
        return all;
    }

    private static List<Obligation> unionAuditObligations(AuthorizationDecision left, AuthorizationDecision right) {
        Set<String> topics = new TreeSet<>();
        for (Obligation obligation : concatObligations(left, right)) {
            if (obligation instanceof RequiredAuditObligation audit) {
                topics.add(audit.auditTopic());
            }
        }
        return topics.stream().map(RequiredAuditObligation::new).map(o -> (Obligation) o).toList();
    }

    private static String mergePolicyVersion(AuthorizationDecision left, AuthorizationDecision right) {
        if (left.policyVersion == null) {
            return right.policyVersion;
        }
        if (right.policyVersion == null) {
            return left.policyVersion;
        }
        return left.policyVersion.equals(right.policyVersion) ? left.policyVersion
                : left.policyVersion + "+" + right.policyVersion;
    }
}
