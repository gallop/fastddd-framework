package com.gallop.fastddd.identity.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * 目录增量通知事件（W5 设计 §5.3/§8.1，W5-b 新增）：一次有效增量落库后由
 * {@code DirectoryIncrementPublisher} 在<b>同一事务内</b>发布的组织变化通知。
 *
 * <p>eventId 规则（冻结）：{@code dir-change:{scope}:{committed_seq}:{type}}
 * ——同一落库事务内混型批按 {@link DirectoryChangeType} 分组发多枚（共享同一
 * committed_seq）；预合并事务不发；空批/无效变更零写零推。投递语义：至少一次
 * + 消费端幂等（宿主 Outbox 唯一约束承载，镜像代激活事件约定）。</p>
 *
 * <p>{@code changeLogRef} 必须指向不可变变更日志条目（W5-b 载体交付后非 null）；
 * {@code objectExternalIds} = 该类型下受影响对象的外部键（open_id 域 /
 * 部门 external 键），仅作路由提示——消费者以回查为准，不信任通知负载内容。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryIncrementEvent {

    /** eventId 规则前缀（§5.3.4 冻结）。 */
    public static final String EVENT_ID_PREFIX = "dir-change:";

    private final String eventId;
    private final DirectoryScope scope;
    private final long committedSeq;
    private final DirectoryChangeType type;
    private final List<String> objectExternalIds;
    private final String changeLogRef;

    public DirectoryIncrementEvent(String eventId,
                                   DirectoryScope scope,
                                   long committedSeq,
                                   DirectoryChangeType type,
                                   List<String> objectExternalIds,
                                   String changeLogRef) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.committedSeq = committedSeq;
        this.type = Objects.requireNonNull(type, "type");
        this.objectExternalIds = objectExternalIds == null ? List.of() : List.copyOf(objectExternalIds);
        this.changeLogRef = Objects.requireNonNull(changeLogRef, "changeLogRef");
    }

    /** {@link #EVENT_ID_PREFIX}{scope}:{committedSeq}:{type.name()}。 */
    public static String eventIdFor(DirectoryScope scope, long committedSeq, DirectoryChangeType type) {
        return EVENT_ID_PREFIX + scope + ":" + committedSeq + ":" + type.name();
    }

    public String eventId() {
        return eventId;
    }

    public DirectoryScope scope() {
        return scope;
    }

    /** 本事务提交后的水位（共享同一落库事务的多枚事件取值一致）。 */
    public long committedSeq() {
        return committedSeq;
    }

    public DirectoryChangeType type() {
        return type;
    }

    public List<String> objectExternalIds() {
        return objectExternalIds;
    }

    /** 不可变变更日志条目引用（W5-b 载体交付后非 null）。 */
    public String changeLogRef() {
        return changeLogRef;
    }

    @Override
    public String toString() {
        return "DirectoryIncrementEvent[" + eventId + ", "
                + objectExternalIds.size() + " objects]";
    }
}
