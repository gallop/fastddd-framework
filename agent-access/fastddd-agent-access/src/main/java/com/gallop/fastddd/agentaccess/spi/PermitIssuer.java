package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * 本地受控许可签发入口（AA-04 §4.2：受控实现 {@code .permit}，禁止宿主自造
 * 第二实现——kit Validator 校验容器内本类型 Bean 恰好一个）。
 *
 * <p>职责：把完整许可包装为 LOCAL_OBJECT 受控 carrier——carrier 实现类构造
 * 是 package-private 的，只有受控实现能创建，因此「流转中的 carrier」即
 * 「经过受控签发」的类型证明。签发动作的审计事件由编排层叠加（FDD-AA-13），
 * 本接口不产出审计副作用。</p>
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
