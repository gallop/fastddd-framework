package com.gallop.fastddd.agentaccess.kit.evidence;

import java.util.Set;

/**
 * test ID 全集目录（AA-03 §1～§4 / Contract §12 / 向量文档 v1）：
 * AAC-01～18、AST-01～08、GV-01～07。证据 manifest 的 test ID 必须出自本全集。
 *
 * <p>全集是<b>登记范围</b>，不是执行声明——某个 ID 在 manifest 中标
 * {@code REGISTERED_ONLY} 表示该场景归属宿主或跨仓联测（如 Python GV 复算、
 * FastAgent V04-04 联测），本仓 kit 未执行也不冒充已执行。</p>
 */
public final class TestIdCatalog {

    public static final Set<String> ALL = build();

    private TestIdCatalog() {
    }

    /** 编号是否属于全集（格式 + 范围双校验） */
    public static boolean isKnown(String testId) {
        return testId != null && ALL.contains(testId);
    }

    private static Set<String> build() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int i = 1; i <= 18; i++) {
            ids.add("AAC-" + pad(i));
        }
        for (int i = 1; i <= 8; i++) {
            ids.add("AST-" + pad(i));
        }
        for (int i = 1; i <= 7; i++) {
            ids.add("GV-" + pad(i));
        }
        return java.util.Collections.unmodifiableSet(ids);
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
