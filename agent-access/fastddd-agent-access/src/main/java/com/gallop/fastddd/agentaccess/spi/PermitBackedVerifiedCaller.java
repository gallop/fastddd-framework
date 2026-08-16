package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * {@link VerifiedCaller} 最小参考实现：直接以许可携带的已验证 CallerContext
 * 作为 Agent 路径 Caller。
 *
 * <p>许可缺失或未携带 caller 时拒绝（fail-closed，无匿名兜底）。完整许可验证
 * （时间窗、audience、digest 一致性）在 FDD-AA-11/12 编排层叠加，实现不得
 * 在此处放松「caller 来源唯一且已验证」的语义。</p>
 */
public final class PermitBackedVerifiedCaller implements VerifiedCaller {

    @Override
    public CallerContext fromVerifiedPermit(ExecutionPermitCarrier permit) {
        if (permit == null) {
            throw new CallerAuthenticationException("Agent 路径拒绝：许可缺失，无已验证 caller 来源");
        }
        CallerContext caller = permit.caller();
        if (caller == null) {
            throw new CallerAuthenticationException("Agent 路径拒绝：许可未携带已验证 caller");
        }
        return caller;
    }
}
