package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 观察快照（W3 设计 §6.6，M1 唯一目录读接口的响应）：
 * <b>"最近一次成功全量扫描观察到什么"</b>——活动代 raw + 元数据快照 +
 * observed_scope 范围证据 + 范围级待核对登记。
 *
 * <p><b>使用禁令（M1 冻结）</b>：禁止作为授权/准入/身份状态依据（登录准入只读
 * principal，§3.2）；禁止把快照缺失解释为删除（删除语义只存在于 facts.tombstone，
 * M2）。差异报表等管理视图须同时呈现观察侧与事实侧（readProtectedFacts）。</p>
 *
 * <p>{@code scopeSuspicions} 是<b>可查询的范围级待核对承载</b>（scope-suspect，
 * W3-V3 复评钉住事项 ②）：范围收缩/完整性存疑时登记于此（非仅日志），
 * 供差异报表呈现原因、生成 M2 回查工作项——不得解释为删除或离职。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ObservedSnapshot {

    private final String activeGenerationId;
    private final long committedSeq;
    private final long appliedSeq;
    private final ObservedScope observedScope;
    private final int departmentCount;
    private final int personCount;
    private final List<ScopeSuspicion> scopeSuspicions;

    public ObservedSnapshot(String activeGenerationId,
                            long committedSeq,
                            long appliedSeq,
                            ObservedScope observedScope,
                            int departmentCount,
                            int personCount,
                            List<ScopeSuspicion> scopeSuspicions) {
        this.activeGenerationId = Objects.requireNonNull(activeGenerationId, "activeGenerationId");
        this.observedScope = Objects.requireNonNull(observedScope, "observedScope");
        this.scopeSuspicions = List.copyOf(scopeSuspicions);
        this.committedSeq = committedSeq;
        this.appliedSeq = appliedSeq;
        this.departmentCount = departmentCount;
        this.personCount = personCount;
    }

    /** 活动代 id（读取时从发布元数据单事务快照；尚未发布过 = 不存在观察快照）。 */
    public Optional<String> activeGenerationId() {
        return Optional.ofNullable(activeGenerationId.isEmpty() ? null : activeGenerationId);
    }

    /** 受保护事实线水位快照（读取时点值；语义见 W3 设计 §6.2）。 */
    public long committedSeq() {
        return committedSeq;
    }

    /** 最终已应用水位（= 最近一次发布的 H_after）。 */
    public long appliedSeq() {
        return appliedSeq;
    }

    /** 实得范围证据（completePaging 冻结）。 */
    public ObservedScope observedScope() {
        return observedScope;
    }

    public int departmentCount() {
        return departmentCount;
    }

    public int personCount() {
        return personCount;
    }

    /** 范围级待核对登记（scope-suspect；可查询承载，非日志）。 */
    public List<ScopeSuspicion> scopeSuspicions() {
        return scopeSuspicions;
    }

    @Override
    public String toString() {
        return "ObservedSnapshot[gen=" + activeGenerationId + ", departments=" + departmentCount
                + ", persons=" + personCount + ", suspicions=" + scopeSuspicions.size() + "]";
    }

    /**
     * 实得范围证据（W3 设计 §5.1 observed_scope）：completePaging 冻结的
     * 根部门 id 集 + 实际遍历到的部门 id 集 + 三流完成标记。
     */
    public static final class ObservedScope {

        private final List<String> rootDepartmentIds;
        private final List<String> departmentIds;
        private final boolean departmentPagingComplete;
        private final boolean personPagingComplete;

        public ObservedScope(List<String> rootDepartmentIds,
                             List<String> departmentIds,
                             boolean departmentPagingComplete,
                             boolean personPagingComplete) {
            this.rootDepartmentIds = List.copyOf(rootDepartmentIds);
            this.departmentIds = List.copyOf(departmentIds);
            this.departmentPagingComplete = departmentPagingComplete;
            this.personPagingComplete = personPagingComplete;
        }

        public List<String> rootDepartmentIds() {
            return rootDepartmentIds;
        }

        /** 本代实际遍历到的部门 id 集（⊆ 配置范围；范围收缩时小于上一代）。 */
        public List<String> departmentIds() {
            return departmentIds;
        }

        public boolean departmentPagingComplete() {
            return departmentPagingComplete;
        }

        public boolean personPagingComplete() {
            return personPagingComplete;
        }

        @Override
        public String toString() {
            return "ObservedScope[roots=" + rootDepartmentIds.size()
                    + ", departments=" + departmentIds.size() + "]";
        }
    }

    /**
     * 范围级待核对登记（scope-suspect）：部门级范围收缩、三流不完整等
     * "范围证据不完整"形态的结构化登记。{@code detail} 为不含 PII 的
     * 稳定描述（字段白名单约束见 W3 设计 §7 结构化日志约定）。
     */
    public static final class ScopeSuspicion {

        private final String type;
        private final String detail;

        public ScopeSuspicion(String type, String detail) {
            this.type = Objects.requireNonNull(type, "type");
            this.detail = Objects.requireNonNull(detail, "detail");
        }

        /** 稳定类型码（如 {@code department-scope-shrunk} / {@code paging-incomplete}）。 */
        public String type() {
            return type;
        }

        public String detail() {
            return detail;
        }

        @Override
        public String toString() {
            return type + "(" + detail + ")";
        }
    }
}
