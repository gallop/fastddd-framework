package com.gallop.fastddd.identity.spring.bridge;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.port.ActorResolver;

/**
 * 身份模式 CRUD 自动填充桥（v0.2 §5）：实现 crud 既有 {@link CurrentUserProvider}
 * 契约（String 协议不变），返回 principalId（协议 ≤50，恰在审计 username 界内）。
 *
 * <p>员工身份缺失**拒绝**（抛 {@link com.gallop.fastddd.identity.core.error.IdentityRequiredException}），
 * 不降级 {@code system}——与 {@code DefaultCurrentUserProvider}/{@code SecurityCurrentUserProvider}
 * 的回退语义是 W2-1 的行为分界。C/identity Worker 装配，经 §4.5 显式自动配置顺序
 * 优先于两者；宿主自定义 provider 仍可覆盖（稳定 Actor 契约）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityCurrentUserProvider implements CurrentUserProvider {

    private final ActorResolver actorResolver;

    public IdentityCurrentUserProvider(ActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    @Override
    public String getCurrentUserId() {
        return actorResolver.currentActor().principalId().value();
    }
}
