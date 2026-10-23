package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot.ObservedScope;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot.ScopeSuspicion;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView.ObjectType;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView.Origin;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-c 差异报表组件单元测试（§6.6 管理视图）：部门键级双差集、人员计数差、
 * 待核对清单、墓碑行排除、从未发布形态。冻结端口限制（人员侧仅计数差无键集）
 * 的呈现面在此钉住。
 */
class DirectoryDiffReporterTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-diff", "app");

    @Test
    void reportComputesBothDepartmentDiffDirectionsPersonDeltaAndPendingList() {
        FakeStore store = new FakeStore();
        store.observation = new ObservedSnapshot("gen-1", 3, 3,
                new ObservedScope(List.of("D1"), List.of("D1", "D2", "D3"), true, true),
                3, 5, List.of(new ScopeSuspicion("department-scope-shrunk", "缺失 1 个")));
        store.facts = List.of(
                fact(ObjectType.DEPARTMENT, "D1", false, false),
                fact(ObjectType.DEPARTMENT, "D2", false, false),
                fact(ObjectType.DEPARTMENT, "D4", false, false), // 事实有而观察无
                fact(ObjectType.PERSON, "OU1", false, false),
                fact(ObjectType.PERSON, "OU2", false, true));    // 待核对仍计入事实人数

        DirectoryDiffReporter.DirectoryDiffReport report = new DirectoryDiffReporter(store).report(SCOPE);

        // 部门键级双差集（排序稳定）
        assertThat(report.departmentsObservedNotInFacts()).containsExactly("D3");
        assertThat(report.departmentFactsNotObserved()).containsExactly("D4");
        // 人员侧仅计数差（观察 5 − 事实 2 = +3）
        assertThat(report.factPersonCount()).isEqualTo(2);
        assertThat(report.personCountDelta()).isEqualTo(3);
        // 待核对清单（规则 B 置位面）
        assertThat(report.pendingConfirmations()).singleElement()
                .satisfies(f -> assertThat(f.externalId()).isEqualTo("OU2"));
        // 范围级怀疑登记随观察侧透出
        assertThat(report.observation().scopeSuspicions()).singleElement()
                .satisfies(s -> assertThat(s.type()).isEqualTo("department-scope-shrunk"));
    }

    @Test
    void tombstoneRowsAreExcludedFromAllDiffFaces() {
        FakeStore store = new FakeStore();
        store.observation = new ObservedSnapshot("gen-1", 2, 2,
                new ObservedScope(List.of("D1"), List.of("D1"), true, true), 1, 1, List.of());
        store.facts = List.of(
                fact(ObjectType.DEPARTMENT, "D1", false, false),
                fact(ObjectType.DEPARTMENT, "D9", true, false),   // 墓碑部门：不进事实键集
                fact(ObjectType.PERSON, "OU1", false, false),
                fact(ObjectType.PERSON, "OU9", true, true));      // 墓碑人员：不计数不进待核对

        DirectoryDiffReporter.DirectoryDiffReport report = new DirectoryDiffReporter(store).report(SCOPE);

        assertThat(report.departmentsObservedNotInFacts()).isEmpty();
        assertThat(report.departmentFactsNotObserved()).isEmpty();
        assertThat(report.factPersonCount()).isEqualTo(1);
        assertThat(report.personCountDelta()).isZero();
        assertThat(report.pendingConfirmations()).isEmpty();
    }

    @Test
    void neverPublishedObservationYieldsEmptyDiffFaces() {
        FakeStore store = new FakeStore();
        store.observation = new ObservedSnapshot("", 0, 0,
                new ObservedScope(List.of(), List.of(), false, false), 0, 0, List.of());
        store.facts = List.of();

        DirectoryDiffReporter.DirectoryDiffReport report = new DirectoryDiffReporter(store).report(SCOPE);

        assertThat(report.observation().activeGenerationId()).isEmpty();
        assertThat(report.departmentsObservedNotInFacts()).isEmpty();
        assertThat(report.departmentFactsNotObserved()).isEmpty();
        assertThat(report.factPersonCount()).isZero();
        assertThat(report.personCountDelta()).isZero();
        assertThat(report.pendingConfirmations()).isEmpty();
    }

    // ------------------------------------------------------------- 夹具

    private static ProtectedFactView fact(ObjectType type, String externalId,
                                          boolean tombstone, boolean pending) {
        return new ProtectedFactView(type, externalId, 1, tombstone, Origin.FULL_SCAN,
                null, pending, "gen-1", 1);
    }

    /** 只读双面替身：观察快照与事实清单可注入；写面不可达。 */
    static final class FakeStore implements DirectoryMirrorStore {

        ObservedSnapshot observation;
        List<ProtectedFactView> facts = List.of();

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            throw new UnsupportedOperationException("差异报表只读取");
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return observation;
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return facts;
        }
    }
}
