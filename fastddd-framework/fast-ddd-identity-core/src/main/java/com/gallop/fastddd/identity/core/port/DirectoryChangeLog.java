package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;

/**
 * 不可变变更日志端口（W5 设计 §3.3/§4.1，W5-b 签名冻结）：目录域水位推进
 * 事务的 append-only 载体——代码零 UPDATE 面 + 守护测试，不引入触发器（D-3）。
 *
 * <p>{@link #append} 须在提交事务内调用（与事实写入、事件发布同事务）；
 * {@code entry.id()} 必须为调用方预铸 ULID（P2-5：先铸 id 后写事实，事实行
 * change_ref 与事件 changeLogRef 引用该值），本方法原样返回其为 changeLogRef。
 * {@code summary} 不得含 PII。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryChangeLog {

    /** 追加一条（append-only；返回 changeLogRef = entry.id()）。 */
    String append(ChangeLogEntry entry);

    /**
     * T8 no-op 判据：同 scope 同 generationId 的 GENERATION_ACTIVATED 行
     * 是否已存在（同代重复发布 → 调用方跳过 append 与代激活事件）。
     *
     * <p>注意 {@code factsInserted=0} 不是判据——A-9 空差异首发布亦为 0。</p>
     */
    boolean hasGenerationActivation(DirectoryScope scope, String generationId);
}
