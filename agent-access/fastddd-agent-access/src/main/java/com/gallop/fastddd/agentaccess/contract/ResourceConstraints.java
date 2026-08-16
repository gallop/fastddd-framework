package com.gallop.fastddd.agentaccess.contract;

import java.util.Set;

/**
 * 许可允许访问的资源约束（Contract §5.2：资源类型、ID/范围和数据范围）。
 *
 * <p><b>最小承载</b>：Contract 未对子结构给出逐字段表，本类型只固定
 * 「资源类型集合必选非空 + ID/范围与数据范围可选引用」的最小语义；结构细化
 * 随 Descriptor/编排落地（FDD-AA-12）扩充，不在此提前冻结猜测结构。</p>
 *
 * @param resourceTypes        可作用的业务资源类型集合（必选，非空）
 * @param resourceIdConstraints 可选：资源 ID/范围约束引用
 * @param dataScope            可选：数据范围约束引用
 */
public record ResourceConstraints(
        Set<String> resourceTypes,
        String resourceIdConstraints,
        String dataScope) {

    public ResourceConstraints {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            throw new IllegalArgumentException("resourceTypes 必选且不得为空");
        }
        resourceTypes = Set.copyOf(resourceTypes);
        if (resourceIdConstraints != null && resourceIdConstraints.isBlank()) {
            throw new IllegalArgumentException("resourceIdConstraints 可选但提供时不得为空白");
        }
        if (dataScope != null && dataScope.isBlank()) {
            throw new IllegalArgumentException("dataScope 可选但提供时不得为空白");
        }
    }
}
