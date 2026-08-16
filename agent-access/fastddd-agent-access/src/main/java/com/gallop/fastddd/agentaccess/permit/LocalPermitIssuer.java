package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;
import com.gallop.fastddd.agentaccess.spi.PermitIssuer;

/**
 * {@link PermitIssuer} 受控唯一实现（AA-04 §4.2：禁止宿主自造第二实现，
 * kit Validator 校验容器内 PermitIssuer Bean 恰好一个）。
 *
 * <p>签发审计事件由编排层叠加（FDD-AA-13）；本实现只固定「唯一构造入口 +
 * package-private 载体」的结构约束。</p>
 */
public final class LocalPermitIssuer implements PermitIssuer {

    @Override
    public ExecutionPermitCarrier issue(ExecutionPermit permit) {
        if (permit == null) {
            throw new IllegalArgumentException("permit 必选：无许可即无签发");
        }
        return new LocalObjectPermitCarrier(permit);
    }
}
