package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 权威回查证据（W5 设计 §7，W5-b 新增）：事务外只读单点查询的观察值 +
 * CAS 基线，交 {@code DirectoryRecheckCommitter.commitCas} 裁决提交。
 *
 * <p>携带约定：{@code conclusion = CONTENT_DIFFERS} 时须携带观察到的权威
 * 内容对象（{@code observedPerson} 与 {@code observedDepartment} 恰一非空；
 * 实现层校验）；其余结论不携带替换内容（两者皆 null）。{@code expectedRevision}
 * = 读取镜像行当时的 local_revision（CAS 基线——提交事务内该行已前移则
 * commitCas 判 stale 零写返回）。{@code sourceEventId} = 触发本次回查的
 * Inbox 事件（无则 null，如管理端手动触发）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class RecheckEvidence {

    private final DirectoryObjectType objectType;
    private final String externalId;
    private final RecheckConclusion conclusion;
    private final long expectedRevision;
    private final DirectoryPerson observedPerson;
    private final DirectoryDepartment observedDepartment;
    private final String sourceEventId;

    public RecheckEvidence(DirectoryObjectType objectType,
                           String externalId,
                           RecheckConclusion conclusion,
                           long expectedRevision,
                           DirectoryPerson observedPerson,
                           DirectoryDepartment observedDepartment,
                           String sourceEventId) {
        this.objectType = Objects.requireNonNull(objectType, "objectType");
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("RecheckEvidence.externalId 不能为空");
        }
        this.externalId = externalId;
        this.conclusion = Objects.requireNonNull(conclusion, "conclusion");
        if (observedPerson != null && observedDepartment != null) {
            throw new IllegalArgumentException("observedPerson 与 observedDepartment 互斥（恰一非空或皆空）");
        }
        this.expectedRevision = expectedRevision;
        this.observedPerson = observedPerson;
        this.observedDepartment = observedDepartment;
        this.sourceEventId = sourceEventId;
    }

    public DirectoryObjectType objectType() {
        return objectType;
    }

    public String externalId() {
        return externalId;
    }

    public RecheckConclusion conclusion() {
        return conclusion;
    }

    /** CAS 基线：读取镜像行当时的 local_revision。 */
    public long expectedRevision() {
        return expectedRevision;
    }

    public DirectoryPerson observedPerson() {
        return observedPerson;
    }

    public DirectoryDepartment observedDepartment() {
        return observedDepartment;
    }

    public String sourceEventId() {
        return sourceEventId;
    }

    @Override
    public String toString() {
        return "RecheckEvidence[" + objectType + " " + externalId
                + ", conclusion=" + conclusion + ", expectedRevision=" + expectedRevision + "]";
    }
}
