package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.PrincipalId;

/**
 * 本地生命周期管理授权策略端口（W3 设计 §9.3，W3-R1）：
 * 管理控制器/手动触发入口在<b>每次操作执行前</b>逐操作裁决（非登录时快照）。
 *
 * <p><b>框架不提供默认实现</b>：宿主组合根必须显式提供 bean；
 * {@code fastddd.identity.admin.enabled=true} 而缺本接口实现 = 启动失败
 * （fail-closed，对齐 W21-01 先例）——不默认放行。</p>
 *
 * <p>裁决为纯判定（无事务、无 IO 副作用）；拒绝 →
 * {@code IdentityAdminDeniedException}（403 语义），操作不得执行。
 * 策略不进核心用例（系统调用直连核心用例，走 SYSTEM_JOB + 审计，W3 设计 §9.3）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityAdminPolicy {

    /**
     * 裁决操作许可。
     *
     * @param operation 操作（DEACTIVATE/REACTIVATE/SYNC_TRIGGER）
     * @param actor 发起者（HTTP 会话经 ActorResolver 解析；交互式手动触发
     *              传入 ActorRef；无 ActorRef 缺省拒绝）
     * @param targetPrincipalId 操作目标主体（可 null：无固定目标的操作）
     * @throws com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException 拒绝
     */
    void checkPrincipalAdmin(IdentityAdminOperation operation, Actor actor, PrincipalId targetPrincipalId);
}
