package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * LOCAL_OBJECT 许可载体默认实现（package-private 构造：只有
 * {@link LocalPermitIssuer} 能创建，AA-04 §4.2 受控要求）。
 *
 * <p>类型证明语义：持有本载体即「经过受控签发路径」，未走
 * {@code PermitIssuer} 的代码无法构造本类型。载体不缓存、不改写许可内容；
 * 消费方仍必须经 {@code ExecutionPermitVerifier} 验证。</p>
 */
public final class LocalObjectPermitCarrier implements ExecutionPermitCarrier {

    private final ExecutionPermit permit;

    LocalObjectPermitCarrier(ExecutionPermit permit) {
        this.permit = java.util.Objects.requireNonNull(permit, "permit 必选");
    }

    /** 受载体保护的完整许可（供验证器/编排读取，不可变）。 */
    public ExecutionPermit permit() {
        return permit;
    }

    @Override
    public CallerContext caller() {
        return permit.caller();
    }
}
