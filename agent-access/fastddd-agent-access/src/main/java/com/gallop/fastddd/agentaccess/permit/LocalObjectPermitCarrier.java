package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * LOCAL_OBJECT 许可载体默认实现（package-private 构造：只有
 * {@link LocalPermitIssuer} 能创建，AA-04 §4.2 受控要求）。
 *
 * <p>类型证明语义（如实标注）：持有本载体即「来自 {@link LocalPermitIssuer}
 * 受控签发路径或同包代码」——package-private 构造在<b>无 JPMS 模块封装</b>的
 * classpath 环境下只是编译期包边界，同包名类理论上可绕过（模块化封装属后续
 * 版本决策，不在此提前冻结）。骨架期依赖包可见性 + ArchUnit/审查兜底；
 * 载体不缓存、不改写许可内容，消费方仍必须经 {@code ExecutionPermitVerifier}
 * 验证——载体不是验证的替代。</p>
 */
public final class LocalObjectPermitCarrier implements ExecutionPermitCarrier {

    private final ExecutionPermit permit;

    LocalObjectPermitCarrier(ExecutionPermit permit) {
        if (permit == null) {
            throw new IllegalArgumentException("permit 必选");
        }
        this.permit = permit;
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
