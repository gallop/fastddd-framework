package com.gallop.fastddd.identity.spring.actor;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;

/**
 * identity 构造的 {@link Authentication}（v0.2 §5）：
 *
 * <ul>
 *   <li>principal 对象 = {@link VerifiedPrincipal}（会话复验产物）或 {@link Actor}
 *       （SYSTEM_JOB 上下文产物）；</li>
 *   <li>{@code getName()} = principalId——这是**装配结果**而非被依赖的偶然：
 *       业务代码取稳定标识必须经 CurrentUserProvider / ActorResolver（§5 规约）；
 *       A/B/D 模式 getName() 语义不变（旧消费者兼容）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;

    private final String name;

    private IdentityAuthenticationToken(Object principal, PrincipalId principalId) {
        super(Collections.emptyList());
        this.principal = principal;
        this.name = principalId.value();
        setAuthenticated(true);
    }

    /** C 模式会话复验通过：principal=VerifiedPrincipal，name=principalId。 */
    public static IdentityAuthenticationToken forSession(VerifiedPrincipal verified) {
        return new IdentityAuthenticationToken(verified, verified.principalId());
    }

    /** SYSTEM_JOB 上下文：principal=Actor，name=principalId（§5.6 框架操作日志兼容）。 */
    public static IdentityAuthenticationToken forSystemJob(Actor actor) {
        return new IdentityAuthenticationToken(actor, actor.principalId());
    }

    @Override
    public Object getCredentials() {
        return ""; // 不透明会话不由 token 携带（cookie 每请求直查权威状态）
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return name;
    }
}
