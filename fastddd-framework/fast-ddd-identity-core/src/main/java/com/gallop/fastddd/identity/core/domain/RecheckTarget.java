package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 回查目标（W5 设计 §7.1，W5-d）：事件侧解析出的「对象类型 + external_id」
 * 二元组——事件只触发回查，目标键即回查与 CAS 的锚。
 *
 * <p>契约见端口 {@code DirectoryEventChannel.resolveObject}：消费类型事件
 * 必然可解析；缺失键 = 协议漂移/实现缺陷，实现方上抛不返回空。</p>
 *
 * @param objectType 对象类型（PERSON / DEPARTMENT）
 * @param externalId 目录对象键（open_id / open_department_id）
 * @author gallop
 * @since 1.1.0
 */
public record RecheckTarget(DirectoryObjectType objectType, String externalId) {

    public RecheckTarget {
        Objects.requireNonNull(objectType, "objectType");
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId 必须非空");
        }
    }
}
