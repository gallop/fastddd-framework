package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;

/**
 * Agent 路径唯一 {@code CallerContext} 构造入口（AA-04 §9 / Contract §5.1 不变量 1）。
 *
 * <p>只允许从<b>已验证许可</b>提取 CallerContext；请求体/payload 自报的身份字段
 * 一律不采信。许可缺失、许可未携带已验证 caller、或 caller 构造非法时必须以
 * {@link CallerAuthenticationException} 拒绝——不得降级、不得匿名兜底。</p>
 *
 * <p>实现约束：</p>
 * <ul>
 *   <li>实现由宿主/编排层显式装配（kit Validator 必需 Bean 之一）；</li>
 *   <li>Agent 路径禁止改用 {@link CallerIdentityBridge} 取得人工归因
 *       （AST-04 补充负向：该组合必须被装配规则与运行时同时拒绝）；</li>
 *   <li>许可的完整验证（时间窗、audience、digest 一致性等）属 FDD-AA-11/12
 *       编排层，本接口骨架期只固定「caller 来源唯一且已验证」这一最小语义。</li>
 * </ul>
 */
public interface VerifiedCaller {

    /**
     * 从已验证许可提取调用责任链。
     *
     * @param permit 已通过上游治理验证的许可载体
     * @return 许可携带的已验证 CallerContext
     * @throws CallerAuthenticationException 许可缺失、未携带已验证 caller 或 caller 非法
     */
    CallerContext fromVerifiedPermit(ExecutionPermitCarrier permit);
}
