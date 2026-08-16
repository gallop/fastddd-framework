package com.gallop.fastddd.agentaccess.arch.violation;

import com.gallop.fastddd.agentaccess.gateway.DefaultAgentAccessGateway;

/**
 * 违例样例：宿主业务代码直接依赖 .gateway 实现类（绕过 .spi 接缝；仅测试导入用）。
 */
public final class HostDependingOnGatewayImplementation {

    private HostDependingOnGatewayImplementation() {
    }

    /** 违例依赖：业务包不得引用网关实现，只允许 AgentAccessGateway 接缝。 */
    @SuppressWarnings("unused")
    static DefaultAgentAccessGateway illegalReference() {
        throw new UnsupportedOperationException("违例样例");
    }
}
