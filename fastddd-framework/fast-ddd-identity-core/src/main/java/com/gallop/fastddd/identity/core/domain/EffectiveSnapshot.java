package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 有效视图快照（W3 设计 §6.6 M2 演进，<b>签名与语义冻结、实现 M2</b>）：
 * raw ⊕ 受保护事实的计算视图——授权语义的当前状态（墓碑/拒绝/撤销生效）。
 *
 * <p>M1 不交付、不承诺本视图：观察快照（{@link ObservedSnapshot}）不得冒充
 * 本层；M2 引入时可变有效视图的读取协议须重新验证（不复用不可变代读取结论）。
 * 本类型只随 {@code DirectoryEffectiveView}（独立接口）出现——M1 装配既不要求
 * 其实现，也不存在返回空集合的默认实现。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class EffectiveSnapshot {

    private final DirectoryScope scope;
    private final String activeGenerationId;
    private final long appliedSeq;
    private final List<EffectiveDepartment> departments;
    private final List<EffectivePerson> persons;

    public EffectiveSnapshot(DirectoryScope scope,
                             String activeGenerationId,
                             long appliedSeq,
                             List<EffectiveDepartment> departments,
                             List<EffectivePerson> persons) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.activeGenerationId = Objects.requireNonNull(activeGenerationId, "activeGenerationId");
        this.appliedSeq = appliedSeq;
        this.departments = List.copyOf(departments);
        this.persons = List.copyOf(persons);
    }

    public DirectoryScope scope() {
        return scope;
    }

    public String activeGenerationId() {
        return activeGenerationId;
    }

    public long appliedSeq() {
        return appliedSeq;
    }

    public List<EffectiveDepartment> departments() {
        return departments;
    }

    public List<EffectivePerson> persons() {
        return persons;
    }

    @Override
    public String toString() {
        return "EffectiveSnapshot[gen=" + activeGenerationId + ", appliedSeq=" + appliedSeq
                + ", departments=" + departments.size() + ", persons=" + persons.size() + "]";
    }

    /** 有效视图部门（墓碑生效的部门不出现）。 */
    public static final class EffectiveDepartment {

        private final String departmentId;
        private final String parentDepartmentId;
        private final String name;

        public EffectiveDepartment(String departmentId, String parentDepartmentId, String name) {
            this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
            this.parentDepartmentId = parentDepartmentId;
            this.name = name;
        }

        public String departmentId() {
            return departmentId;
        }

        public String parentDepartmentId() {
            return parentDepartmentId;
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return "EffectiveDepartment[" + departmentId + "]";
        }
    }

    /** 有效视图人员（墓碑生效的人员不出现；displayName 为经事实合并后的展示名）。 */
    public static final class EffectivePerson {

        private final String openId;
        private final String displayName;
        private final List<String> departmentIds;

        public EffectivePerson(String openId, String displayName, List<String> departmentIds) {
            this.openId = Objects.requireNonNull(openId, "openId");
            this.displayName = displayName;
            this.departmentIds = List.copyOf(departmentIds);
        }

        public String openId() {
            return openId;
        }

        public String displayName() {
            return displayName;
        }

        public List<String> departmentIds() {
            return departmentIds;
        }

        @Override
        public String toString() {
            return "EffectivePerson[" + openId + "]";
        }
    }
}
