package com.gallop.fastddd.agentaccess.spi;

/**
 * 调用路径分流（Contract §5.1 不变量 3/4 的入口治理语义）。
 *
 * <p>三路径与 {@code CallerContext} 的构造入口一一对应，不得交叉：</p>
 * <ul>
 *   <li>{@link #HUMAN}：人工路径。{@code CallerContext} 经
 *       {@link CallerIdentityBridge} 从宿主<b>已验证</b>安全上下文桥接取得。</li>
 *   <li>{@link #SYSTEM_JOB}：显式注册的系统任务路径。同样经
 *       {@link CallerIdentityBridge} 桥接，但必须是宿主显式注册的工作负载类型
 *       （可审计，不是匿名兜底）。</li>
 *   <li>{@link #AGENT}：Agent 路径。<b>唯一</b>构造入口是
 *       {@link VerifiedCaller#fromVerifiedPermit}；禁止经桥接取得人工归因，
 *       禁止未认证降级为匿名 {@code system} principal。</li>
 * </ul>
 *
 * <p>本枚举只表达入口分流语义；{@code ActorType}（wire 层）表达责任链主体类型，
 * 二者不互相推导映射。</p>
 */
public enum CallerKind {

    HUMAN,

    SYSTEM_JOB,

    AGENT
}
