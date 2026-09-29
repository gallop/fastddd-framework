package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView.ObjectType;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 目录差异报表组件（W3 设计 v3 §6.6 / §9.4 差异报表=管理视图，W3-c）：
 * {@code readObservation}（观察侧）对 {@code readProtectedFacts}（事实侧）的实时比对
 * + 待核对清单呈现。
 *
 * <p><b>边界（冻结端口限制的解释性决策，W3-c 执行记录登记）</b>：
 * {@link ObservedSnapshot} 不暴露人员 id 集（M1 冻结形态），故人员侧只有
 * <b>计数差</b>（观察 personCount 对事实层非墓碑 PERSON 行数）与 pending 清单，
 * 无人员级键差集；部门侧 observed departmentIds 是暴露的，做<b>键级比对</b>。
 * 墓碑行不进差集（受保护"确定删除"事实，零修改不复活）。</p>
 *
 * <p><b>一致性口径</b>：两次读取非同一事务快照（管理视图实时比对；发布切指针是
 * 原子的，读到的新旧混合最多呈现为一次过渡差集，不影响"缺失不得解释为删除"
 * 的使用禁令）。本组件只做呈现，<b>不裁决准入</b>——§9.3 授权由宿主入口负责；
 * 待核对消解（回查/裁决）属 M2。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryDiffReporter {

    private final DirectoryMirrorStore mirrorStore;

    public DirectoryDiffReporter(DirectoryMirrorStore mirrorStore) {
        this.mirrorStore = mirrorStore;
    }

    /** 生成差异报表（观察侧 vs 事实侧 + 待核对清单 + 范围级怀疑登记）。 */
    public DirectoryDiffReport report(DirectoryScope scope) {
        ObservedSnapshot observation = mirrorStore.readObservation(scope);
        List<ProtectedFactView> facts = mirrorStore.readProtectedFacts(scope);

        Set<String> observedDepartmentIds = new HashSet<>(observation.observedScope().departmentIds());
        Set<String> factDepartmentIds = new HashSet<>();
        long factPersonCount = 0;
        List<ProtectedFactView> pendingConfirmations = new ArrayList<>();
        for (ProtectedFactView fact : facts) {
            if (fact.tombstone()) {
                // 墓碑行不进任何差异面（含待核对清单——规则 B/E 均以非墓碑为前置，
                // 与存储不变量一致；W3-c 测试轮发现的原顺序偏差修正）
                continue;
            }
            if (fact.pendingConfirm()) {
                pendingConfirmations.add(fact);
            }
            if (fact.objectType() == ObjectType.DEPARTMENT) {
                factDepartmentIds.add(fact.externalId());
            } else {
                factPersonCount++;
            }
        }
        List<String> departmentsObservedNotInFacts = new ArrayList<>(observedDepartmentIds);
        departmentsObservedNotInFacts.removeAll(factDepartmentIds);
        departmentsObservedNotInFacts.sort(String::compareTo);
        List<String> departmentFactsNotObserved = new ArrayList<>(factDepartmentIds);
        departmentFactsNotObserved.removeAll(observedDepartmentIds);
        departmentFactsNotObserved.sort(String::compareTo);

        return new DirectoryDiffReport(observation, factPersonCount,
                departmentsObservedNotInFacts, departmentFactsNotObserved, pendingConfirmations);
    }

    /**
     * 差异报表结果：
     * <ul>
     *   <li>{@code observation}：观察侧全量（含 committedSeq/appliedSeq 水位、
     *       范围级怀疑登记 {@code scopeSuspicions}、personCount/departmentCount）；</li>
     *   <li>{@code factPersonCount}：事实侧非墓碑 PERSON 行数（人员计数差 =
     *       observation.personCount() − 本值，符号方向见报表呈现）；</li>
     *   <li>部门键级差集两个方向（观察有而事实无 = 首见待落库方向异常；
     *       事实有而观察无 = 本轮未观察到，候选待核对/范围收缩，<b>不得解释为删除</b>）；</li>
     *   <li>{@code pendingConfirmations}：人员级待核对清单（规则 B 置位/规则 E 复位面）。</li>
     * </ul>
     */
    public record DirectoryDiffReport(ObservedSnapshot observation,
                                      long factPersonCount,
                                      List<String> departmentsObservedNotInFacts,
                                      List<String> departmentFactsNotObserved,
                                      List<ProtectedFactView> pendingConfirmations) {

        public DirectoryDiffReport {
            pendingConfirmations = List.copyOf(pendingConfirmations);
        }

        /** 人员计数差（观察 − 事实；正=观察多、负=事实多，仅计数无键集）。 */
        public long personCountDelta() {
            return observation.personCount() - factPersonCount;
        }

        @Override
        public String toString() {
            return "DirectoryDiffReport[gen=" + observation.activeGenerationId().orElse("-")
                    + ", departments " + observation.departmentCount()
                    + " (obs-not-fact=" + departmentsObservedNotInFacts.size()
                    + ", fact-not-obs=" + departmentFactsNotObserved.size() + ")"
                    + ", persons obs=" + observation.personCount() + " fact=" + factPersonCount
                    + ", pending=" + pendingConfirmations.size()
                    + ", suspicions=" + observation.scopeSuspicions().size() + "]";
        }
    }
}
