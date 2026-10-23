package com.gallop.fastddd.identity.core.domain;

/**
 * 单键本地事实视图（W5 设计 §7.3 三步之首，W5-d）：worker 事务外读取的
 * 本地基线——expectedRevision（CAS 基线）+ 形态标志 + 内容文本。
 *
 * <p>区别于 {@link ProtectedFactView}（W3 冻结、<b>无内容</b>的管理面只读视图）：
 * 本视图携带 {@code factJson}（事实列原文文本）供内容等值比较——比较经
 * {@code DirectoryFactMatcher} 端口结构等值承载，不比字符串字面。</p>
 *
 * @param localRevision 本地事实版本（CAS 基线；无行时调用方以 0 为基线）
 * @param tombstone     墓碑标志（true 时权威在场 → 结论必为 CONTENT_DIFFERS 复活）
 * @param pendingConfirm 待核对标志（回查消解面，规则 E/IDENTICAL 复位）
 * @param factJson      事实内容列文本（jsonb 文本形态；无行不存在本视图）
 * @author gallop
 * @since 1.1.0
 */
public record LocalFactView(long localRevision, boolean tombstone, boolean pendingConfirm,
                            String factJson) {

    public LocalFactView {
        if (factJson == null) {
            throw new IllegalArgumentException("factJson 必须非 null（内容比较输入）");
        }
    }
}
