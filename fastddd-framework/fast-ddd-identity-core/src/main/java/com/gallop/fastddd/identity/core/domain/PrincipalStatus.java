package com.gallop.fastddd.identity.core.domain;

/**
 * 主体状态（本地权威，v0.2 §7）：停用 = 条件 UPDATE + authEpoch 递增 + 撤销全部会话（同一事务）。
 *
 * @author gallop
 * @since 1.1.0
 */
public enum PrincipalStatus {

    /** 活跃：可签发/复验会话。 */
    ACTIVE,

    /** 停用：全部会话已撤销；重新启用不复活旧会话（epoch 再递增）。 */
    DISABLED
}
