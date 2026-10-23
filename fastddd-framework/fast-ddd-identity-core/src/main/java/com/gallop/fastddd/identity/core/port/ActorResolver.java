package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;

import java.util.Optional;

/**
 * 统一 Actor 解析端口（v0.2 §5.6）：CRUD 自动填充与身份模式审计共用。
 *
 * @author gallop
 * @since 1.1.0
 */
public interface ActorResolver {

    /**
     * 解析当前 Actor。
     *
     * @throws IdentityRequiredException 员工身份缺失（身份模式拒绝降级为 system）
     */
    Actor currentActor();

    /** 尝试解析（不抛；审计等可选场景使用）。 */
    Optional<Actor> tryCurrentActor();
}
