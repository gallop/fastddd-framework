package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 代激活事件（W3 设计 §5.3 通知协议冻结，M2/W5 实现）：新活动代发布完成的
 * 通知负载。<b>M1 不发布、不装配、无运行时依赖</b>——本类型与
 * {@code IdentityChangePublisher} 一起冻结签名，供 M2 适配到宿主 Outbox。
 *
 * <p>eventId 规则（冻结）：{@code dir-gen-activated:{scope}:{generationId}}；
 * changeLogRef 必须指向真正不可变的变更日志载体条目（§5.3.3：引用列 ≠ 日志本身，
 * 载体交付前该字段不写）。投递语义：至少一次 + 消费端幂等（Outbox 唯一约束承载）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryGenerationActivatedEvent {

    /** eventId 规则前缀（§5.3.4 冻结）。 */
    public static final String EVENT_ID_PREFIX = "dir-gen-activated:";

    private final String eventId;
    private final DirectoryScope scope;
    private final String generationId;
    private final long committedSeq;
    private final String changeLogRef;

    public DirectoryGenerationActivatedEvent(String eventId,
                                             DirectoryScope scope,
                                             String generationId,
                                             long committedSeq,
                                             String changeLogRef) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.committedSeq = committedSeq;
        this.changeLogRef = changeLogRef;
    }

    /** {@link #EVENT_ID_PREFIX}{scope}:{generationId}。 */
    public static String eventIdFor(DirectoryScope scope, String generationId) {
        return EVENT_ID_PREFIX + scope + ":" + generationId;
    }

    public String eventId() {
        return eventId;
    }

    public DirectoryScope scope() {
        return scope;
    }

    public String generationId() {
        return generationId;
    }

    /** 发布事务提交后的水位（H_after；活动代发布声明只到 ≤ appliedSeq）。 */
    public long committedSeq() {
        return committedSeq;
    }

    /** 不可变变更日志条目引用（M2；载体交付前为 null，字段冻结不写）。 */
    public String changeLogRef() {
        return changeLogRef;
    }

    @Override
    public String toString() {
        return "DirectoryGenerationActivatedEvent[" + eventId + ", committedSeq=" + committedSeq + "]";
    }
}
