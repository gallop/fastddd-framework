package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 受保护事实只读视图（W3 设计 §3.4/§6.6）：对象键账本 + 受保护状态位。
 * 供差异报表/待核对清单（管理视图，经 §9.3 授权）——<b>只进管理视图，
 * 不进授权路径</b>。
 *
 * <p>M1 事实层 = 追加式对象键账本：localRevision 恒 1（M1 无修改路径）、
 * tombstone 恒 false（M1 无删除证据来源，§6.4）、origin 恒 FULL_SCAN、
 * changeRef 恒 null（列冻结，M2 指向不可变变更日志载体条目）。
 * pendingConfirm 是观察层待核对标记（scan-missing/scope-suspect 消费面），
 * 置位/复位不动本视图其余字段、不推进事实水位。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ProtectedFactView {

    private final ObjectType objectType;
    private final String externalId;
    private final long localRevision;
    private final boolean tombstone;
    private final Origin origin;
    private final String changeRef;
    private final boolean pendingConfirm;
    private final String sourceGeneration;
    private final long updatedSeq;

    public ProtectedFactView(ObjectType objectType,
                             String externalId,
                             long localRevision,
                             boolean tombstone,
                             Origin origin,
                             String changeRef,
                             boolean pendingConfirm,
                             String sourceGeneration,
                             long updatedSeq) {
        this.objectType = Objects.requireNonNull(objectType, "objectType");
        this.externalId = Objects.requireNonNull(externalId, "externalId");
        this.localRevision = localRevision;
        this.tombstone = tombstone;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.changeRef = changeRef;
        this.pendingConfirm = pendingConfirm;
        this.sourceGeneration = sourceGeneration;
        this.updatedSeq = updatedSeq;
    }

    public ObjectType objectType() {
        return objectType;
    }

    /** 目录对象键（人员 = openId；部门 = departmentId）。 */
    public String externalId() {
        return externalId;
    }

    /** 每次被接受的证据门控变更 +1；M1 新增时 =1，此后不再修改。 */
    public long localRevision() {
        return localRevision;
    }

    /** 受保护"确定删除"事实；M1 恒 false（无写入方，W3 设计 §6.4）。 */
    public boolean tombstone() {
        return tombstone;
    }

    public Origin origin() {
        return origin;
    }

    /** 不可变变更日志条目引用（M2 写入；M1 恒 null，列冻结）。 */
    public String changeRef() {
        return changeRef;
    }

    /** 观察层待核对标记（scan-missing / scope-suspect 的消费入口）。 */
    public boolean pendingConfirm() {
        return pendingConfirm;
    }

    /** 首次观察到该对象键的代。 */
    public String sourceGeneration() {
        return sourceGeneration;
    }

    /** 首次落库时的事实线序号（= 当次 T7 的 H_after）。 */
    public long updatedSeq() {
        return updatedSeq;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProtectedFactView
                && objectType == ((ProtectedFactView) o).objectType
                && externalId.equals(((ProtectedFactView) o).externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectType, externalId);
    }

    @Override
    public String toString() {
        return "ProtectedFactView[" + objectType + ":" + externalId
                + ", pendingConfirm=" + pendingConfirm + ", tombstone=" + tombstone + "]";
    }

    /** 目录对象类型（事实行主键维度之一）。 */
    public enum ObjectType {
        PERSON, DEPARTMENT
    }

    /** 事实来源（W3 设计 §5.1：M1 恒 FULL_SCAN；RECHECK/INCREMENT 为 M2 增量/回查路径，列冻结）。 */
    public enum Origin {
        FULL_SCAN, RECHECK, INCREMENT
    }
}
