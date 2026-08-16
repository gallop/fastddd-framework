package com.gallop.fastddd.agentaccess.contract;

/**
 * 最终承担业务责任的主体类型（Contract §5.1）。
 *
 * <ul>
 *   <li>{@link #HUMAN}：自然人主体</li>
 *   <li>{@link #SERVICE}：服务主体（机器身份承担业务责任）</li>
 *   <li>{@link #ORGANIZATION}：组织主体</li>
 * </ul>
 */
public enum SubjectType {

    HUMAN,

    SERVICE,

    ORGANIZATION
}
