package com.gallop.fastddd.identity.spring.bridge;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.security.audit.AuditActorProvider;

/**
 * 统一 Actor 的审计提供者（v0.2 §5.6 / W2-1 修复轮 W21-04）：把
 * {@code @AuditLog} 切面的执行者解析接到统一 {@link ActorResolver}——
 * SYSTEM_JOB 栈优先，其次会话 Authentication；身份缺失抛
 * {@code IdentityRequiredException}（业务执行前拒绝，不降 anonymous）。
 * CRUD 自动填充与业务审计自此共用同一 Actor 来源。
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityAuditActorProvider implements AuditActorProvider {

    private final ActorResolver actorResolver;

    public IdentityAuditActorProvider(ActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public AuditActor resolveActor() {
        Actor actor = actorResolver.currentActor();
        return new AuditActor(actor.tenantId(), actor.principalId().value(),
                actor.principalType().name());
    }
}
