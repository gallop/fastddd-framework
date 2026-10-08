package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.IncrementResult;

/**
 * 增量落库端口（W5 设计 §3.3/§5.2，W5-b 签名冻结）：将单事件归一化的
 * 增量写批落库并返回水位结论。
 *
 * <p>调用契约：须在<b>提交事务内</b>调用（与 {@code DirectoryIncrementPublisher}
 * 同事务——先库后信）；幂等锚 = {@code batch.eventId()}（Inbox 处理状态 +
 * 事实行 origin/change_ref 双层防重）；空批 / 重复投递 → {@code changed=false}
 * 零写零推零水位变化。混型批按 {@code DirectoryChangeType} 分组发多枚增量事件；
 * 预合并分支（尾部过大，差异登记 D8）推 committed_seq 不推 applied_seq、不发事件。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryIncrementApplier {

    /** 落库一批增量并返回水位/事件结论（事务内调用）。 */
    IncrementResult apply(DirectoryScope scope, IncrementBatch batch);
}
