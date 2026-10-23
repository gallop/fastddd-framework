package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryGenerationActivatedEvent;

/**
 * 目录变更通知端口（W3 设计 §5.3——<b>签名冻结，实现 M2/W5</b>）。
 *
 * <p><b>M1 状态：通知一律关闭</b>——T7 无 T8 子步骤、无 Outbox 适配、
 * <b>无本端口的运行时依赖</b>、无通知开关键。全量发布在零 Outbox 环境可完整
 * 完成（矩阵 26）。M1 装配不要求也不装配任何本端口实现。</p>
 *
 * <p><b>M2/W5 适配契约（冻结）</b>：</p>
 * <ul>
 *   <li>宿主组合根提供适配（绑定框架 {@code fast-ddd-event} 的
 *       {@code OutboxEventService}），声明与身份模块同一 DataSource/
 *       TransactionManager——资源不一致启动失败；</li>
 *   <li>{@link #publishGenerationActivated} 是 T7 的<b>同事务子步骤</b>
 *       （T8）：入库失败抛出使 T7 回滚；<b>不允许 afterCommit 回调当交接</b>；</li>
 *   <li>幂等由 Outbox 唯一约束承载（eventId 规则见
 *       {@link DirectoryGenerationActivatedEvent#EVENT_ID_PREFIX}；
 *       至少一次投递 + 消费端幂等）；</li>
 *   <li>changeLogRef 须指向不可变变更日志载体条目（§5.3.3：引用列 ≠ 日志本身，
 *       载体交付前该字段不写）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityChangePublisher {

    /** 代激活通知（M2：T7 同事务；M1 无调用方）。 */
    void publishGenerationActivated(DirectoryGenerationActivatedEvent event);
}
