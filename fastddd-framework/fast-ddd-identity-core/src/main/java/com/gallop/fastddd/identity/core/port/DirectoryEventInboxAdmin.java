package com.gallop.fastddd.identity.core.port;

/**
 * 事件收件箱管理端口（W5 设计 §5.1/管理面，W5-d）：FAILED 终态事件的
 * 显式重放入口（达 attempts 上限的行不自动重试——管理裁决替代自动重试）。
 *
 * <p>设计 §3 模块表未列本端口（W5-d 差异登记）：{@link DirectoryEventInbox}
 * （W5-b 冻结签名）无重放面——冻结不动、管理面新端口承载。</p>
 *
 * <p><b>重放语义</b>：FAILED → RECEIVED，attempts 归零、租约列与 last_error
 * 清空——事件重新进入 claim 候选集（幂等重放安全：态前置限定 FAILED）。
 * 授权/审计归宿主管理端（操作枚举 {@code INBOX_REPLAY}，经
 * {@code IdentityAdminPolicy} 逐操作裁决——同 W3 §9.3 管理端纪律）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryEventInboxAdmin {

    /**
     * 重放指定事件。
     *
     * @return true = 已复位 RECEIVED；false = 行不存在或非 FAILED 态
     *         （不可重放态幂等拒绝，调用方按 404 语义处置）
     */
    boolean replay(String eventId);
}
