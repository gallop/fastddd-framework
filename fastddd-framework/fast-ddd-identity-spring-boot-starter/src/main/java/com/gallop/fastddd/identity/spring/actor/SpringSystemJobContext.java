package com.gallop.fastddd.identity.spring.actor;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.function.Supplier;

/**
 * Spring 版系统作业上下文（v0.2 §5.6）：在 core 的 ThreadLocal 栈语义之上，
 * 额外保存/恢复 {@link SecurityContext} 并安装 name=principalId 的
 * {@link IdentityAuthenticationToken}（框架操作日志/切面在身份模式下取到一致 Actor）。
 *
 * <p>finally 严格恢复外层上下文；线程池任务不继承（ThreadLocal 天然隔离），
 * 每次入口先经 core 协议校验（SYSTEM_JOB 类型 + system-job: 前缀 + ≤50）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class SpringSystemJobContext {

    /** SYSTEM_JOB 非主体库常驻主体（无 principal 行），租户按系统域固定值。 */
    static final String SYSTEM_TENANT = "system";

    private SpringSystemJobContext() {
    }

    /** 在系统作业主体下执行动作；嵌套恢复、异常恢复（finally 双层）。 */
    public static <T> T run(PrincipalId systemJobPrincipalId, Supplier<T> action) {
        Actor actor = new Actor(SYSTEM_TENANT, systemJobPrincipalId,
                com.gallop.fastddd.identity.core.domain.PrincipalType.SYSTEM_JOB);
        SecurityContext outer = SecurityContextHolder.getContext();
        return SystemJobPrincipalContext.run(actor, () -> {
            try {
                SecurityContext jobContext = SecurityContextHolder.createEmptyContext();
                jobContext.setAuthentication(IdentityAuthenticationToken.forSystemJob(actor));
                SecurityContextHolder.setContext(jobContext);
                return action.get();
            } finally {
                SecurityContextHolder.setContext(outer);
            }
        });
    }

    /** 便捷重载（Runnable）。 */
    public static void run(PrincipalId systemJobPrincipalId, Runnable action) {
        run(systemJobPrincipalId, () -> {
            action.run();
            return null;
        });
    }
}
