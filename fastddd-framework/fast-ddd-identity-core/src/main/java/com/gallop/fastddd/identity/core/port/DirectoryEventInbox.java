package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.EventEnvelope;

import java.time.Duration;
import java.util.List;

/**
 * 入站事件收件箱端口（W5 设计 §3.3/§4.1/§5.1，W5-b 签名冻结）：飞书事件订阅
 * 持久化接收 + 租约式消费的状态机载体。先落 Inbox 后 ACK（崩溃不丢事件），
 * at-least-once 由上游重投与本侧幂等共同承载。
 *
 * <p>消费状态机：RECEIVED →（{@link #claim} 置 PROCESSING + 租约）→
 * {@link #markProcessed} / {@link #markFailed}；租约到期未终态的行由
 * {@link #reclaimExpired} 复位 RECEIVED、attempts+1；attempts ≥ 上限 →
 * FAILED 终态（不再自动重试，管理端显式重放入口）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryEventInbox {

    /**
     * 持久化接收一行（eventId 主键幂等；单短事务完成写入，设计 §4.1 :176——
     * 不依赖调用方事务）。
     *
     * @return true = 新写入；false = eventId 已存在（重复投递——<b>调用方仍须 ACK</b>，
     *         不抛异常、不触发再处理）
     */
    boolean persist(EventEnvelope envelope);

    /**
     * 择取一批待处理信封：置 PROCESSING、记 claimed_at 与
     * lease_expires_at = now + lease（短事务完成置位，处理不占该事务）。
     */
    List<EventEnvelope> claim(int limit, Duration lease);

    /**
     * 崩溃恢复：复位租约已过期的 PROCESSING 行 → RECEIVED、attempts+1；
     * attempts 达上限的行置 FAILED 终态。
     *
     * @return 本轮复位为 RECEIVED 的行数
     */
    int reclaimExpired();

    /** 处理成功终态（processed_at 定值、清 claimed_at/lease_expires_at 置位；行已终态则幂等空操作）。 */
    void markProcessed(String eventId);

    /**
     * 处理失败登记：记 last_error、attempts+1、清 claimed_at/lease_expires_at 置位；
     * 未达上限回 RECEIVED 待再消费，达上限置 FAILED 终态。
     */
    void markFailed(String eventId, String reason);
}
