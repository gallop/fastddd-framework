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
    SYNC_TRIGGER,

    /**
     * FAILED 终态事件显式重放（W5 设计 §5.1 管理面，W5-d）：复位 RECEIVED、
     * attempts 归零、租约/错误清空。无固定目标主体（{@code targetPrincipalId=null}
     * ——SYNC_TRIGGER 同式，冻结 {@code IdentityAdminPolicy} 签名零变更）。
     * <b>枚举扩项 = 契约变更</b>（本类冻结注记），W5-d 差异登记在案。
     */
    INBOX_REPLAY,

    /**
     * 手动触发目录事实回收（W5 设计 §8.3 M-26 管理入口先例，W5-f f-1）：preview
     * 同操作位（{@code RetentionReport.preview} 标志区分形态）。无固定目标主体
     * （{@code targetPrincipalId=null}——SYNC_TRIGGER/INBOX_REPLAY 同式，
     * {@code IdentityAdminPolicy} 签名零变更）。<b>枚举扩项 = 契约变更</b>
     * （本类冻结注记），W5-f 设计 O-4 登记 + v2 窄面复评通过在案（W5-d D15
     * INBOX_REPLAY 先例）；W5-f f-2 的 recheck 操作位另扩。
     */
    RETENTION_RUN
}
