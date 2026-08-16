package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;

/**
 * 人工/SystemJob 路径身份桥接 Port（宿主实现）。
 *
 * <p>职责：把宿主<b>既有安全上下文</b>（已认证的用户会话）或<b>显式注册的
 * SYSTEM_JOB 工作负载</b>适配为 {@code CallerContext}，供非 Agent 路径复用
 * 统一责任链语义。这不是新的认证机制——桥接只做「已验证事实的适配」，
 * 不做认证本身。</p>
 *
 * <p>硬约束：</p>
 * <ul>
 *   <li><b>不得作为 Agent 路径的 Caller 来源</b>（AST-04 补充负向）：Agent 请求
 *       经本桥接取得人工归因必须被拒绝——Agent 路径唯一入口是
 *       {@link VerifiedCaller}；</li>
 *   <li>宿主侧无已验证上下文时必须抛 {@link CallerAuthenticationException}，
 *       不得构造匿名/{@code system} 兜底 principal（Contract §5.1 不变量 3）；</li>
 *   <li>SYSTEM_JOB 只能桥接宿主显式注册的工作负载（不变量 4：显式、可审计）。</li>
 * </ul>
 */
public interface CallerIdentityBridge {

    /**
     * 桥接当前宿主已验证安全上下文为调用责任链。
     *
     * @return 从已验证事实适配的 CallerContext
     * @throws CallerAuthenticationException 无已验证上下文，或请求的归因类型未被显式注册
     */
    CallerContext bridgeCurrentCaller();
}
