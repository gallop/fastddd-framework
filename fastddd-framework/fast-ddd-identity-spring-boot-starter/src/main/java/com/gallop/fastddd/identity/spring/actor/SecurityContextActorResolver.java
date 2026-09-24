package com.gallop.fastddd.identity.spring.actor;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Spring 环境统一 Actor 解析（v0.2 §5.6）：CRUD 自动填充与身份模式审计共用。
 *
 * <p>优先级：显式 SYSTEM_JOB 上下文（栈顶）→ SecurityContext 中 identity 构造的
 * Authentication（principal=VerifiedPrincipal）。两者皆无 = 身份缺失：
 * {@link #currentActor()} 抛 {@link IdentityRequiredException}（身份模式拒绝降级
 * {@code system}），{@link #tryCurrentActor()} 返回 empty。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class SecurityContextActorResolver implements ActorResolver {

    @Override
    public Actor currentActor() {
        return tryCurrentActor().orElseThrow(() -> new IdentityRequiredException(
                "当前操作缺少员工/系统身份：身份模式拒绝降级为 system。"
                        + "Web 请求须携带有效会话；后台任务须用 SpringSystemJobContext 显式声明系统作业主体。"));
    }

    @Override
    public Optional<Actor> tryCurrentActor() {
        Optional<Actor> systemJob = SystemJobPrincipalContext.currentActor();
        if (systemJob.isPresent()) {
            return systemJob;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthenticationToken token
                && authentication.isAuthenticated()
                && token.getPrincipal() instanceof VerifiedPrincipal verified) {
            return Optional.of(new Actor(verified.tenantId(), verified.principalId(),
                    verified.principalType()));
        }
        return Optional.empty();
    }
}
