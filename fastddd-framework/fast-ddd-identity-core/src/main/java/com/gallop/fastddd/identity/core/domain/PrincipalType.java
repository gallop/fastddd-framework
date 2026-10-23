package com.gallop.fastddd.identity.core.domain;

/**
 * 主体类型三分（v0.2 §5.1）：人类员工 / 系统作业 / 框架内部系统兜底。
 *
 * @author gallop
 * @since 1.1.0
 */
public enum PrincipalType {

    /** 人类员工主体（外部登录绑定，principalId 为标准 UUID 文本）。 */
    HUMAN,

    /** 系统作业主体（{@code system-job:} 前缀 + job-key，显式系统上下文使用）。 */
    SYSTEM_JOB,

    /** 框架内部系统兜底主体（常量 {@code system}，仅显式系统上下文使用）。 */
    SYSTEM
}
