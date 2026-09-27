package com.gallop.fastddd.identity.core.domain;

/**
 * 本地生命周期管理操作（W3 设计 §9.3，W3-R1）：{@code IdentityAdminPolicy}
 * 逐操作裁决的操作枚举。M1 冻结三个操作；新增操作 = 契约变更，须评审。
 *
 * @author gallop
 * @since 1.1.0
 */
public enum IdentityAdminOperation {

    /** 本地受控停用（T4：DISABLED + epoch+1 + 全会话撤销，同事务）。 */
    DEACTIVATE,

    /** 本地受控启用（逆操作，独立裁决）。 */
    REACTIVATE,

    /** 手动触发目录同步（交互式触发可复用策略裁决；无 ActorRef 缺省拒绝）。 */
    SYNC_TRIGGER
}
