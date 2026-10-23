package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;

/**
 * 身份审计写入端口（v0.2 §5.6）：带 Actor 的审计记录写入，两个显式 SQL writer
 * （仅旧列 / 旧列 + actor 列）由宿主按 DDL 选择；事务责任随宿主审计策略（T5）。
 *
 * @author gallop
 * @since 1.1.0
 */
public interface IdentityAuditWriter {

    void write(IdentityAuditRecord record);
}
