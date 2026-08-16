package com.gallop.fastddd.agentaccess.contract;

/**
 * 代表 Subject 行动的逻辑主体类型（Contract §5.1）。
 *
 * <ul>
 *   <li>{@link #HUMAN}：人直接操作</li>
 *   <li>{@link #AGENT}：稳定 Agent ID 代表 Subject 行动（Agent 场景）</li>
 *   <li>{@link #WORKLOAD}：常规服务工作负载</li>
 *   <li>{@link #SYSTEM_JOB}：显式、可审计的系统任务类型——不是匿名兜底
 *       （Contract §5.1 不变量 4：必须显式注册、显式使用）</li>
 * </ul>
 */
public enum ActorType {

    HUMAN,

    AGENT,

    WORKLOAD,

    SYSTEM_JOB
}
