package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;

/**
 * 登出用例（T3）：撤销单会话，幂等。
 *
 * @author gallop
 * @since 1.1.0
 */
public class LogoutUseCase {

    private final LocalSessionPort sessionPort;
    private final IdentityTransactionRunner runner;

    public LogoutUseCase(LocalSessionPort sessionPort, IdentityTransactionRunner runner) {
        this.sessionPort = sessionPort;
        this.runner = runner;
    }

    public void logout(SessionId sessionId) {
        runner.inTransaction(() -> {
            sessionPort.revokeSession(sessionId);
            return null;
        });
    }
}
