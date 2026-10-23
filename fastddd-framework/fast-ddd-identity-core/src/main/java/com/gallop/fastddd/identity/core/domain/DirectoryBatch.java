package com.gallop.fastddd.identity.core.domain;

import java.util.List;

/**
 * 一次写批载荷（W3 设计 §5.2 T6b）：携带 generation_id 写入构建代，
 * 仅新代可见。分页游标/检查点随代记录由镜像存储内部承载（T6b 步骤④），
 * 批载荷本身不含游标——失败处置为整代废弃，运行内游标由编排用例持有。
 *
 * <p>departments 与 persons 可同批或任一为空（部门页批 / 人员页批分别写入
 * 也合法）；任职关系由 persons 派生，不单独成批。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryBatch {

    private final List<DirectoryDepartment> departments;
    private final List<DirectoryPerson> persons;

    public DirectoryBatch(List<DirectoryDepartment> departments, List<DirectoryPerson> persons) {
        this.departments = List.copyOf(departments);
        this.persons = List.copyOf(persons);
    }

    /** 仅部门批。 */
    public static DirectoryBatch ofDepartments(List<DirectoryDepartment> departments) {
        return new DirectoryBatch(departments, List.of());
    }

    /** 仅人员批。 */
    public static DirectoryBatch ofPersons(List<DirectoryPerson> persons) {
        return new DirectoryBatch(List.of(), persons);
    }

    public List<DirectoryDepartment> departments() {
        return departments;
    }

    public List<DirectoryPerson> persons() {
        return persons;
    }

    public boolean isEmpty() {
        return departments.isEmpty() && persons.isEmpty();
    }

    @Override
    public String toString() {
        return "DirectoryBatch[" + departments.size() + " departments, "
                + persons.size() + " persons]";
    }
}
