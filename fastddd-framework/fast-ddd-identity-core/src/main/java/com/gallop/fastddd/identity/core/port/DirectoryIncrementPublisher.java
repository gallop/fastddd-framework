package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryIncrementEvent;

/**
 * 目录增量通知端口（W5 设计 §3.3/§5.3，W5-b 签名冻结）：镜像
 * {@code IdentityChangePublisher}（代激活事件）的 M2 能力分离先例——增量
 * 组织变化通知走独立端口，由宿主适配到 Outbox。
 *
 * <p>适配契约（与 IdentityChangePublisher 同款）：<b>同一事务内</b>调用、
 * 先库后信——Outbox 行入库失败须抛出使落库事务整体回滚（不产生已提交库态
 * 而无通知）；<b>禁 afterCommit</b>（先库后信单事务两写，不注册事务后回调）；
 * 至少一次投递 + 消费端幂等由 Outbox 唯一约束承载（eventId 规则 =
 * {@code dir-change:{scope}:{committed_seq}:{type}}，见
 * {@link DirectoryIncrementEvent#eventIdFor}）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryIncrementPublisher {

    /** 发布一枚增量事件（事务内调用；changeLogRef 须指向不可变载体条目）。 */
    void publishDirectoryChanged(DirectoryIncrementEvent event);
}
