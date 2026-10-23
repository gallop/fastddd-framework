package com.gallop.fastddd.identity.core.directory;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;

import java.util.List;

/** W3-a 目录测试对象便捷工厂（仅测试）。 */
final class DirectoryTestDsl {

    private DirectoryTestDsl() {
    }

    /** 根部门（parent = null）。 */
    static DirectoryDepartment root(String id) {
        return new DirectoryDepartment(id, null, "dept-" + id);
    }

    /** 子部门（parent = "0" 根哨兵按飞书语义归一化为 null 的对立面：显式父 id）。 */
    static DirectoryDepartment department(String id, String parentId) {
        return new DirectoryDepartment(id, parentId, "dept-" + id);
    }

    static DirectoryPerson person(String openId, String... departmentIds) {
        return new DirectoryPerson(openId, "name-" + openId, null, List.of(departmentIds));
    }
}
