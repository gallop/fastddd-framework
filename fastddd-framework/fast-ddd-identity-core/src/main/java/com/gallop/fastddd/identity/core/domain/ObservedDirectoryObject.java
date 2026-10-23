package com.gallop.fastddd.identity.core.domain;

/**
 * 单对象权威观察（W5 设计 §7.1 回查、W0-6 P-8，W5-d）：{@code DirectoryRecheckQuery}
 * 的返回包装——{@link DirectoryPerson}/{@link DirectoryDepartment} 互斥携带 +
 * 停用派生标志。
 *
 * <p><b>deactivated 派生口径（W0-6 §1/§3.2 文档级）</b>：适配器侧由源状态布尔组
 * 派生，域侧不重复推断——人员 = {@code is_frozen ∨ is_resigned ∨ is_exited ∨
 * is_unjoin}（{@code is_activated=false} 单独不构成停用——激活流程语义）；
 * 部门 = {@code status.is_deleted}。live 校准（离职用户单查真实返回形态、
 * is_exited→is_resigned 转换时机）挂 W0-6 live 实测腿。</p>
 *
 * <p>{@link DirectoryPerson}/{@link DirectoryDepartment} 为 W3 冻结域类型、
 * <b>不含状态字段</b>——本包装即停用信息的标准载体（扩域类型 = 冻结契约变更）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public record ObservedDirectoryObject(DirectoryPerson person, DirectoryDepartment department,
                                      boolean deactivated) {

    public ObservedDirectoryObject {
        if ((person == null) == (department == null)) {
            throw new IllegalArgumentException(
                    "person 与 department 互斥恰携其一（person=" + (person != null)
                            + ", department=" + (department != null) + "）");
        }
    }

    public static ObservedDirectoryObject of(DirectoryPerson person, boolean deactivated) {
        return new ObservedDirectoryObject(person, null, deactivated);
    }

    public static ObservedDirectoryObject of(DirectoryDepartment department, boolean deactivated) {
        return new ObservedDirectoryObject(null, department, deactivated);
    }

    /** 观察对象类型（由互斥携带方派生）。 */
    public DirectoryObjectType objectType() {
        return person != null ? DirectoryObjectType.PERSON : DirectoryObjectType.DEPARTMENT;
    }
}
