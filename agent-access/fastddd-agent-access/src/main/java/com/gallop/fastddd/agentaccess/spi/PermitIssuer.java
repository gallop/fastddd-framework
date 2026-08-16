package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * 本地受控许可签发入口（AA-04 §4.2：受控实现 {@code .permit}，禁止宿主自造
 * 第二实现——kit Validator 校验容器内本类型 Bean 恰好一个）。
 *
 * <p>职责：把完整许可包装为 LOCAL_OBJECT 受控 carrier。carrier 的 package-private
 * 构造在无 JPMS 模块封装的 classpath 环境下只是编译期包边界（同包名代码理论上
 * 可绕过），「经过受控签发」的类型证明据此降级为约定 + 包可见性/审查兜底，
 * 与 {@code .permit.LocalObjectPermitCarrier} 的如实口径一致。签发动作的审计
 * 事件由编排层叠加（FDD-AA-13），本接口不产出审计副作用。</p>
 *
 * <p>注意：签发不是验证。carrier 的消费方仍必须经
 * {@link ExecutionPermitVerifier} 的 fail-closed 验证后才能进入两段式授权。</p>
 */
public interface PermitIssuer {

    /**
     * 以完整许可构造 LOCAL_OBJECT 受控载体。
     *
     * @param permit 结构完整的许可（非 null）
     * @return 受控 carrier
     * @throws IllegalArgumentException 许可缺失
     */
    ExecutionPermitCarrier issue(ExecutionPermit permit);
}
