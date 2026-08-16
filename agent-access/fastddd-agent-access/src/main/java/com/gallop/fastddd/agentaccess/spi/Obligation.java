package com.gallop.fastddd.agentaccess.spi;

import java.util.Set;

/**
 * 必做义务（typed；合成规则按子类型冻结）。
 *
 * <p>sealed 封闭：义务类型集合是治理契约的一部分，出现新义务类型必须扩充
 * 本 sealed 层级并同步补 {@link AuthorizationDecision} 的合成规则——
 * <b>禁止</b>以字符串标签做 runtime 分派来「兼容未知义务类型」（未知类型
 * fail-closed 的结构化表达）。</p>
 */
public sealed interface Obligation permits RequiredAuditObligation, DataScopeObligation, NumericCapObligation {

    /** 义务类型标签（审计/合成诊断用；不用于 runtime 分派）。 */
    String type();
}
