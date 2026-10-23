package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 观察到的人员（目录源分页单元；观察快照层，W3 设计 §3.4）。
 *
 * <p>{@code departmentIds} 即任职关系（镜像第三流 directory_relation_mirror 由
 * person × department 派生，源端口不设独立任职流）。openId 是目录对象键
 * （external_id），与本地 principalId 经五元组绑定关联——本对象不含也不推断主体状态。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryPerson {

    private final String openId;
    private final String name;
    private final String enName;
    private final List<String> departmentIds;

    public DirectoryPerson(String openId, String name, String enName, List<String> departmentIds) {
        this.openId = Objects.requireNonNull(openId, "openId");
        this.name = name;
        this.enName = enName;
        this.departmentIds = List.copyOf(departmentIds);
    }

    public String openId() {
        return openId;
    }

    public String name() {
        return name;
    }

    public String enName() {
        return enName;
    }

    /** 任职部门 id 集（可能为空：人员未挂任何可见部门）。 */
    public List<String> departmentIds() {
        return departmentIds;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DirectoryPerson
                && openId.equals(((DirectoryPerson) o).openId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openId);
    }

    @Override
    public String toString() {
        return "DirectoryPerson[" + openId + "]";
    }
}
