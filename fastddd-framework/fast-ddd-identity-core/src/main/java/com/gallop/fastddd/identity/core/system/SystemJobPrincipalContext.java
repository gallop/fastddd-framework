package com.gallop.fastddd.identity.core.system;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 系统作业主体上下文（v0.2 §5.6）：SYSTEM_JOB 上下文显式设置、栈式嵌套、finally 恢复。
 *
 * <p>纯 {@link ThreadLocal} 栈实现——线程池任务不继承员工身份（ThreadLocal 跨任务/跨池
 * 天然隔离；每次 {@link #run} 入口先防御性清理，确保连续任务不串身份）。
 * 执行人与业务发起人分离：本上下文只承载"当前执行的系统作业"，发起人由宿主在
 * 业务参数中显式传递，不从上下文推断。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class SystemJobPrincipalContext {

    private static final ThreadLocal<Deque<Actor>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private SystemJobPrincipalContext() {
    }

    /**
     * 在系统作业主体下执行动作：校验 SYSTEM_JOB 类型与 principalId 协议，入栈执行，
     * finally 恢复外层（嵌套）或清空（顶层）。
     */
    public static <T> T run(Actor systemJobActor, Supplier<T> action) {
        if (systemJobActor == null || systemJobActor.principalType() != PrincipalType.SYSTEM_JOB) {
            throw new IllegalArgumentException(
                    "SystemJobPrincipalContext 只接受 SYSTEM_JOB 主体（收到："
                            + (systemJobActor == null ? "null" : systemJobActor.principalType()) + "）");
        }
        // 协议复核（含 system-job: 前缀与 ≤50 上限）
        PrincipalId.forSystemJob(stripPrefix(systemJobActor.principalId().value()));

        Deque<Actor> stack = STACK.get();
        stack.push(systemJobActor);
        try {
            return action.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                STACK.remove();
            }
        }
    }

    /** 当前系统作业 Actor（无则 empty）。 */
    public static Optional<Actor> currentActor() {
        return Optional.ofNullable(STACK.get().peek());
    }

    /** 防御性清空（异常路径兜底；run 的 finally 已保证正常清理）。 */
    public static void clear() {
        STACK.remove();
    }

    private static String stripPrefix(String principalIdValue) {
        if (!principalIdValue.startsWith(PrincipalId.SYSTEM_JOB_PREFIX)) {
            throw new IllegalArgumentException(
                    "SYSTEM_JOB principalId 须以 '" + PrincipalId.SYSTEM_JOB_PREFIX + "' 开头：" + principalIdValue);
        }
        return principalIdValue.substring(PrincipalId.SYSTEM_JOB_PREFIX.length());
    }
}
