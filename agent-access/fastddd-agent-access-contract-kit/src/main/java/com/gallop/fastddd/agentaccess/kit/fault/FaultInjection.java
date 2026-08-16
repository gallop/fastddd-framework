package com.gallop.fastddd.agentaccess.kit.fault;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AST-01 故障注入工具：宿主在治理 Port 上注入「依赖不可用」形态，证明
 * fail-closed（拒绝且禁止任何降级放行）。AA-03 §3 AST-01：PDP 超时/
 * INDETERMINATE、Verifier Bean 缺失、DB/Outbox 断开三类。
 *
 * <p>用法（宿主测试内联到任意函数式 Port）：</p>
 * <pre>{@code
 * // 调用即故障（PDP 超时/DB 断开形态）
 * TargetAdmissionAuthorizationPort port = q -> FaultInjection.alwaysFails(
 *         new IllegalStateException("simulated pdp timeout")).get();
 * // 前 N 次正常，之后故障（运行中断连形态）
 * OutcomeDedupPort dedup = key -> FaultInjection.failsAfter(3,
 *         () -> delegate.complete(key, receipt), new IllegalStateException("db gone")).get();
 * }</pre>
 *
 * <p>返回 {@code INDETERMINATE} 形态无需本工具（lambda 直接构造
 * {@code AuthorizationDecision} 即可）。本工具只做「抛错类」故障的
 * 计数与复用；不吞异常、不重试、不降级——被测编排必须把它当真实故障收敛。</p>
 */
public final class FaultInjection {

    private FaultInjection() {
    }

    /** 每次调用都抛给定故障（超时/断连/崩溃形态） */
    public static <T> Supplier<T> alwaysFails(RuntimeException failure) {
        requireFailure(failure);
        return () -> {
            throw failure;
        };
    }

    /**
     * 前 {@code successfulCallsBeforeFailure} 次返回委托值，之后每次抛故障
     * （运行中途依赖断开形态）。委托在第 N+1 次起不再被调用。
     */
    public static <T> Supplier<T> failsAfter(int successfulCallsBeforeFailure,
                                             Supplier<T> delegate, RuntimeException failure) {
        requireFailure(failure);
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 必选（正常路径委托）");
        }
        if (successfulCallsBeforeFailure < 0) {
            throw new IllegalArgumentException("successfulCallsBeforeFailure 不得为负");
        }
        AtomicInteger remaining = new AtomicInteger(successfulCallsBeforeFailure);
        return () -> {
            if (remaining.getAndDecrement() > 0) {
                return delegate.get();
            }
            throw failure;
        };
    }

    private static void requireFailure(RuntimeException failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure 必选（注入的故障形态）");
        }
    }
}
