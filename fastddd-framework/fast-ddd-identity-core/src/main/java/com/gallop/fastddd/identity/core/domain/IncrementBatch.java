package com.gallop.fastddd.identity.core.domain;

import java.util.List;

/**
 * 增量写批载荷（W5 设计 §5.2 增量路径，W5-b 新增）：由单个 Inbox 事件归一化
 * 而来的待写事实集。{@code eventId} = 触发本次增量的信封事件（幂等锚——
 * applier 落库前按 Inbox 处理状态 + 事实行 origin/change_ref 双层防重）。
 *
 * <p>departments 与 persons 可同批或任一为空（单事件通常只涉及一侧）；形态
 * 镜像 {@link DirectoryBatch}（构建代全量写批），区别仅在幂等锚与消费路径。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IncrementBatch {

    private final String eventId;
    private final List<DirectoryDepartment> departments;
    private final List<DirectoryPerson> persons;

    public IncrementBatch(String eventId,
                          List<DirectoryDepartment> departments,
                          List<DirectoryPerson> persons) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("IncrementBatch.eventId 不能为空");
        }
        this.eventId = eventId;
        this.departments = List.copyOf(departments);
        this.persons = List.copyOf(persons);
    }

    /** 仅部门增量。 */
    public static IncrementBatch ofDepartments(String eventId, List<DirectoryDepartment> departments) {
        return new IncrementBatch(eventId, departments, List.of());
    }

    /** 仅人员增量。 */
    public static IncrementBatch ofPersons(String eventId, List<DirectoryPerson> persons) {
        return new IncrementBatch(eventId, List.of(), persons);
    }

    public String eventId() {
        return eventId;
    }

    public List<DirectoryDepartment> departments() {
        return departments;
    }

    public List<DirectoryPerson> persons() {
        return persons;
    }

    /** 空批：零写零推零水位变化（§5.2 空批语义）。 */
    public boolean isEmpty() {
        return departments.isEmpty() && persons.isEmpty();
    }

    @Override
    public String toString() {
        return "IncrementBatch[eventId=" + eventId + ", "
                + departments.size() + " departments, " + persons.size() + " persons]";
    }
}
