package com.gallop.fastddd.identity.core.domain;

/**
 * 目录增量变化类型（W5 设计 §3.2/§9.1，W5-b 词表冻结）：{type} 词表承载
 * {@code dir-change:{scope}:{committed_seq}:{type}} 事件 id 的类型段。
 *
 * <p><b>限定名形态（冻结）</b>：采用 {@code PERSON_CHANGED / PERSON_DEACTIVATED /
 * DEPARTMENT_CHANGED / DEPARTMENT_DEACTIVATED} 四枚举——裸 {@code DEACTIVATED}
 * 在混合对象引用的事件 id 中丢失人员/部门判别。依据 = W0-6 P-3 六事件最小集
 * （人员/部门 × created_v3/updated_v3/deleted_v3）；created/updated 归并 CHANGED；
 * {@code contact.scope.updated_v3} 不纳入 W5 消费面。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public enum DirectoryChangeType {

    /** 人员内容变化（created_v3/updated_v3 归并）。 */
    PERSON_CHANGED(DirectoryObjectType.PERSON),

    /** 人员停用/离职（status 布尔组 / deleted_v3，一律经回查确证后发）。 */
    PERSON_DEACTIVATED(DirectoryObjectType.PERSON),

    /** 部门内容变化（created_v3/updated_v3 归并）。 */
    DEPARTMENT_CHANGED(DirectoryObjectType.DEPARTMENT),

    /** 部门停用/删除（deleted_v3 + status.is_deleted 布尔，一律经回查确证后发）。 */
    DEPARTMENT_DEACTIVATED(DirectoryObjectType.DEPARTMENT);

    private final DirectoryObjectType objectType;

    DirectoryChangeType(DirectoryObjectType objectType) {
        this.objectType = objectType;
    }

    /** 该变化类型归属的对象域（人员/部门）。 */
    public DirectoryObjectType objectType() {
        return objectType;
    }
}
