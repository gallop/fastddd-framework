package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.port.IdentityLifecyclePort;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;

/**
 * 本地受控停用/启用用例（T4，§9.2）：停用 = 锁主体 + 条件 UPDATE（status/epoch+1）
 * + 撤销全部会话，同一事务；已 DISABLED 幂等；不存在 NOT_FOUND。
 *
 * @author gallop
 * @since 1.1.0
 */
public class PrincipalLifecycleUseCase {

    private final IdentityLifecyclePort lifecyclePort;
    private final IdentityTransactionRunner runner;

    public PrincipalLifecycleUseCase(IdentityLifecyclePort lifecyclePort, IdentityTransactionRunner runner) {
        this.lifecyclePort = lifecyclePort;
        this.runner = runner;
    }

    public VerifiedPrincipal deactivate(String principalId) {
        return runner.inTransaction(() -> lifecyclePort.deactivatePrincipal(principalId));
    }

    public VerifiedPrincipal reactivate(String principalId) {
        return runner.inTransaction(() -> lifecyclePort.reactivatePrincipal(principalId));
    }
}
