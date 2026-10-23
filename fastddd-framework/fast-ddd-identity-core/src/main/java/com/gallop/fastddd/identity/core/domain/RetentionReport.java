package com.gallop.fastddd.identity.core.domain;

/**
 * 目录事实回收报告（W5 设计 §8.3 M-26；W5-f 设计 §3.2，W5-f f-1 交付）：
 * {@code preview} 与 {@code execute} 共用——preview=true 形态各计数为同判据
 * SELECT count 只读预估（零删除）；execute 形态为各面实际回收行数。
 *
 * <ul>
 *   <li>面①三面分计：{@code mirrorPersonRows}/{@code mirrorDepartmentRows}/
 *       {@code mirrorRelationRows}（generation 行本体保留，F2）；</li>
 *   <li>面②：{@code inboxRowsPurged}（仅 PROCESSED 行）；</li>
 *   <li>面③：{@code changeLogRowsPurged} + {@code changeLogSkippedByWatermark}
 *       （到龄但被水位 W = min(start_seq) over（非终态代 ∪ {活动代}）锚定保留
 *       的条目在场登记——P3-1：活动代激活条目恒保留，本标志即该守护的观测面）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public record RetentionReport(DirectoryScope scope,
                              long mirrorPersonRows,
                              long mirrorDepartmentRows,
                              long mirrorRelationRows,
                              long inboxRowsPurged,
                              long changeLogRowsPurged,
                              boolean changeLogSkippedByWatermark,
                              boolean preview) {

    /** 面①三镜像合计（诊断便捷面，非契约字段）。 */
    public long mirrorRowsTotal() {
        return mirrorPersonRows + mirrorDepartmentRows + mirrorRelationRows;
    }
}
