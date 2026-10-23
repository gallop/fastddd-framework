package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 构建代校验报告（W3 设计 §4 VALIDATED 前置）：completePaging 之后、publish
 * 之前的只读校验结果。校验内容：三流分页均正常结束；任职关系引用的部门/人员
 * 均在本代内；范围外对象未混入；批次计数与检查点一致。
 *
 * <p>校验失败（任何一条不满足）时 {@code DirectoryMirrorStore.validate} 抛
 * {@code DirectoryContractViolationException}（不返回"失败报告"——编排路径
 * 直接走整代废弃，不发布撕裂快照）。通过后置 VALIDATED，此后追加批次一律拒绝。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ValidationReport {

    private final String generationId;
    private final ObservedSnapshot.ObservedScope observedScope;
    private final int departmentCount;
    private final int personCount;
    private final int relationCount;
    private final List<String> checks;

    public ValidationReport(String generationId,
                            ObservedSnapshot.ObservedScope observedScope,
                            int departmentCount,
                            int personCount,
                            int relationCount,
                            List<String> checks) {
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.observedScope = Objects.requireNonNull(observedScope, "observedScope");
        this.checks = List.copyOf(checks);
        this.departmentCount = departmentCount;
        this.personCount = personCount;
        this.relationCount = relationCount;
    }

    public String generationId() {
        return generationId;
    }

    /** completePaging 冻结的范围证据（随报告复核三流完整性）。 */
    public ObservedSnapshot.ObservedScope observedScope() {
        return observedScope;
    }

    public int departmentCount() {
        return departmentCount;
    }

    public int personCount() {
        return personCount;
    }

    /** 任职关系数（person × department 派生）。 */
    public int relationCount() {
        return relationCount;
    }

    /** 通过的检查项（稳定名，供记录与测试锚定）。 */
    public List<String> checks() {
        return checks;
    }

    @Override
    public String toString() {
        return "ValidationReport[gen=" + generationId + ", departments=" + departmentCount
                + ", persons=" + personCount + ", relations=" + relationCount + "]";
    }
}
