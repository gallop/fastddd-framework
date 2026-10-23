package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 观察到的部门（目录源分页单元；观察快照层，W3 设计 §3.4）。
 *
 * <p>快照字段按源侧实际字段权限可得性呈现——缺失即空/省略，不虚构。
 * 本对象只是"观察到什么"的记录，不构成授权语义。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryDepartment {

    private final String departmentId;
    private final String parentDepartmentId;
    private final String name;

    public DirectoryDepartment(String departmentId, String parentDepartmentId, String name) {
        this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
        this.parentDepartmentId = parentDepartmentId;
        this.name = name;
    }

    public String departmentId() {
        return departmentId;
    }

    /** 根部门为 {@code null}（或按源侧语义的自引用值，适配器归一化为 null）。 */
    public String parentDepartmentId() {
        return parentDepartmentId;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DirectoryDepartment
                && departmentId.equals(((DirectoryDepartment) o).departmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentId);
    }

    @Override
    public String toString() {
        return "DirectoryDepartment[" + departmentId + "]";
    }
}
