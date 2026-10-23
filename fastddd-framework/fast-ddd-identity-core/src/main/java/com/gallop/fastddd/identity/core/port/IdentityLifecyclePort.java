package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;

/**
 * 主体生命周期端口（v0.2 §6，T4）：明确停用 = 锁主体 + 条件 UPDATE（status/epoch+1）
 * + 撤销全部会话，同一事务。
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityLifecyclePort {

    /**
     * 停用：已 DISABLED 幂等成功（返回现状）；主体不存在抛 NotFound（不冒充停用成功）。
     * 重新启用：epoch 再递增、旧会话保持撤销（不复活）。
     */
    VerifiedPrincipal deactivatePrincipal(String principalId);

    /** 重新启用：不复活旧会话（停用时已撤销；epoch 再递增保证任何遗留快照失配）。 */
    VerifiedPrincipal reactivatePrincipal(String principalId);
}
