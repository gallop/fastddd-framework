package com.gallop.fastddd.agentaccess.kit.evidence;

/**
 * 单个 test ID 的执行结果（manifest 填报值——校验器不生成事实）。
 *
 * <ul>
 *   <li>{@code PASSED}——该场景已真实执行且断言通过；</li>
 *   <li>{@code FAILED}——已执行且失败（构建必须失败，不得静默降级）；</li>
 *   <li>{@code SKIPPED}——环境不具备如实跳过（如无 Docker 时 conformance），
 *       报告必须保留 skipped 记录，不得冒充 PASSED。</li>
 * </ul>
 *
 * <p>「登记但未执行」不使用本枚举——用 manifest 的 {@code registeredOnlyTestIds}
 * 显式登记归属（宿主/跨仓），避免以枚举缺省值冒充执行。</p>
 */
public enum TestOutcome {
    PASSED, FAILED, SKIPPED
}
