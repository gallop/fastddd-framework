package com.gallop.fastddd.agentaccess.kit.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FDD-AA-15/12.5：覆盖目录与 test ID 全集（{@link TestIdCatalog#ALL}）完全对齐——
 * 无遗漏（每个 ID 至少一条归属）、无多余（未知 ID 拒绝）、登记条目形态合法。
 */
class TestCoverageCatalogTest {

    @Test
    void catalogCoversExactlyTheFullTestIdUniverse() {
        // 双向：全集 ⊆ 目录（无遗漏）且目录 ⊆ 全集（无未知 ID）
        Set<String> covered = TestCoverageCatalog.coveredTestIds();
        for (String testId : TestIdCatalog.ALL) {
            assertTrue(covered.contains(testId), "缺少归属登记: " + testId);
        }
        for (String testId : covered) {
            assertTrue(TestIdCatalog.isKnown(testId), "目录出现全集外 test ID: " + testId);
        }
        assertEquals(TestIdCatalog.ALL.size(), covered.size(), "33 个 test ID 一个不多一个不少");
    }

    @Test
    void everyEntryIsWellFormedAndGrouped() {
        Map<String, List<TestCoverageCatalog.CoverageEntry>> byId = TestCoverageCatalog.byTestId();
        assertEquals(TestCoverageCatalog.ENTRIES.size(),
                byId.values().stream().mapToInt(List::size).sum(), "分组不丢条目");
        for (TestCoverageCatalog.CoverageEntry entry : TestCoverageCatalog.ENTRIES) {
            assertNotNull(entry.level(), entry.testId() + " 层级必选");
            assertFalse(entry.carriedBy().isBlank(), entry.testId() + " 承载说明非空白");
            assertTrue(entry.carriedBy().length() >= 4, entry.testId() + " 承载说明有实质内容");
        }
    }

    @Test
    void crossRepoAndConformanceCarriersAreExplicitlyRegistered() {
        // 登记不冒充：跨仓/宿主归属与 kit conformance 必须在目录中显式出现（抽查锚点条目）
        Map<String, List<TestCoverageCatalog.CoverageEntry>> byId = TestCoverageCatalog.byTestId();
        assertTrue(byId.get("AST-03").stream()
                        .anyMatch(e -> e.level() == TestCoverageCatalog.CoverageLevel.HOST_OR_CROSS_REPO),
                "Python GV 复算必须显式登记跨仓归属");
        assertTrue(byId.get("AAC-09").stream()
                        .anyMatch(e -> e.level() == TestCoverageCatalog.CoverageLevel.KIT_CONFORMANCE),
                "AAC-09 宿主真表 conformance 必须显式登记");
        assertTrue(byId.get("AST-07").stream()
                        .anyMatch(e -> e.level() == TestCoverageCatalog.CoverageLevel.HOST_OR_CROSS_REPO),
                "READ 时间窗/速率未实现范围必须显式登记不冒充");
    }

    @Test
    void catalogIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> TestCoverageCatalog.ENTRIES.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> TestCoverageCatalog.byTestId().put("AAC-99", List.of()));
    }

    @Test
    void blankEntryComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestCoverageCatalog.CoverageEntry(null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TestCoverageCatalog.CoverageEntry("  ", TestCoverageCatalog.CoverageLevel.REPOSITORY_TEST, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new TestCoverageCatalog.CoverageEntry("AAC-01", null, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new TestCoverageCatalog.CoverageEntry("AAC-01", TestCoverageCatalog.CoverageLevel.KIT_CONFORMANCE, ""));
    }
}
